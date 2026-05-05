package com.marcioarruda.clubedodomino.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.BestPlayer
import com.marcioarruda.clubedodomino.data.HolidayRepository
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

data class DashboardUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val error: String? = null,
    val totalPlayers: Int = 0,
    val totalMatchesToday: Int = 0,
    val totalDebt: Double = 0.0,
    val isNewMatchVisible: Boolean = false,
    val groupedMatches: Map<String, List<Match>> = emptyMap(),
    val bestPlayers: List<BestPlayer> = emptyList(),
    val worstPlayers: List<BestPlayer> = emptyList(),
    val isRefreshing: Boolean = false
)

class DashboardViewModel(private val repository: ClubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val matchAvailabilityManager = com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager
    private val dateFormatter = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR"))


    init {
        // Inicia o monitoramento ativo da disponibilidade do módulo
        startAvailabilityMonitoring()
    }

    fun loadDashboardData(userId: String, isRefreshing: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRefreshing) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                // Carrega os dados do usuário
                val user = repository.getPlayer(userId)

                // Carrega estatísticas gerais (pode ser feito em paralelo se necessário)
                val totalPlayers = repository.getTotalPlayers()
                val totalMatchesToday = repository.getMatchesCountToday()
                val totalDebt = repository.getTotalDebt(userId)

                // Carrega e processa as partidas recentes
                val matches = repository.getMatches().distinctBy { it.id }.sortedByDescending { it.date }.take(20)
                val groupedMatches = matches.groupBy { dateFormatter.format(it.date) }


                // Calculate Best and Worst Players of the Day
                val todayStr = dateFormatter.format(java.util.Date())
                val todayMatches = groupedMatches[todayStr] ?: emptyList()
                
                var topPlayers = emptyList<BestPlayer>()
                var bottomPlayers = emptyList<BestPlayer>()

                if (todayMatches.isNotEmpty()) {
                    val playerPoints = mutableMapOf<User, Int>()
                    
                    // Initialize all players who played today with 0 points
                    todayMatches.forEach { match ->
                        listOf(match.team1Player1, match.team1Player2, match.team2Player1, match.team2Player2).forEach { p ->
                            if (!playerPoints.containsKey(p)) {
                                playerPoints[p] = 0
                            }
                        }
                    }

                    todayMatches.forEach { match ->
                        val winnerScore = if (match.score1 > match.score2) match.score1 else match.score2
                        val loserScore = if (match.score1 > match.score2) match.score2 else match.score1
                        val isBucho = (winnerScore == 6 && loserScore == 0)
                        val points = if (match.wasBuchoRe) winnerScore + 2 else if (isBucho) winnerScore + 1 else java.lang.Math.abs(match.score1 - match.score2)

                        val winners = if (match.score1 > match.score2) listOf(match.team1Player1, match.team1Player2) else listOf(match.team2Player1, match.team2Player2)
                        
                        winners.forEach { w ->
                            val current = playerPoints.keys.find { it.id == w.id } ?: w
                            playerPoints[current] = (playerPoints[current] ?: 0) + points
                        }
                    }
                    
                    if (playerPoints.isNotEmpty()) {
                        val maxPoints = playerPoints.values.maxOrNull() ?: 0
                        val minPoints = playerPoints.values.minOrNull() ?: 0

                        if (maxPoints > 0) {
                            topPlayers = playerPoints.filterValues { it == maxPoints }.map { BestPlayer(it.key, it.value) }
                        }
                        
                        // Worst players are those with minimum points (could be 0)
                        bottomPlayers = playerPoints.filterValues { it == minPoints }.map { BestPlayer(it.key, it.value) }
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        user = user,
                        totalPlayers = totalPlayers,
                        totalMatchesToday = totalMatchesToday,
                        totalDebt = totalDebt,
                        groupedMatches = groupedMatches,
                        bestPlayers = topPlayers,
                        worstPlayers = bottomPlayers
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Ocorreu um erro desconhecido."
                    )
                }
            }
        }
    }

    fun updateProfileImage(email: String, base64Image: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.updateProfile(email, base64Image)
                // Recarrega os dados para atualizar a foto
                loadDashboardData(email)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Falha ao atualizar foto: ${e.message}"
                    ) 
                }
            }
        }
    }

    private fun startAvailabilityMonitoring() {
        // tickerFlow emite um valor a cada 60 segundos (1 minuto)
        tickerFlow(periodMillis = 60_000, initialDelayMillis = 0)
            .onEach {
                matchAvailabilityManager.initialize(com.marcioarruda.clubedodomino.DominoClubApplication.instance)
                
                // Recalcula a visibilidade a cada emissão
                val isAvailable = matchAvailabilityManager.isModuleAvailable(com.marcioarruda.clubedodomino.DominoClubApplication.instance)
                _uiState.update { it.copy(isNewMatchVisible = isAvailable) }
            }
            .launchIn(viewModelScope) // Lança o flow no escopo do ViewModel
    }

    // Helper para criar um ticker flow
    private fun tickerFlow(periodMillis: Long, initialDelayMillis: Long = 0) = flow {
        kotlinx.coroutines.delay(initialDelayMillis)
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(periodMillis)
        }
    }
}
