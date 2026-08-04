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

import com.marcioarruda.clubedodomino.data.ChampionCelebration

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
    val isRefreshing: Boolean = false,
    val championCelebration: ChampionCelebration? = null
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


                // Calculate Best and Worst Players of the Day using Ranking API
                var topPlayers = emptyList<BestPlayer>()
                var bottomPlayers = emptyList<BestPlayer>()

                val rankingResult = repository.getRankingResult()
                val allPlayers = repository.getPlayers()

                rankingResult.onSuccess { ranking ->
                    // Só concorre ao destaque do dia quem jogou pelo menos 2 partidas —
                    // evita que 1 partida isolada (vitória ou derrota) decida o prêmio.
                    val eligibleToday = ranking.filter { it.partidas_dia >= 2 && !it.jogador.contains("NÃO MEMBRO", ignoreCase = true) }

                    if (eligibleToday.isNotEmpty()) {
                        fun winRate(r: com.marcioarruda.clubedodomino.data.network.RankingDto) =
                            r.vitorias_dia.toDouble() / r.partidas_dia

                        // Craque do dia: maior taxa de aproveitamento; empate desempatado por mais pontos.
                        val bestRanked = eligibleToday.sortedWith(
                            compareByDescending<com.marcioarruda.clubedodomino.data.network.RankingDto> { winRate(it) }
                                .thenByDescending { it.pontos_dia }
                        )
                        val bestTop = bestRanked.first()
                        val bestTied = bestRanked.filter {
                            winRate(it) == winRate(bestTop) && it.pontos_dia == bestTop.pontos_dia
                        }

                        // Piorzinho do dia: menor taxa de aproveitamento; empate desempatado por mais
                        // derrotas (pior sequência) e depois por menos pontos.
                        val worstRanked = eligibleToday.sortedWith(
                            compareBy<com.marcioarruda.clubedodomino.data.network.RankingDto> { winRate(it) }
                                .thenByDescending { it.derrotas_dia }
                                .thenBy { it.pontos_dia }
                        )
                        val worstTop = worstRanked.first()
                        val worstTied = worstRanked.filter {
                            winRate(it) == winRate(worstTop) &&
                                it.derrotas_dia == worstTop.derrotas_dia &&
                                it.pontos_dia == worstTop.pontos_dia
                        }

                        topPlayers = bestTied.mapNotNull { r ->
                            val playerUser = allPlayers.find { u -> u.name.equals(r.jogador.trim(), ignoreCase = true) || u.displayName.equals(r.jogador.trim(), ignoreCase = true) }
                            playerUser?.let { BestPlayer(it, r.pontos_dia, r.vitorias_dia, r.partidas_dia) }
                        }

                        bottomPlayers = worstTied.mapNotNull { r ->
                            val playerUser = allPlayers.find { u -> u.name.equals(r.jogador.trim(), ignoreCase = true) || u.displayName.equals(r.jogador.trim(), ignoreCase = true) }
                            playerUser?.let { BestPlayer(it, r.pontos_dia, r.vitorias_dia, r.partidas_dia) }
                        }
                    }
                }

                // Lógica de celebração do campeão do mês
                var championCelebration: ChampionCelebration? = null
                try {
                    val tz = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
                    val zoneId = tz.toZoneId()
                    val today = java.time.LocalDate.now(zoneId)
                    val currentTime = java.time.LocalTime.now(zoneId)

                    var showCelebration = false
                    var targetYear = today.year
                    var targetMonth = today.monthValue

                    if (today.dayOfMonth == today.lengthOfMonth()) {
                        // Último dia do mês atual - ativa após o encerramento do cadastro (14h)
                        if (currentTime.isAfter(java.time.LocalTime.of(14, 0))) {
                            showCelebration = true
                        }
                    } else if (today.dayOfMonth == 1) {
                        // Primeiro dia do mês seguinte - ativa o dia todo para o mês anterior
                        showCelebration = true
                        val prevDate = today.minusMonths(1)
                        targetYear = prevDate.year
                        targetMonth = prevDate.monthValue
                    }

                    if (showCelebration) {
                        championCelebration = repository.getChampionCelebration(targetYear, targetMonth)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
                        worstPlayers = bottomPlayers,
                        championCelebration = championCelebration
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
        tickerFlow(periodMillis = 30_000, initialDelayMillis = 0)
            .onEach {
                val isAvailable = matchAvailabilityManager.isModuleAvailable(com.marcioarruda.clubedodomino.DominoClubApplication.instance, _uiState.value.user?.name)
                _uiState.update { it.copy(isNewMatchVisible = isAvailable) }
            }
            .launchIn(viewModelScope)
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
