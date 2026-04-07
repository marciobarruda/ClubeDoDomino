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
                // val mimeType = contentResolver.getType(uri) ?: "image/*" // Unused in new DTO?
                
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null) {
                    _uiState.update { it.copy(uploadStatus = UploadStatus.ERROR, uploadError = "Falha ao ler arquivo.") }
                    return@launch
                }

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                // Fetch Data for Request
                val users = repository.getPlayers()
                val user = users.find { it.id == userId } ?: throw Exception("Usuário não encontrado")
                
                // Re-using current UI state debts might be risky if not loaded.
                // But generally upload is done after load.
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

                // Filtra enviando apenas buchos "vencidos" (meses anteriores) e mensalidades pendentes
                val pendingDebts = _uiState.value.debts.filter { entry ->
                    if (entry.status != FinancialEntryStatus.PENDING) return@filter false
                    
                    if (entry.type == FinancialEntryType.MONTHLY_FEE || entry.type == FinancialEntryType.EXTRA_TAX) {
                        return@filter true
                    }
                    
                    if (entry.type == FinancialEntryType.BUCHO) {
                        val itemCal = Calendar.getInstance()
                        itemCal.time = entry.dueDate
                        val itemYear = itemCal.get(Calendar.YEAR)
                        val itemMonth = itemCal.get(Calendar.MONTH)
                        
                        return@filter if (itemYear < currentYear) true
                        else (itemYear == currentYear && itemMonth < currentMonth)
                    }
                    false
                }
                
                val buchos = pendingDebts.filter { it.type == FinancialEntryType.BUCHO }
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

                repository.uploadComprovante(request)
                
                _uiState.update { it.copy(uploadStatus = UploadStatus.SUCCESS) }
            } catch (e: Exception) {
                _uiState.update { it.copy(uploadStatus = UploadStatus.ERROR, uploadError = e.message ?: "Erro desconhecido ao enviar.") }
            }
        }
    }

    fun dismissUploadStatus() {
        _uiState.update { it.copy(uploadStatus = UploadStatus.IDLE, uploadError = null) }
    }

    fun onNavigateToHomeComplete() {
        _uiState.update { it.copy(navigateToHome = false) }
    }

    fun loadFinancialData(userId: String, isRefreshing: Boolean = false) {
        viewModelScope.launch {
            mutex.withLock {
                if (isRefreshing) {
                    _uiState.update { it.copy(isRefreshing = true, error = null) }
                } else {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
                try {
                    val users = repository.getPlayers()
                    val currentUser = users.find { it.id == userId }

                    if (currentUser == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Usuário não encontrado.") }
                        return@launch
                    }
                    val buchosResult = repository.getBuchosResult()
                    var mensalidadesResult = repository.getMensalidadesResult()
                    val matches = repository.getMatches()

                    val allDebts = mutableListOf<FinancialEntry>()

                    var myBuchosValue = 0.0
                    buchosResult.onSuccess { buchos ->
                        val entries = buchos.mapNotNull {
                            with(repository) { it.toFinancialEntry(users) }
                        }
                        allDebts.addAll(entries)

                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                        // Calculate Previous Month for Tax Logic
                        val prevCal = Calendar.getInstance()
                        prevCal.add(Calendar.MONTH, -1)
                        val prevMonth = prevCal.get(Calendar.MONTH)
                        val prevYear = prevCal.get(Calendar.YEAR)

                        myBuchosValue = entries.filter {
                            val cal = Calendar.getInstance()
                            cal.time = it.dueDate
                            it.userId == userId && cal.get(Calendar.MONTH) == prevMonth && cal.get(Calendar.YEAR) == prevYear
                        }.sumOf { it.amount }
                    }.onFailure {
                        Log.e("FinanceViewModel", "Falha ao carregar buchos", it)
                    }

                    var currentMensalidades = mensalidadesResult.getOrNull()?.mapNotNull {
                        with(repository) { it.toFinancialEntry(users) }
                    } ?: emptyList()

                    allDebts.addAll(currentMensalidades)

                    // Calculate Global Stats for Previous Month
                    val prevCal = Calendar.getInstance()
                    prevCal.add(Calendar.MONTH, -1)
                    val prevMonth = prevCal.get(Calendar.MONTH)
                    val prevYear = prevCal.get(Calendar.YEAR)

                    val buchosList = buchosResult.getOrNull() ?: emptyList()
                    val globalStats = adminRepository.calculateStats(matches, buchosList, users, prevMonth, prevYear)
                    
                    val isOnVacation = adminRepository.isPlayerOnVacation(userId)
                    val isActive = adminRepository.isPlayerActive(userId)

                    val myMatches = matches.filter {
                        val cal = Calendar.getInstance()
                        cal.time = it.date
                        val isMyMatch = it.team1Player1.id == userId || it.team1Player2.id == userId ||
                                it.team2Player1.id == userId || it.team2Player2.id == userId
                        isMyMatch && cal.get(Calendar.MONTH) == prevMonth && cal.get(Calendar.YEAR) == prevYear
                    }.size

                    if (isActive && !isOnVacation && myMatches < globalStats.avgMatches) {
                        val deficit = globalStats.avgBuchos - myBuchosValue

                        if (deficit > 0.01) {
                            val lastBusinessDay = adminRepository.getLastBusinessDayOfMonth(prevYear, prevMonth)
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val dataString = dateFormat.format(lastBusinessDay)
                            
                            val alreadyExists = allDebts.any { entry ->
                                entry.userId == userId &&
                                (entry.type == FinancialEntryType.EXTRA_TAX || entry.description.contains("Taxa extra", ignoreCase = true)) &&
                                run {
                                    val cal = Calendar.getInstance()
                                    cal.time = entry.dueDate
                                    cal.get(Calendar.MONTH) == prevMonth && cal.get(Calendar.YEAR) == prevYear
                                }
                            }
                            
                            if (!alreadyExists) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        com.marcioarruda.clubedodomino.data.network.RetrofitClient.instance
                                            .registerDebit(com.marcioarruda.clubedodomino.data.network.DebitRequest(
                                                data = dataString,
                                                jogador = currentUser.name,
                                                valor = deficit,
                                                pago = false,
                                                obs = "Taxa extra"
                                            ))
                                    } catch (e: Exception) {
                                        Log.e("FinanceViewModel", "Falha ao registrar taxa extra", e)
                                    }
                                }
                                
                                allDebts.add(FinancialEntry(
                                    id = UUID.randomUUID().toString(),
                                    userId = userId,
                                    type = FinancialEntryType.EXTRA_TAX,
                                    amount = deficit,
                                    status = FinancialEntryStatus.PENDING,
                                    dueDate = lastBusinessDay,
                                    description = "Taxa Extra (Assiduidade)",
                                    originalRemoteId = null,
                                    originalReference = null
                                ))
                            }
                        }
                    }

                    updateUiState(allDebts, currentUser.id, globalStats)
                    
                    // --- RECOVERY OF MISSING MONTHLY FEES (Last 4 Months) ---
                    val cal = Calendar.getInstance()
                    val curYear = cal.get(Calendar.YEAR)
                    val curMonth = cal.get(Calendar.MONTH)

                    for (i in 0..3) {
                        val checkCal = Calendar.getInstance()
                        checkCal.add(Calendar.MONTH, -i)
                        val checkMonth = checkCal.get(Calendar.MONTH)
                        val checkYear = checkCal.get(Calendar.YEAR)

                        val hasFee = currentMensalidades.any { entry ->
                            if (entry.userId == currentUser.id) {
                                val mCal = Calendar.getInstance()
                                mCal.time = entry.dueDate
                                mCal.get(Calendar.YEAR) == checkYear && mCal.get(Calendar.MONTH) == checkMonth
                            } else false
                        }

                        if (!hasFee && !adminRepository.isPlayerOnVacation(userId)) {
                            try {
                                Log.d("FinanceViewModel", "Mensalidade faltando para ${currentUser.name} em ${checkMonth + 1}/$checkYear. Gerando...")
                                repository.createMensalidade(currentUser.name, checkMonth, checkYear)
                                // We refresh the list inside the loop or after? 
                                // Better to refetch once after loop if any were created.
                            } catch (e: Exception) {
                                Log.e("FinanceViewModel", "Falha ao gerar mensalidade retroativa", e)
                            }
                        }
                    }

                    // Refetch all to show new ones
                    val refreshedMensalidades = repository.getMensalidadesResult().getOrNull()?.mapNotNull {
                        with(repository) { it.toFinancialEntry(users) }
                    } ?: emptyList()
                    
                    // Update allDebts with unique items
                    val existingRemoteIds = allDebts.mapNotNull { it.originalRemoteId }.toSet()
                    val newUniqueMensalidades = refreshedMensalidades.filter { it.originalRemoteId !in existingRemoteIds }
                    allDebts.addAll(newUniqueMensalidades)

                    updateUiState(allDebts, currentUser.id, globalStats)

                } catch (e: Exception) {

                } catch (e: Exception) {
                    Log.e("FinanceViewModel", "Erro desconhecido", e)
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Erro desconhecido") }
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

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)

        val totalDue = pendingDebts.filter { entry ->
            if (entry.status != FinancialEntryStatus.PENDING) return@filter false

            if (entry.type == FinancialEntryType.MONTHLY_FEE || entry.type == FinancialEntryType.EXTRA_TAX) {
                return@filter true
            }

            // LOGIC: Filter out Future Buchos from Total Due
            // Buchos from current month or future years/months are excluded from the *sum* displayed at the top,
            // but they are still shown in the list below with the "A Vencer" flag.
            if (entry.type == FinancialEntryType.BUCHO) {
                val itemCal = Calendar.getInstance()
                itemCal.time = entry.dueDate
                val itemYear = itemCal.get(Calendar.YEAR)
                val itemMonth = itemCal.get(Calendar.MONTH)
                
                // Return TRUE (include in sum) only if it is from a previous month/year
                return@filter if (itemYear < currentYear) true
                else (itemYear == currentYear && itemMonth < currentMonth)
            }
            false
        }.sumOf { it.amount }

        val totalUpcoming = pendingDebts.filter { entry ->
            if (entry.status != FinancialEntryStatus.PENDING) return@filter false
            if (entry.type != FinancialEntryType.BUCHO) return@filter false
            
            val itemCal = Calendar.getInstance()
            itemCal.time = entry.dueDate
            val itemYear = itemCal.get(Calendar.YEAR)
            val itemMonth = itemCal.get(Calendar.MONTH)
            
            // Return TRUE (include in upcoming sum) if it is from current or future month/year
            return@filter if (itemYear > currentYear) true
            else (itemYear == currentYear && itemMonth >= currentMonth)
        }.sumOf { it.amount }

        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                debts = sortedDebts,
                totalDue = totalDue,
                totalUpcoming = totalUpcoming,
                globalStats = globalStats
            )
        }
    }
}