package com.marcioarruda.clubedodomino.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.AdminRepository
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.GlobalStats
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.network.BuchoDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class AdminUiState(
    val isLoading: Boolean = false,
    val matches: List<Match> = emptyList(),
    val buchos: List<BuchoDto> = emptyList(),
    val players: List<AdminPlayerItem> = emptyList(),
    val globalStats: GlobalStats? = null,
    val error: String? = null,
    val message: String? = null
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

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        matches = matches,
                        buchos = buchos,
                        players = adminPlayers,
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
    
    fun dismissMessage() {
         _uiState.update { it.copy(message = null, error = null) }
    }
}
