package com.marcioarruda.clubedodomino.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.FinancialEntryStatus
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.ActiveMatch
import com.marcioarruda.clubedodomino.data.network.DebitRequest
import com.marcioarruda.clubedodomino.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class TipoBatida(val pontos: Int, val label: String) {
    SIMPLES(1, "Simples"),
    CARROCA(2, "Carroça"),
    LA_E_LO(3, "Lá e Lô"),
    CRUZADA(4, "Cruzada")
}

data class MatchRegistrationState(
    val availablePlayers: List<User> = emptyList(),
    val selectedPlayers: List<User?> = listOf(null, null, null, null),
    val score1: Int = 0,
    val score2: Int = 0,
    val fechas: Int = 0,
    val showBatidaDialogForTeam: Int? = null,
    val isBuchoRe: Boolean = false,
    val isBuchoReEnabled: Boolean = false,
    val showRepeatDialog: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val editingMatchId: String? = null,
    val editingMatchDate: java.util.Date? = null,
    val editingMatchRegisteredBy: User? = null,
    val originalScore1: Int = 0,
    val originalScore2: Int = 0,
    val originalIsBuchoRe: Boolean = false,
    val originalPlayers: List<User> = emptyList(),
    val isModuleAvailable: Boolean = true,
    val remainingSecondsToClose: Long? = null,
    val isActiveMatchStarted: Boolean = false,
    val activeMatchId: String? = null
)

class MatchViewModel(
    private val repository: ClubRepository,
    private val adminRepository: com.marcioarruda.clubedodomino.data.AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchRegistrationState())
    val uiState: StateFlow<MatchRegistrationState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val matchAvailabilityManager = com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager

    var currentUserName: String? = null
        private set

    private fun isNonMemberPlayer(u: User): Boolean {
        return u.name.contains("NÃO MEMBRO", ignoreCase = true) ||
            u.name.contains("NAO MEMBRO", ignoreCase = true) ||
            u.id == "7"
    }

    fun setCurrentUser(name: String?) {
        if (name == currentUserName) return
        currentUserName = name
        if (name != null) loadActiveMatchForUser(name)
    }

    private fun loadActiveMatchForUser(username: String) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state.isActiveMatchStarted) return@launch
                val activeMatch = repository.getActiveMatchForUser(username) ?: return@launch
                val players = state.availablePlayers
                val p1 = players.find { it.name == activeMatch.player1 }
                val p2 = players.find { it.name == activeMatch.player2 }
                val p3 = players.find { it.name == activeMatch.player3 }
                val p4 = players.find { it.name == activeMatch.player4 }
                _uiState.update {
                    it.copy(
                        selectedPlayers = listOf(p1, p2, p3, p4),
                        isActiveMatchStarted = true,
                        activeMatchId = activeMatch.id
                    )
                }
            } catch (_: Exception) {}
        }
    }

    init {
        loadPlayers()
        startAutoCloseTimer()
    }

    private fun startAutoCloseTimer() {
        viewModelScope.launch {
            while (true) {
                val context = com.marcioarruda.clubedodomino.DominoClubApplication.instance
                val currentState = _uiState.value
                if (currentState.editingMatchId == null) {
                    // Partida já confirmada como ativa localmente: libera sem depender de nova consulta ao banco.
                    val available = currentState.isActiveMatchStarted ||
                        matchAvailabilityManager.isModuleAvailable(context, currentUserName)
                    val remainingSeconds = matchAvailabilityManager.getRemainingSecondsToClose(context, currentUserName)

                    if (!available) {
                        val diagInfo = matchAvailabilityManager.getExtendedDiagnosticInfo(context)
                        _uiState.update {
                            if (it.success) it else it.copy(
                                error = "MÓDULO BLOQUEADO\n$diagInfo\n\nCertifique-se de que 'Data e Hora Automáticas' está ATIVA nas configurações do sistema.",
                                success = false,
                                isModuleAvailable = false,
                                remainingSecondsToClose = null
                            )
                        }
                    } else {
                        _uiState.update { it.copy(
                            error = null,
                            isModuleAvailable = true,
                            remainingSecondsToClose = remainingSeconds
                        ) }
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun loadPlayers() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val users = repository.getPlayers()
                
                // Fetch financial data for blocking logic
                val buchosResult = repository.getBuchosResult()
                val mensalidadesResult = repository.getMensalidadesResult()
                
                val blockedUserIds = mutableSetOf<String>()
                
                // Current Date (Start of Month)
                val currentCal = Calendar.getInstance()
                currentCal.set(Calendar.DAY_OF_MONTH, 1)
                currentCal.set(Calendar.HOUR_OF_DAY, 0)
                currentCal.set(Calendar.MINUTE, 0)
                currentCal.set(Calendar.SECOND, 0)
                currentCal.set(Calendar.MILLISECOND, 0)
                val startOfCurrentMonth = currentCal.time

                with(repository) {
                    // Process Buchos
                    buchosResult.onSuccess { list ->
                        list.forEach { dto ->
                            val entry = dto.toFinancialEntry(users)
                            if (entry != null && entry.status != FinancialEntryStatus.PAID) {
                                // Rule: Non-Member Immunity
                                val user = users.find { it.id == entry.userId }
                                val isNonMember = user?.name?.contains("NÃO MEMBRO", ignoreCase = true) == true || user?.id == "7"
                                
                                if (!isNonMember) {
                                    val entryCal = Calendar.getInstance()
                                    entryCal.time = entry.dueDate
                                    entryCal.set(Calendar.DAY_OF_MONTH, 1)
                                    // ... Reset time parts
                                    entryCal.set(Calendar.HOUR_OF_DAY, 0); entryCal.set(Calendar.MINUTE, 0); entryCal.set(Calendar.SECOND, 0); entryCal.set(Calendar.MILLISECOND, 0)
                                    
                                    val todayCal = Calendar.getInstance()
                                    val isPast10th = todayCal.get(Calendar.DAY_OF_MONTH) >= 11
                                    
                                    val prevMonthCal = Calendar.getInstance().apply {
                                        add(Calendar.MONTH, -1)
                                        set(Calendar.DAY_OF_MONTH, 1)
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }

                                    val isDebtFromBeforePreviousMonth = entryCal.time.before(prevMonthCal.time)
                                    val isDebtFromPreviousMonthOrOlder = entryCal.time.before(startOfCurrentMonth)

                                    if (isDebtFromBeforePreviousMonth) {
                                        blockedUserIds.add(entry.userId)
                                    } else if (isDebtFromPreviousMonthOrOlder && isPast10th) {
                                        blockedUserIds.add(entry.userId)
                                    }
                                }
                            }
                        }
                    }
                    
                    // Process Mensalidades
                    mensalidadesResult.onSuccess { list ->
                         list.forEach { dto ->
                            val entry = dto.toFinancialEntry(users)
                            if (entry != null && entry.status != FinancialEntryStatus.PAID) {
                                val user = users.find { it.id == entry.userId }
                                val isNonMember = user?.name?.contains("NÃO MEMBRO", ignoreCase = true) == true || user?.id == "7"

                                if (!isNonMember) {
                                    val entryCal = Calendar.getInstance()
                                    entryCal.time = entry.dueDate
                                    entryCal.set(Calendar.DAY_OF_MONTH, 1)
                                    // ... Reset time parts
                                    entryCal.set(Calendar.HOUR_OF_DAY, 0); entryCal.set(Calendar.MINUTE, 0); entryCal.set(Calendar.SECOND, 0); entryCal.set(Calendar.MILLISECOND, 0)

                                    val todayCal = Calendar.getInstance()
                                    val isPast10th = todayCal.get(Calendar.DAY_OF_MONTH) >= 11
                                    
                                    val prevMonthCal = Calendar.getInstance().apply {
                                        add(Calendar.MONTH, -1)
                                        set(Calendar.DAY_OF_MONTH, 1)
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }

                                    val isDebtFromBeforePreviousMonth = entryCal.time.before(prevMonthCal.time)
                                    val isDebtFromPreviousMonthOrOlder = entryCal.time.before(startOfCurrentMonth)

                                    if (isDebtFromBeforePreviousMonth) {
                                        blockedUserIds.add(entry.userId)
                                    } else if (isDebtFromPreviousMonthOrOlder && isPast10th) {
                                        blockedUserIds.add(entry.userId)
                                    }
                                }
                            }
                        }
                    }
                }

                val allUsers = users.toMutableList()
                val hasNonMember = allUsers.any { it.name.contains("NÃO MEMBRO", ignoreCase = true) || it.name.contains("NAO MEMBRO", ignoreCase = true) || it.id == "7" }
                if (!hasNonMember) {
                    allUsers.add(User(
                        id = "7", 
                        name = "JOGADOR NÃO MEMBRO", 
                        displayName = "NÃO MEMBRO", 
                        photoUrl = "", 
                        clubId = "", 
                        isMember = false
                    ))
                }

                val eligiblePlayers = allUsers
                    .filter { user ->
                        val isNonMember = user.name.contains("NÃO MEMBRO", ignoreCase = true) || user.name.contains("NAO MEMBRO", ignoreCase = true) || user.id == "7"
                        if (isNonMember) true
                        else user.isActive && (user.id !in blockedUserIds)
                    }
                    .sortedBy { it.displayName }

                _uiState.update { 
                    it.copy(
                        availablePlayers = eligiblePlayers,
                        isLoading = false
                    ) 
                }

                currentUserName?.let { username ->
                    val activeMatch = repository.getActiveMatchForUser(username)
                    if (activeMatch != null) {
                        val p1 = eligiblePlayers.find { it.name == activeMatch.player1 }
                        val p2 = eligiblePlayers.find { it.name == activeMatch.player2 }
                        val p3 = eligiblePlayers.find { it.name == activeMatch.player3 }
                        val p4 = eligiblePlayers.find { it.name == activeMatch.player4 }
                        _uiState.update {
                            it.copy(
                                selectedPlayers = listOf(p1, p2, p3, p4),
                                isActiveMatchStarted = true,
                                activeMatchId = activeMatch.id
                            )
                        }
                    }
                } ?: run {
                    // currentUserName ainda não chegou — loadActiveMatchForUser será chamado pelo setCurrentUser
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Falha ao carregar jogadores: ${e.message}") }
            }
        }
    }

    fun onPlayerSelected(index: Int, player: User) {
        _uiState.update {
            val newSelectedPlayers = it.selectedPlayers.toMutableList()
            newSelectedPlayers[index] = player
            it.copy(selectedPlayers = newSelectedPlayers)
        }
    }

    fun onScoreChange(team: Int, score: Int) {
        _uiState.update {
            val s1 = if (team == 1) score else it.score1
            val s2 = if (team == 2) score else it.score2

            // Regra 5: Bucho de Ré disponível apenas se um dos placares for 5 e o outro for maior
            val buchoReEnabled = (s1 == 5 && s2 > 5) || (s2 == 5 && s1 > 5)
            // Se desabilitar, desmarca
            val isBuchoRe = if (buchoReEnabled) it.isBuchoRe else false

            it.copy(
                score1 = s1,
                score2 = s2,
                isBuchoReEnabled = buchoReEnabled,
                isBuchoRe = isBuchoRe
            )
        }
    }

    fun onScoreIncrement(team: Int) {
        val state = _uiState.value
        if (state.fechas > 0) {
            _uiState.update { it.copy(showBatidaDialogForTeam = team) }
        } else {
            val current = if (team == 1) state.score1 else state.score2
            onScoreChange(team, current + 1)
        }
    }

    fun onFechasChange(value: Int) {
        _uiState.update { it.copy(fechas = if (value < 0) 0 else value) }
    }

    fun onBatidaSelected(tipo: TipoBatida) {
        val state = _uiState.value
        val team = state.showBatidaDialogForTeam ?: return
        val ganho = state.fechas + tipo.pontos
        val novoScore1 = if (team == 1) state.score1 + ganho else state.score1
        val novoScore2 = if (team == 2) state.score2 + ganho else state.score2

        _uiState.update { it.copy(showBatidaDialogForTeam = null, fechas = 0) }
        onScoreChange(if (team == 1) 1 else 2, if (team == 1) novoScore1 else novoScore2)
    }

    fun onDismissBatidaDialog() {
        _uiState.update { it.copy(showBatidaDialogForTeam = null) }
    }

    fun onBuchoReChanged(isBuchoRe: Boolean) {
        _uiState.update { it.copy(isBuchoRe = isBuchoRe) }
    }

    fun saveMatch(registeredBy: User) {
        val state = _uiState.value
        if (state.isLoading) return // Prevent double clicks
        
        if (state.selectedPlayers.any { it == null }) {
            _uiState.update { it.copy(error = "Selecione todos os 4 jogadores.") }
            return
        }

        // Validação básica de nomes duplicados (redundante com filtro de UI, mas seguro)
        val distinctPlayers = state.selectedPlayers.filterNotNull().map { it.id }.distinct()
        if (distinctPlayers.size != 4) {
            _uiState.update { it.copy(error = "Jogadores não podem ser repetidos.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        // Regra: O placar mínimo de uma das duplas deve ser 6
        val s1 = state.score1
        val s2 = state.score2
        if (s1 < 6 && s2 < 6) {
            _uiState.update { it.copy(isLoading = false, error = "Placar de criança? Pelo menos uma dupla tem que ter 6 pontos. Joguem de verdade!") }
            return
        }

        viewModelScope.launch {
            try {
                // Partida já confirmada como ativa localmente: dispensa nova checagem de horário.
                if (!state.isActiveMatchStarted &&
                    !matchAvailabilityManager.isModuleAvailable(com.marcioarruda.clubedodomino.DominoClubApplication.instance, currentUserName)
                ) {
                    val diag = matchAvailabilityManager.getExtendedDiagnosticInfo(com.marcioarruda.clubedodomino.DominoClubApplication.instance)
                    _uiState.update { it.copy(isLoading = false, error = "MÓDULO BLOQUEADO\n$diag") }
                    return@launch
                }

                // As duplas já foram sorteadas em startMatch() (ou mantidas na ordem selecionada,
                // se algum jogador for NÃO MEMBRO) e persistidas em selectedPlayers — aqui só se
                // usa a ordem já definida, sem sortear de novo.
                val p1 = state.selectedPlayers[0]!!
                val p2 = state.selectedPlayers[1]!!
                val p3 = state.selectedPlayers[2]!!
                val p4 = state.selectedPlayers[3]!!

                // Lógica de Vencedores e Pontuação
                val isTeam1Winner = state.score1 > state.score2
                val winners = if (isTeam1Winner) listOf(p1, p2) else listOf(p3, p4)
                val losers = if (isTeam1Winner) listOf(p3, p4) else listOf(p1, p2)
                val winnerScore = if (isTeam1Winner) state.score1 else state.score2
                val loserScore = if (isTeam1Winner) state.score2 else state.score1

                val duplaVencedora = "${winners[0].displayName}/${winners[1].displayName}"
                val duplaPerdedora = "${losers[0].displayName}/${losers[1].displayName}"

                // Regra 2: Bucho Simples (qualquer placar x 0)
                val isBuchoSimple = (loserScore == 0)
                // Regra 5: Bucho de Ré - Apenas se marcado no checkbox (UI State)
                val isBuchoRe = state.isBuchoRe

                // Regra 4: Cálculo de Pontos e Valor
                var points = 0
                var debitValue = 0.0

                if (isBuchoRe) {
                    points = winnerScore + 2
                    debitValue = 3.00
                } else if (isBuchoSimple) {
                    points = winnerScore + 1
                    debitValue = 2.00
                } else {
                    points = abs(state.score1 - state.score2)
                    debitValue = 0.0 
                }

                // Salvar Partida
                val match = Match(
                    id = UUID.randomUUID().toString(),
                    date = Date(),
                    team1Player1 = p1,
                    team1Player2 = p2,
                    team2Player1 = p3,
                    team2Player2 = p4,
                    score1 = state.score1,
                    score2 = state.score2,
                    wasBuchoRe = isBuchoRe,
                    registeredBy = registeredBy,
                    pts = points // Passando pontos calculados explicitamente
                )
                
                repository.registerMatch(match)

                // Regra 2 e 4: Registrar Débito (Bucho)
                if (debitValue > 0.0) {
                    val dateStr = dateFormat.format(Date())
                    val placarStr = "${state.score1}x${state.score2}"

                    // Regra 4: Jogador Não Membro
                    fun isNonMember(u: User): Boolean {
                        return u.name.contains("NÃO MEMBRO", ignoreCase = true) || u.id == "7"
                    }

                    val loser1IsNonMember = isNonMember(losers[0])
                    val loser2IsNonMember = isNonMember(losers[1])

                    // Logica de pagamento
                    if (loser1IsNonMember && loser2IsNonMember) {
                        // Ninguém paga
                    } else if (loser1IsNonMember) {
                        // Loser 2 paga dobro
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[1].name, valor = debitValue * 2,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = registeredBy.name, wasBuchoRe = state.isBuchoRe
                        ))
                    } else if (loser2IsNonMember) {
                        // Loser 1 paga dobro
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[0].name, valor = debitValue * 2,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = registeredBy.name, wasBuchoRe = state.isBuchoRe
                        ))
                    } else {
                        // Ambos pagam — uma linha por jogador
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[0].name, valor = debitValue,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = registeredBy.name, wasBuchoRe = state.isBuchoRe
                        ))
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[1].name, valor = debitValue,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = registeredBy.name, wasBuchoRe = state.isBuchoRe
                        ))
                    }
                }

                state.activeMatchId?.let { id ->
                    repository.deleteActiveMatch(id)
                }

                // Regra 6: Atualizar lista e perguntar sobre repetição
                repository.getMatches() // Força atualização cache
                _uiState.update { it.copy(isLoading = false, showRepeatDialog = true) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao salvar: ${e.message}") }
            }
        }
    }

    fun onRepeatMatch(repeat: Boolean) {
        viewModelScope.launch {
            if (repeat && !matchAvailabilityManager.isModuleAvailable(com.marcioarruda.clubedodomino.DominoClubApplication.instance, currentUserName)) {
                _uiState.update { 
                    it.copy(
                        showRepeatDialog = false, 
                        error = "Fora do horário permitido para iniciar partidas!",
                        success = false
                    ) 
                }
                return@launch
            }

            _uiState.update {
                if (repeat) {
                    val chosenPlayers = it.selectedPlayers.filterNotNull()
                    val shouldShuffle = chosenPlayers.size == 4 && chosenPlayers.none { p -> isNonMemberPlayer(p) }
                    val reshuffledPlayers = if (shouldShuffle) chosenPlayers.shuffled() else it.selectedPlayers

                    it.copy(
                        showRepeatDialog = false,
                        selectedPlayers = reshuffledPlayers,
                        score1 = 0,
                        score2 = 0,
                        fechas = 0,
                        isBuchoRe = false,
                        isBuchoReEnabled = false,
                        success = false
                    )
                } else {
                    it.copy(
                        showRepeatDialog = false,
                        selectedPlayers = listOf(null, null, null, null),
                        score1 = 0,
                        score2 = 0,
                        fechas = 0,
                        isBuchoRe = false,
                        isBuchoReEnabled = false,
                        success = true
                    )
                }
            }
        }
    }

    fun startMatch() {
        val state = _uiState.value
        if (state.selectedPlayers.any { it == null }) {
            _uiState.update { it.copy(error = "Selecione todos os 4 jogadores.") }
            return
        }

        val distinctPlayers = state.selectedPlayers.filterNotNull().map { it.id }.distinct()
        if (distinctPlayers.size != 4) {
            _uiState.update { it.copy(error = "Jogadores não podem ser repetidos.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                if (!matchAvailabilityManager.isModuleAvailable(com.marcioarruda.clubedodomino.DominoClubApplication.instance, currentUserName)) {
                    val diag = matchAvailabilityManager.getExtendedDiagnosticInfo(com.marcioarruda.clubedodomino.DominoClubApplication.instance)
                    _uiState.update { it.copy(isLoading = false, error = "MÓDULO BLOQUEADO\n$diag") }
                    return@launch
                }

                val activeMatches = repository.getActiveMatches()
                val selectedNames = state.selectedPlayers.filterNotNull().map { it.name }

                var conflictPlayer: String? = null
                for (match in activeMatches) {
                    val activePlayers = listOf(match.player1, match.player2, match.player3, match.player4)
                    val overlap = selectedNames.find { it in activePlayers }
                    if (overlap != null) {
                        conflictPlayer = overlap
                        break
                    }
                }

                if (conflictPlayer != null) {
                    _uiState.update { it.copy(isLoading = false, error = "O participante $conflictPlayer já está vinculado a uma partida em andamento!") }
                    return@launch
                }

                // Sorteia as duplas ao abrir a partida, desde que nenhum dos 4 seja NÃO MEMBRO.
                val chosenPlayers = state.selectedPlayers.filterNotNull()
                val shouldShuffle = chosenPlayers.none { isNonMemberPlayer(it) }
                val orderedPlayers = if (shouldShuffle) chosenPlayers.shuffled() else chosenPlayers

                val matchId = UUID.randomUUID().toString()
                val newActiveMatch = ActiveMatch(
                    id = matchId,
                    player1 = orderedPlayers[0].name,
                    player2 = orderedPlayers[1].name,
                    player3 = orderedPlayers[2].name,
                    player4 = orderedPlayers[3].name,
                    cadastrador = currentUserName ?: "Desconhecido"
                )

                val success = repository.startActiveMatch(newActiveMatch)
                if (success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isActiveMatchStarted = true,
                            activeMatchId = matchId,
                            selectedPlayers = orderedPlayers,
                            error = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Erro ao iniciar partida no banco de dados.") }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao iniciar partida: ${e.message}") }
            }
        }
    }

    fun cancelActiveMatch() {
        val state = _uiState.value
        val matchId = state.activeMatchId ?: return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                val success = repository.deleteActiveMatch(matchId)
                if (success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isActiveMatchStarted = false,
                            activeMatchId = null,
                            score1 = 0,
                            score2 = 0,
                            fechas = 0,
                            isBuchoRe = false,
                            isBuchoReEnabled = false,
                            selectedPlayers = listOf(null, null, null, null)
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Erro ao cancelar partida no banco de dados.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao cancelar: ${e.message}") }
            }
        }
    }
    
    fun dismissDialog() {
        _uiState.update { it.copy(showRepeatDialog = false) }
    }

    fun loadMatch(matchId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                if (_uiState.value.availablePlayers.isEmpty()) {
                    val users = repository.getPlayers()
                    _uiState.update { it.copy(availablePlayers = users) }
                }
                
                val matches = repository.getMatches()
                val match = matches.find { it.id == matchId }
                
                if (match != null) {
                    _uiState.update {
                        it.copy(
                            selectedPlayers = listOf(match.team1Player1, match.team1Player2, match.team2Player1, match.team2Player2),
                            score1 = match.score1,
                            score2 = match.score2,
                            isBuchoRe = match.wasBuchoRe,
                            isBuchoReEnabled = (match.score1 == 5 && match.score2 > 5) || (match.score2 == 5 && match.score1 > 5),
                            isLoading = false,
                            editingMatchId = match.id,
                            editingMatchDate = match.date,
                            editingMatchRegisteredBy = match.registeredBy,
                            originalScore1 = match.score1,
                            originalScore2 = match.score2,
                            originalIsBuchoRe = match.wasBuchoRe,
                            originalPlayers = listOf(match.team1Player1, match.team1Player2, match.team2Player1, match.team2Player2)
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Partida não encontrada.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao carregar partida: ${e.message}") }
            }
        }
    }

    fun updateMatch(matchId: String) {
        val preCheckState = _uiState.value
        if (preCheckState.selectedPlayers.any { it == null }) {
            _uiState.update { it.copy(error = "Selecione todos os 4 jogadores.") }
            return
        }
        val distinctPlayers = preCheckState.selectedPlayers.filterNotNull().map { it.id }.distinct()
        if (distinctPlayers.size != 4) {
            _uiState.update { it.copy(error = "Jogadores não podem ser repetidos.") }
            return
        }
        val s1 = preCheckState.score1
        val s2 = preCheckState.score2
        if (s1 < 6 && s2 < 6) {
            _uiState.update { it.copy(error = "Placar de criança? Pelo menos uma dupla tem que ter 6 pontos. Joguem de verdade!") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val state = _uiState.value
                val editingDate = state.editingMatchDate ?: Date()

                // Só o próprio cadastrador pode editar, e apenas no mesmo dia da partida
                // e dentro da janela de horário permitida para cadastro.
                val registeredByName = state.editingMatchRegisteredBy?.name?.trim()
                val isOwner = currentUserName != null && registeredByName != null &&
                    currentUserName!!.trim().equals(registeredByName, ignoreCase = true)
                if (!isOwner) {
                    _uiState.update { it.copy(isLoading = false, error = "Apenas quem cadastrou a partida pode editá-la.") }
                    return@launch
                }
                if (!matchAvailabilityManager.canEditMatch(com.marcioarruda.clubedodomino.DominoClubApplication.instance, currentUserName, editingDate)) {
                    _uiState.update { it.copy(isLoading = false, error = "Só é possível editar a partida no mesmo dia e dentro do horário de cadastro.") }
                    return@launch
                }

                val p1 = state.selectedPlayers[0]!!
                val p2 = state.selectedPlayers[1]!!
                val p3 = state.selectedPlayers[2]!!
                val p4 = state.selectedPlayers[3]!!

                val oldWinnerScore = maxOf(state.originalScore1, state.originalScore2)
                val oldLoserScore = minOf(state.originalScore1, state.originalScore2)
                val oldIsBuchoSimple = oldLoserScore == 0
                val oldIsBuchoRe = state.originalIsBuchoRe
                val oldDebitValue = if (oldIsBuchoRe) 3.00 else if (oldIsBuchoSimple) 2.00 else 0.0

                val newWinnerScore = maxOf(state.score1, state.score2)
                val newLoserScore = minOf(state.score1, state.score2)
                val newIsBuchoSimple = newLoserScore == 0
                val newIsBuchoRe = state.isBuchoRe
                val newDebitValue = if (newIsBuchoRe) 3.00 else if (newIsBuchoSimple) 2.00 else 0.0

                val isTeam1Winner = state.score1 > state.score2
                val winners = if (isTeam1Winner) listOf(p1, p2) else listOf(p3, p4)
                val losers = if (isTeam1Winner) listOf(p3, p4) else listOf(p1, p2)
                val duplaVencedora = "${winners[0].displayName}/${winners[1].displayName}"
                val duplaPerdedora = "${losers[0].displayName}/${losers[1].displayName}"

                val points = when {
                    newIsBuchoRe -> newWinnerScore + 2
                    newIsBuchoSimple -> newWinnerScore + 1
                    else -> abs(state.score1 - state.score2)
                }

                val match = Match(
                    id = matchId,
                    date = editingDate,
                    team1Player1 = p1,
                    team1Player2 = p2,
                    team2Player1 = p3,
                    team2Player2 = p4,
                    score1 = state.score1,
                    score2 = state.score2,
                    wasBuchoRe = newIsBuchoRe,
                    registeredBy = state.editingMatchRegisteredBy ?: p1,
                    pts = points
                )

                repository.updateMatch(match)

                // Ajusta débitos de bucho se o resultado deixou de ser/passou a ser bucho.
                // Usa as duplas ORIGINAIS (calculadas a partir dos jogadores/placar antes da edição)
                // para localizar o débito antigo — usar as duplas novas aqui faria a busca falhar
                // sempre que a edição trocasse algum jogador, deixando o débito antigo órfão e
                // ainda criando um novo (cobrança em duplicidade).
                if (oldDebitValue > 0.0 && state.originalPlayers.size == 4) {
                    val oldP1 = state.originalPlayers[0]
                    val oldP2 = state.originalPlayers[1]
                    val oldP3 = state.originalPlayers[2]
                    val oldP4 = state.originalPlayers[3]
                    val oldIsTeam1Winner = state.originalScore1 > state.originalScore2
                    val oldWinners = if (oldIsTeam1Winner) listOf(oldP1, oldP2) else listOf(oldP3, oldP4)
                    val oldLosers = if (oldIsTeam1Winner) listOf(oldP3, oldP4) else listOf(oldP1, oldP2)
                    val oldDuplaVencedora = "${oldWinners[0].displayName}/${oldWinners[1].displayName}"
                    val oldDuplaPerdedora = "${oldLosers[0].displayName}/${oldLosers[1].displayName}"

                    val oldPlacarStr = "${state.originalScore1}x${state.originalScore2}"
                    val oldDateStr = dateFormat.format(editingDate)
                    repository.getBuchosResult().getOrNull()
                        ?.filter {
                            it.placar == oldPlacarStr && it.data?.take(10) == oldDateStr &&
                                it.dupla_vencedora == oldDuplaVencedora && it.dupla_perdedora == oldDuplaPerdedora
                        }
                        ?.forEach { bucho -> bucho.id?.let { repository.deleteBucho(it.toString()) } }
                }

                if (newDebitValue > 0.0) {
                    val dateStr = dateFormat.format(editingDate)
                    val placarStr = "${state.score1}x${state.score2}"

                    val loser1IsNonMember = isNonMemberPlayer(losers[0])
                    val loser2IsNonMember = isNonMemberPlayer(losers[1])

                    if (loser1IsNonMember && loser2IsNonMember) {
                        // Ninguém paga
                    } else if (loser1IsNonMember) {
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[1].name, valor = newDebitValue * 2,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = match.registeredBy.name, wasBuchoRe = newIsBuchoRe
                        ))
                    } else if (loser2IsNonMember) {
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[0].name, valor = newDebitValue * 2,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = match.registeredBy.name, wasBuchoRe = newIsBuchoRe
                        ))
                    } else {
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[0].name, valor = newDebitValue,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = match.registeredBy.name, wasBuchoRe = newIsBuchoRe
                        ))
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[1].name, valor = newDebitValue,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora,
                            cadastrado_por = match.registeredBy.name, wasBuchoRe = newIsBuchoRe
                        ))
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        success = true,
                        editingMatchId = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao atualizar: ${e.message}") }
            }
        }
    }
}
