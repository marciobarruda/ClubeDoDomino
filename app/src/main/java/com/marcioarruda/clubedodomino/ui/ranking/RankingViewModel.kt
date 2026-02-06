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
                val matchesResult = repository.getRawMatchesResult()
                val users = usersDeferred.await()

                matchesResult.onSuccess { rawMatches ->
                    if (rawMatches.isEmpty() || users.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, rankingList = emptyList()) }
                        return@onSuccess
                    }

                    // De-duplicate matches based on ID to avoid double counting
                    val matches = rawMatches.distinctBy { it.id }

                    val cal = Calendar.getInstance()
                    val currentYear = cal.get(Calendar.YEAR)
                    val currentMonth = cal.get(Calendar.MONTH)
                    val currentDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

                    val statsMap = users.associate {
                        it.name.lowercase() to MutableRankingStats(it.name, it.photoUrl)
                    }.toMutableMap()

                    matches.forEach { match ->
                        // Etapa 1: Parse Seguro da Data
                        val matchDate = try {
                            match.data?.let { dateFormat.parse(it) }
                        } catch (e: Exception) {
                            null
                        } ?: return@forEach // Pula para a próxima partida se a data for inválida

                        cal.time = matchDate
                        val matchYear = cal.get(Calendar.YEAR)

                        // Etapa 2: Filtro Temporal (Condição B)
                        // Processa apenas partidas do ano corrente.
                        if (matchYear == currentYear) {
                            val allPlayersInMatch = listOfNotNull(
                                match.jogador1, match.jogador2, match.jogador3, match.jogador4
                            ).map { it.trim().lowercase() }
                            
                            val matchMonth = cal.get(Calendar.MONTH)
                            val matchDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                            val isThisMonth = matchMonth == currentMonth
                            val isToday = matchDayOfYear == currentDayOfYear

                            // A contagem de partidas está correta e é feita para todos os participantes.
                            allPlayersInMatch.forEach { playerName ->
                                statsMap[playerName]?.let { stats ->
                                    stats.yearlyMatches++
                                    if (isThisMonth) stats.monthlyMatches++
                                    if (isToday) stats.dailyMatches++
                                }
                            }

                            // Etapa 3: Filtro de Vitória (Condição A) e Operação de Soma
                            // Split by both '/' and '&' to handle different separators
                            val winnerNames = match.dupla_vencedora?.split(Regex("[/&]"))
                                ?.map { it.trim().lowercase() } ?: emptyList()
                            
                            val points = match.pts ?: 0 // Trata nulos como 0

                            // A soma de pontos é feita APENAS para os vencedores.
                            winnerNames.forEach { winnerName ->
                                if (allPlayersInMatch.contains(winnerName)) {
                                    statsMap[winnerName]?.let { stats ->
                                        // Aplica a soma para o acumulado do ano, mês e dia.
                                        stats.yearlyPoints += points
                                        if (isThisMonth) stats.monthlyPoints += points
                                        if (isToday) stats.dailyPoints += points
                                    }
                                }
                            }
                        }
                    }

                    val rankingList = statsMap.values
                        .map { it.toRankingPlayer() }
                        .filter { it.yearlyMatches > 0 }
                        .sortedByDescending { it.yearlyPoints } // Ordenação mantida

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

    private data class MutableRankingStats(
        val name: String,
        val photoUrl: String,
        var dailyPoints: Long = 0,
        var dailyMatches: Long = 0,
        var monthlyPoints: Long = 0,
        var monthlyMatches: Long = 0,
        var yearlyPoints: Long = 0,
        var yearlyMatches: Long = 0
    ) {
        fun toRankingPlayer() = RankingPlayer(
            playerName = name,
            photoUrl = photoUrl,
            dailyPoints = dailyPoints.toInt(),
            dailyMatches = dailyMatches.toInt(),
            monthlyPoints = monthlyPoints.toInt(),
            monthlyMatches = monthlyMatches.toInt(),
            yearlyPoints = yearlyPoints.toInt(),
            yearlyMatches = yearlyMatches.toInt()
        )
    }
}
