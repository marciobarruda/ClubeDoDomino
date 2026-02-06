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

                val buchos = buchosResult.getOrNull()?.sortedByDescending { it.id } ?: emptyList()

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
                            isActive = adminRepository.isPlayerActive(user.id),
                            isOnVacation = adminRepository.isPlayerOnVacation(user.id)
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

    fun togglePlayerActive(user: User, isActive: Boolean) {
        adminRepository.setPlayerActive(user.id, isActive)
        updateLocalPlayerState(user.id) { it.copy(isActive = isActive) }
    }

    fun togglePlayerVacation(user: User, isOnVacation: Boolean) {
        adminRepository.setPlayerVacation(user.id, isOnVacation)
        updateLocalPlayerState(user.id) { it.copy(isOnVacation = isOnVacation) }
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
