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
    val totalVencido: Double = 0.0,
    val totalAVencer: Double = 0.0,
    val isNewMatchVisible: Boolean = false,
    val groupedMatches: Map<String, List<Match>> = emptyMap(),
    val bestPlayers: List<BestPlayer> = emptyList(),
    val worstPlayers: List<BestPlayer> = emptyList(),
    val isRefreshing: Boolean = false,
    val championCelebration: ChampionCelebration? = null,
    // KPI de participação no mês: partidas jogadas pelo usuário logado vs. meta mínima (média
    // parcial do grupo elegível no mês, arredondada pra cima) para não pegar taxa extra.
    val minhasPartidasNoMes: Int = 0,
    val metaPartidasNoMes: Int = 0
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
                val (totalVencido, totalAVencer) = repository.getDebtBreakdown(userId)

                // Carrega e processa as partidas recentes
                val allMatches = repository.getMatches().distinctBy { it.id }.sortedByDescending { it.date }
                val matches = allMatches.take(20)
                val groupedMatches = matches.groupBy { dateFormatter.format(it.date) }

                // Calculate Best and Worst Players of the Day using Ranking API
                var topPlayers = emptyList<BestPlayer>()
                var bottomPlayers = emptyList<BestPlayer>()

                val rankingResult = repository.getRankingResult()
                val allPlayers = repository.getPlayers()

                val (minhasPartidasNoMes, metaPartidasNoMes) = calcularParticipacaoNoMes(userId, allMatches, allPlayers)

                rankingResult.onSuccess { ranking ->
                    val awards = com.marcioarruda.clubedodomino.domain.calculateDailyAwards(ranking, allPlayers)
                    topPlayers = awards.best
                    bottomPlayers = awards.worst
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
                        totalVencido = totalVencido,
                        totalAVencer = totalAVencer,
                        groupedMatches = groupedMatches,
                        bestPlayers = topPlayers,
                        worstPlayers = bottomPlayers,
                        championCelebration = championCelebration,
                        minhasPartidasNoMes = minhasPartidasNoMes,
                        metaPartidasNoMes = metaPartidasNoMes
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

    // Calcula "quantas partidas o usuário logado já jogou no mês corrente" vs. a meta mínima para
    // não pegar taxa extra de buchos. Replica a mesma regra de elegibilidade e pró-rata de férias
    // usada no backend (gerarTaxaExtraBuchosParaMes em server.js), mas sobre o mês EM ANDAMENTO —
    // é uma projeção parcial que muda conforme mais partidas são registradas no mês.
    private fun calcularParticipacaoNoMes(userId: String, allMatches: List<Match>, allPlayers: List<User>): Pair<Int, Int> {
        val cal = java.util.Calendar.getInstance()
        val anoAtual = cal.get(java.util.Calendar.YEAR)
        val mesAtual = cal.get(java.util.Calendar.MONTH) // 0-based

        cal.set(anoAtual, mesAtual, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val inicioMes = cal.time
        cal.add(java.util.Calendar.MONTH, 1)
        val fimMes = cal.time // exclusivo
        val diasDoMes = ((fimMes.time - inicioMes.time) / 86400000L).toInt()

        // Elegibilidade: ativo, não "NÃO MEMBRO", e sem férias cobrindo o mês inteiro — mesma
        // regra SQL do backend (WHERE ativo=1 AND NOT (ferias cobre o mês inteiro) AND nome NOT LIKE '%NÃO MEMBRO%').
        fun ferasCobremMesInteiro(user: User): Boolean {
            val inicio = user.vacationStart ?: return false
            if (inicio.after(inicioMes)) return false // férias começam depois do 1º dia do mês
            val fim = user.vacationEnd
            return fim == null || !fim.before(cal.apply { time = fimMes; add(java.util.Calendar.DAY_OF_MONTH, -1) }.time)
        }

        val elegiveis = allPlayers.filter { user ->
            user.isActive &&
                !user.name.uppercase(java.util.Locale.ROOT).contains("NÃO MEMBRO") &&
                !ferasCobremMesInteiro(user)
        }
        if (elegiveis.isEmpty()) return 0 to 0

        val nomesElegiveis = elegiveis.map { it.name.trim().uppercase(java.util.Locale.ROOT) }.toSet()

        // Partidas do mês corrente
        val matchesDoMes = allMatches.filter { !it.date.before(inicioMes) && it.date.before(fimMes) }

        // Conta participações por jogador elegível (cada partida conta até 4 vezes, uma por jogador)
        val partidasPorJogador = nomesElegiveis.associateWith { 0 }.toMutableMap()
        for (match in matchesDoMes) {
            for (jogador in listOf(match.team1Player1, match.team1Player2, match.team2Player1, match.team2Player2)) {
                val nome = jogador.name.trim().uppercase(java.util.Locale.ROOT)
                if (nome in partidasPorJogador) partidasPorJogador[nome] = partidasPorJogador.getValue(nome) + 1
            }
        }

        // A média considera só quem de fato jogou pelo menos 1 partida no mês — um elegível que
        // ficou parado o mês inteiro não entra no denominador, senão "dilui" a média pra baixo e
        // deixa quem jogou bastante com uma meta artificialmente fácil (mesma regra usada em
        // gerarTaxaExtraBuchosParaMes no backend). A lista de elegíveis continua sendo quem PODE
        // ser contado (ativo, sem férias no mês inteiro) — isso só muda a base da média.
        val jogadoresQueJogaram = nomesElegiveis.count { (partidasPorJogador[it] ?: 0) > 0 }
        val totalPartidas = partidasPorJogador.values.sum()
        val avgMatches = if (jogadoresQueJogaram > 0) totalPartidas.toDouble() / jogadoresQueJogaram else 0.0

        // Fator de disponibilidade do jogador logado (dias fora de férias no mês / dias do mês) —
        // mesma lógica de diasDisponiveisNoMes no backend.
        val usuarioLogado = allPlayers.find { it.id == userId }
        val fator = usuarioLogado?.let { user ->
            val inicio = user.vacationStart
            if (inicio == null) 1.0
            else {
                val fimFerias = user.vacationEnd ?: fimMes
                val overlapInicio = if (inicio.after(inicioMes)) inicio else inicioMes
                val fimFeriasExclusivo = java.util.Date(fimFerias.time + 86400000L)
                val overlapFim = if (fimFeriasExclusivo.before(fimMes)) fimFeriasExclusivo else fimMes
                val diasDeFerias = maxOf(0L, (overlapFim.time - overlapInicio.time) / 86400000L)
                if (diasDoMes > 0) maxOf(0.0, (diasDoMes - diasDeFerias).toDouble() / diasDoMes) else 1.0
            }
        } ?: 1.0

        val metaMatches = kotlin.math.ceil(avgMatches * fator).toInt()
        val nomeLogadoKey = usuarioLogado?.name?.trim()?.uppercase(java.util.Locale.ROOT)
        val minhasPartidas = partidasPorJogador[nomeLogadoKey] ?: 0

        return minhasPartidas to metaMatches
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
