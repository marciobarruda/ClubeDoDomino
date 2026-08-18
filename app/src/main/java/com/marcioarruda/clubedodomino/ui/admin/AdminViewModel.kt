package com.marcioarruda.clubedodomino.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.AdminRepository
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.FinancialEntry
import com.marcioarruda.clubedodomino.data.FinancialEntryStatus
import com.marcioarruda.clubedodomino.data.FinancialEntryType
import com.marcioarruda.clubedodomino.data.GlobalStats
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.network.BuchoDto
import com.marcioarruda.clubedodomino.data.network.MensalidadeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class DebtorItem(
    val user: User,
    val totalDue: Double,
    val debts: List<FinancialEntry>
)

data class AdminUiState(
    val isLoading: Boolean = false,
    val matches: List<Match> = emptyList(),
    val buchos: List<BuchoDto> = emptyList(),
    val mensalidades: List<MensalidadeDto> = emptyList(),
    val players: List<AdminPlayerItem> = emptyList(),
    val debtors: List<DebtorItem> = emptyList(),
    val globalStats: GlobalStats? = null,
    val error: String? = null,
    val message: String? = null,
    val isCreatingPlayer: Boolean = false,
    val isUpdatingDbPassword: Boolean = false
)

data class AdminPlayerItem(
    val user: User,
    val isActive: Boolean,
    val isOnVacation: Boolean
)

class AdminViewModel(
    private val repository: ClubRepository,
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Parallel fetching could be better but sequential is safer for now
                val matches = repository.getMatches().sortedByDescending { it.date }
                val buchosResult = repository.getBuchosResult()
                val users = repository.getPlayers()

                val buchos = buchosResult.getOrNull()?.filter { it.pago != true }?.sortedByDescending { it.id } ?: emptyList()

                // Calculate Stats safely
                val stats = try {
                    adminRepository.calculateStats(matches, buchos, users)
                } catch (e: Exception) {
                    GlobalStats(0.0, 0.0, 0, 0, 0) // Fallback to avoid crash
                }

                // Map Players
                val adminPlayers = users
                    .filter { !it.name.contains("NÃO MEMBRO", ignoreCase = true) && it.id != "7" }
                    .map { user ->
                        AdminPlayerItem(
                            user = user,
                            isActive = user.isActive,
                            isOnVacation = user.isOnVacation
                        )
                    }
                    .sortedBy { it.user.displayName }

                // Calcular inadimplentes — mesma lógica do FinanceViewModel.updateUiState
                val now = Calendar.getInstance()
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH)

                val allBuchos = repository.getBuchosResult().getOrNull() ?: emptyList()
                val allMensalidades = repository.getMensalidadesResult().getOrNull() ?: emptyList()
                val mensalidadesNaoPagas = allMensalidades.filter { it.pago != true }.sortedByDescending { it.id }
                val allEntries = buildList {
                    addAll(allBuchos.mapNotNull { with(repository) { it.toFinancialEntry(users) } })
                    addAll(allMensalidades.mapNotNull { with(repository) { it.toFinancialEntry(users) } })
                }

                val debtors = adminPlayers.mapNotNull { playerItem ->
                    val userId = playerItem.user.id
                    val overdueDebts = allEntries.filter { entry ->
                        if (entry.userId != userId) return@filter false
                        if (entry.status != FinancialEntryStatus.PENDING) return@filter false
                        when (entry.type) {
                            FinancialEntryType.MONTHLY_FEE, FinancialEntryType.EXTRA_TAX -> true
                            FinancialEntryType.BUCHO -> {
                                val cal = Calendar.getInstance().apply { time = entry.dueDate }
                                val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
                                y < currentYear || (y == currentYear && m < currentMonth)
                            }
                            else -> false
                        }
                    }.sortedByDescending { it.dueDate }

                    val total = overdueDebts.sumOf { it.amount }
                    if (total > 0.0) DebtorItem(playerItem.user, total, overdueDebts) else null
                }.sortedByDescending { it.totalDue }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        matches = matches,
                        buchos = buchos,
                        mensalidades = mensalidadesNaoPagas,
                        players = adminPlayers,
                        debtors = debtors,
                        globalStats = stats
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Falha ao carregar dados: ${e.message}") }
            }
        }
    }

    fun deleteMatch(matchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMatch(matchId, "Excluir")
                loadData() // Refresh
                _uiState.update { it.copy(message = "Partida excluída com sucesso.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao excluir partida: ${e.message}") }
            }
        }
    }

    fun deleteBucho(buchoId: Long?) {
        if (buchoId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteBucho(buchoId.toString(), "Excluir")
                loadData() // Refresh
                _uiState.update { it.copy(message = "Bucho excluído com sucesso.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao excluir bucho: ${e.message}") }
            }
        }
    }

    fun markBuchoAsPaid(buchoId: Long?) {
        if (buchoId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.markBuchoAsPaid(buchoId)
                loadData() // Refresh
                _uiState.update { it.copy(message = "Bucho marcado como pago.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao marcar bucho como pago: ${e.message}") }
            }
        }
    }

    fun deleteMensalidade(mensalidadeId: Long?) {
        if (mensalidadeId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMensalidade(mensalidadeId.toString())
                loadData() // Refresh
                _uiState.update { it.copy(message = "Mensalidade excluída com sucesso.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao excluir mensalidade: ${e.message}") }
            }
        }
    }

    fun markMensalidadeAsPaid(mensalidadeId: Long?) {
        if (mensalidadeId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.markMensalidadeAsPaid(mensalidadeId)
                loadData() // Refresh
                _uiState.update { it.copy(message = "Mensalidade marcada como paga.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao marcar mensalidade como paga: ${e.message}") }
            }
        }
    }

    fun togglePlayerActive(user: User, isActive: Boolean) {
        updateLocalPlayerState(user.id) { it.copy(isActive = isActive) }
        viewModelScope.launch {
            try {
                repository.setPlayerActive(user.id, isActive)
                val status = if (isActive) "ativo" else "inativo"
                _uiState.update { it.copy(message = "${user.displayName} marcado como $status.") }
            } catch (e: Exception) {
                updateLocalPlayerState(user.id) { it.copy(isActive = !isActive) }
                _uiState.update { it.copy(error = "Erro ao salvar: ${e.message}") }
            }
        }
    }

    fun togglePlayerVacation(user: User, isOnVacation: Boolean) {
        updateLocalPlayerState(user.id) { it.copy(isOnVacation = isOnVacation) }
        viewModelScope.launch {
            try {
                repository.setPlayerVacation(user.id, isOnVacation)
                val status = if (isOnVacation) "em férias" else "fora do modo férias"
                _uiState.update { it.copy(message = "${user.displayName} marcado como $status.") }
            } catch (e: Exception) {
                updateLocalPlayerState(user.id) { it.copy(isOnVacation = !isOnVacation) }
                _uiState.update { it.copy(error = "Erro ao salvar: ${e.message}") }
            }
        }
    }

    private fun updateLocalPlayerState(userId: String, update: (AdminPlayerItem) -> AdminPlayerItem) {
        _uiState.update { state ->
            val newPlayers = state.players.map { 
                if (it.user.id == userId) update(it) else it 
            }
            state.copy(players = newPlayers)
        }
    }
    
    fun createPlayer(
        name: String,
        email: String,
        password: String,
        avatarId: String,
        billingStartDate: Calendar
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPlayer = true, error = null) }
            try {
                repository.createPlayer(name, email, password, avatarId, billingStartDate)
                loadData()
                _uiState.update { it.copy(isCreatingPlayer = false, message = "Jogador \"$name\" cadastrado com mensalidades retroativas aplicadas.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreatingPlayer = false, error = "Erro ao cadastrar jogador: ${e.message}") }
            }
        }
    }

    fun dismissMessage() {
         _uiState.update { it.copy(message = null, error = null) }
    }

    fun updateDbPassword(requesterEmail: String, senhaLogin: String, novaSenha: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingDbPassword = true, error = null) }
            try {
                repository.updateDbPassword(requesterEmail, senhaLogin, novaSenha)
                _uiState.update {
                    it.copy(isUpdatingDbPassword = false, message = "Senha do banco de dados atualizada com sucesso.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUpdatingDbPassword = false, error = "Erro ao atualizar senha do banco: ${e.message}")
                }
            }
        }
    }
}
