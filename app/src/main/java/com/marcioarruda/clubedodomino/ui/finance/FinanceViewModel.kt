package com.marcioarruda.clubedodomino.ui.finance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.AdminRepository
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.FinancialEntry
import com.marcioarruda.clubedodomino.data.FinancialEntryStatus
import com.marcioarruda.clubedodomino.data.FinancialEntryType
import com.marcioarruda.clubedodomino.data.GlobalStats
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.network.BuchoDto
import com.marcioarruda.clubedodomino.data.network.ComprovanteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.UUID

enum class UploadStatus {
    IDLE,
    UPLOADING,
    SUCCESS,
    ERROR
}

data class FinanceUiState(
    val isLoading: Boolean = false,
    val debts: List<FinancialEntry> = emptyList(),
    val totalDue: Double = 0.0,
    val totalUpcoming: Double = 0.0,
    val error: String? = null,
    val uploadStatus: UploadStatus = UploadStatus.IDLE,
    val uploadError: String? = null,
    val uploadBaixaAutomatica: Boolean? = null,
    val navigateToHome: Boolean = false,
    val globalStats: GlobalStats? = null,
    val isRefreshing: Boolean = false
)

class FinanceViewModel(
    private val repository: ClubRepository,
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val mutex = Mutex()
    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    fun uploadComprovante(userId: String, uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadStatus = UploadStatus.UPLOADING, uploadError = null) }
            try {
                val contentResolver = context.contentResolver
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null) {
                    _uiState.update { it.copy(uploadStatus = UploadStatus.ERROR, uploadError = "Falha ao ler arquivo.") }
                    return@launch
                }

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val users = repository.getPlayers()
                val user = users.find { it.id == userId } ?: throw Exception("Usuário não encontrado")
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

                val pendingDebts = _uiState.value.debts.filter { entry ->
                    if (entry.status != FinancialEntryStatus.PENDING) return@filter false
                    if (entry.type == FinancialEntryType.MONTHLY_FEE || entry.type == FinancialEntryType.EXTRA_TAX) return@filter true
                    if (entry.type == FinancialEntryType.BUCHO) {
                        val itemCal = Calendar.getInstance()
                        itemCal.time = entry.dueDate
                        val itemYear = itemCal.get(Calendar.YEAR)
                        val itemMonth = itemCal.get(Calendar.MONTH)
                        return@filter if (itemYear < currentYear) true else (itemYear == currentYear && itemMonth < currentMonth)
                    }
                    false
                }
                
                // Taxa extra é fisicamente um registro na tabela `buchos` no servidor (ver
                // gerarTaxaExtraBuchosParaMes em server.js), então precisa ir junto com os buchos
                // no `bucho_ids` do comprovante — senão o valor entra no total cobrado mas o ID
                // nunca chega para o fluxo de baixa conseguir marcá-la como paga.
                val buchos = pendingDebts.filter { it.type == FinancialEntryType.BUCHO || it.type == FinancialEntryType.EXTRA_TAX }
                val mensalidades = pendingDebts.filter { it.type == FinancialEntryType.MONTHLY_FEE }
                val valorTotal = pendingDebts.sumOf { it.amount }
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                val request = ComprovanteRequest(
                    jogadorNome = user.name,
                    valorTotal = valorTotal,
                    buchoIds = buchos.mapNotNull { it.originalRemoteId },
                    mensalidadeIds = mensalidades.mapNotNull { it.originalRemoteId?.toString() },
                    mensalidadeVencimentos = mensalidades.map { dateFormat.format(it.dueDate) },
                    imagemBase64 = base64
                )

                val response = repository.uploadComprovante(request)
                _uiState.update { it.copy(uploadStatus = UploadStatus.SUCCESS, uploadBaixaAutomatica = response.baixaAutomatica == true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(uploadStatus = UploadStatus.ERROR, uploadError = e.message ?: "Erro desconhecido ao enviar.") }
            }
        }
    }

    fun dismissUploadStatus() {
        _uiState.update { it.copy(uploadStatus = UploadStatus.IDLE, uploadError = null, uploadBaixaAutomatica = null) }
    }

    fun onNavigateToHomeComplete() {
        _uiState.update { it.copy(navigateToHome = false) }
    }

    fun loadFinancialData(userId: String, isRefreshing: Boolean = false) {
        viewModelScope.launch {
            mutex.withLock {
                _uiState.update { it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing, error = null) }
                try {
                    val users = repository.getPlayers()
                    val currentUser = users.find { it.id == userId } ?: run {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Usuário não encontrado.") }
                        return@launch
                    }

                    val buchosResult = repository.getBuchosResult()
                    val mensalidadesResult = repository.getMensalidadesResult()
                    val matches = repository.getMatches()
                    val buchosList = buchosResult.getOrNull() ?: emptyList()

                    val allDebts = mutableListOf<FinancialEntry>()
                    buchosResult.onSuccess { buchos ->
                        allDebts.addAll(buchos.mapNotNull { with(repository) { it.toFinancialEntry(users) } })
                    }
                    mensalidadesResult.onSuccess { mensalidades ->
                        allDebts.addAll(mensalidades.mapNotNull { with(repository) { it.toFinancialEntry(users) } })
                    }

                    // Geração de mensalidades e de taxa extra de buchos agora é feita centralmente pelo
                    // servidor (cron mensal, dia 1º) — ver gerarMensalidadesDoMesAtual e
                    // gerarTaxaExtraBuchosMesAnterior em server/server.js. O disparo client-side que
                    // existia aqui foi removido porque rodava toda vez que qualquer jogador abria esta
                    // tela, gerando mensalidades em duplicidade com o cron do servidor.

                    // Final Refresh
                    val finalBuchos = repository.getBuchosResult().getOrNull()?.mapNotNull { with(repository) { it.toFinancialEntry(users) } } ?: emptyList()
                    val finalMensalidades = repository.getMensalidadesResult().getOrNull()?.mapNotNull { with(repository) { it.toFinancialEntry(users) } } ?: emptyList()
                    val prevMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    
                    val globalStats = adminRepository.calculateStats(matches, buchosList, users, prevMonthCal.get(Calendar.MONTH), prevMonthCal.get(Calendar.YEAR))
                    
                    updateUiState(finalBuchos + finalMensalidades, currentUser.id, globalStats)

                } catch (e: Exception) {
                    Log.e("FinanceViewModel", "Erro no carregamento financeiro", e)
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Erro ao carregar") }
                }
            }
        }
    }

    private fun updateUiState(allDebts: List<FinancialEntry>, userId: String, globalStats: GlobalStats? = null) {
        val userDebts = allDebts.filter { it.userId == userId }
        val pendingDebts = userDebts.filter { 
            val isPaidExtraTax = it.type == FinancialEntryType.EXTRA_TAX && it.status == FinancialEntryStatus.PAID
            it.status != FinancialEntryStatus.PAID && !isPaidExtraTax
        }
        val sortedDebts = pendingDebts.sortedByDescending { it.dueDate }
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        val totalDue = pendingDebts.filter { entry ->
            if (entry.status != FinancialEntryStatus.PENDING) return@filter false
            if (entry.type == FinancialEntryType.MONTHLY_FEE || entry.type == FinancialEntryType.EXTRA_TAX) return@filter true
            if (entry.type == FinancialEntryType.BUCHO) {
                val itemCal = Calendar.getInstance().apply { time = entry.dueDate }
                val itemYear = itemCal.get(Calendar.YEAR)
                val itemMonth = itemCal.get(Calendar.MONTH)
                return@filter if (itemYear < currentYear) true else (itemYear == currentYear && itemMonth < currentMonth)
            }
            false
        }.sumOf { it.amount }

        val totalUpcoming = pendingDebts.filter { entry ->
            if (entry.status != FinancialEntryStatus.PENDING) return@filter false
            if (entry.type != FinancialEntryType.BUCHO) return@filter false
            val itemCal = Calendar.getInstance().apply { time = entry.dueDate }
            val itemYear = itemCal.get(Calendar.YEAR)
            val itemMonth = itemCal.get(Calendar.MONTH)
            return@filter if (itemYear > currentYear) true else (itemYear == currentYear && itemMonth >= currentMonth)
        }.sumOf { it.amount }

        _uiState.update {
            it.copy(
                isLoading = false, isRefreshing = false, debts = sortedDebts,
                totalDue = totalDue, totalUpcoming = totalUpcoming, globalStats = globalStats
            )
        }
    }
}