package com.marcioarruda.clubedodomino.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.RankingPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class RankingUiState(
    val isLoading: Boolean = false,
    val rankingList: List<RankingPlayer> = emptyList(),
    val error: String? = null
)

class RankingViewModel(private val repository: ClubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    init {
        loadRanking()
    }

    fun loadRanking() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val usersDeferred = async { repository.getPlayers() }
                val rankingResult = repository.getRankingResult()
                val users = usersDeferred.await()

                rankingResult.onSuccess { rawRanking ->
                    if (rawRanking.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, rankingList = emptyList()) }
                        return@onSuccess
                    }

                    val statsMap = rawRanking.mapNotNull { dto ->
                        if (dto.jogador.contains("NÃO MEMBRO", ignoreCase = true)) return@mapNotNull null
                        val user = users.find { it.name.equals(dto.jogador.trim(), ignoreCase = true) || it.displayName.equals(dto.jogador.trim(), ignoreCase = true) }
                        val photoUrl = user?.photoUrl ?: ""
                        
                        RankingPlayer(
                            playerName = dto.jogador,
                            photoUrl = photoUrl,
                            dailyPoints = dto.pontos_dia,
                            dailyMatches = dto.partidas_dia,
                            monthlyPoints = dto.pontos_mes,
                            monthlyMatches = dto.partidas_mes,
                            yearlyPoints = dto.pontos_ano,
                            yearlyMatches = dto.partidas_ano,
                            dailyBuchosApplied = dto.buchos_aplicados_dia,
                            dailyBuchosReceived = dto.buchos_sofridos_dia,
                            monthlyBuchosApplied = dto.buchos_aplicados_mes,
                            monthlyBuchosReceived = dto.buchos_sofridos_mes,
                            yearlyBuchosApplied = dto.buchos_aplicados_ano,
                            yearlyBuchosReceived = dto.buchos_sofridos_ano,
                            yearlyWins = dto.vitorias_ano,
                            yearlyLosses = dto.derrotas_ano
                        )
                    }

                    val rankingList = statsMap
                        .sortedWith(compareByDescending<RankingPlayer> { it.monthlyPoints }.thenByDescending { it.yearlyPoints })

                    _uiState.update {
                        it.copy(isLoading = false, rankingList = rankingList)
                    }

                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Erro ao carregar ranking") }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
