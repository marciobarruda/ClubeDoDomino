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

data class MatchRegistrationState(
    val availablePlayers: List<User> = emptyList(),
    val selectedPlayers: List<User?> = listOf(null, null, null, null),
    val score1: Int = 0,
    val score2: Int = 0,
    val isBuchoRe: Boolean = false,
    val isBuchoReEnabled: Boolean = false,
    val showRepeatDialog: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val editingMatchId: String? = null,
    val editingMatchDate: java.util.Date? = null,
    val editingMatchRegisteredBy: User? = null,
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
                if (_uiState.value.editingMatchId == null) {
                    val available = matchAvailabilityManager.isModuleAvailable(context, currentUserName)
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
                if (!matchAvailabilityManager.isModuleAvailable(com.marcioarruda.clubedodomino.DominoClubApplication.instance, currentUserName)) {
                    val diag = matchAvailabilityManager.getExtendedDiagnosticInfo(com.marcioarruda.clubedodomino.DominoClubApplication.instance)
                    _uiState.update { it.copy(isLoading = false, error = "MÓDULO BLOQUEADO\n$diag") }
                    return@launch
                }

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
                        // Ambos pagam normal - Chamada Única para evitar duplicidade
                        val combinedNames = "${losers[0].name} / ${losers[1].name}"
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = combinedNames, valor = debitValue,
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
                    it.copy(
                        showRepeatDialog = false,
                        score1 = 0,
                        score2 = 0,
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

                val matchId = UUID.randomUUID().toString()
                val newActiveMatch = ActiveMatch(
                    id = matchId,
                    player1 = state.selectedPlayers[0]!!.name,
                    player2 = state.selectedPlayers[1]!!.name,
                    player3 = state.selectedPlayers[2]!!.name,
                    player4 = state.selectedPlayers[3]!!.name,
                    cadastrador = currentUserName ?: "Desconhecido"
                )

                val success = repository.startActiveMatch(newActiveMatch)
                if (success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isActiveMatchStarted = true,
                            activeMatchId = matchId,
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
                            editingMatchRegisteredBy = match.registeredBy
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val state = _uiState.value
                val p1 = state.selectedPlayers[0]!!
                val p2 = state.selectedPlayers[1]!!
                val p3 = state.selectedPlayers[2]!!
                val p4 = state.selectedPlayers[3]!!

                val match = Match(
                    id = matchId,
                    date = state.editingMatchDate ?: java.util.Date(),
                    team1Player1 = p1,
                    team1Player2 = p2,
                    team2Player1 = p3,
                    team2Player2 = p4,
                    score1 = state.score1,
                    score2 = state.score2,
                    wasBuchoRe = state.isBuchoRe,
                    registeredBy = state.editingMatchRegisteredBy ?: p1,
                    pts = 0 // Repositório atualizará baseado nos novos placares
                )

                repository.updateMatch(match)
                
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
