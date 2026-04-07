package com.marcioarruda.clubedodomino.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.FinancialEntryStatus
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.network.DebitRequest
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
    val editingMatchRegisteredBy: User? = null
)

class MatchViewModel(
    private val repository: ClubRepository,
    private val adminRepository: com.marcioarruda.clubedodomino.data.AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchRegistrationState())
    val uiState: StateFlow<MatchRegistrationState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        loadPlayers()
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
                                    val isPast10th = todayCal.get(Calendar.DAY_OF_MONTH) >= 10
                                    
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
                                    val isPast10th = todayCal.get(Calendar.DAY_OF_MONTH) >= 10
                                    
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
                        // Rule: "Ativo/Inativo" - Only show active OR Non-Member
                        val isNonMember = user.name.contains("NÃO MEMBRO", ignoreCase = true) || user.name.contains("NAO MEMBRO", ignoreCase = true) || user.id == "7"
                        val isActive = adminRepository.isPlayerActive(user.id)
                        
                        // Condition: (Active AND Not Blocked) OR (Non-Member)
                        // Note: Non-Members are immune to blocking above, but explicitly here too:
                        // They must obey "Uniqueness" which is UI/Selection logic, not list loading.
                        // They are "Always Visible" (Active check bypassed)
                        
                        if (isNonMember) true // Always visible
                        else isActive && (user.id !in blockedUserIds)
                    }
                    .sortedBy { it.displayName }

                _uiState.update { 
                    it.copy(
                        availablePlayers = eligiblePlayers,
                        isLoading = false
                    ) 
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

        viewModelScope.launch {
            try {
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
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora
                        ))
                    } else if (loser2IsNonMember) {
                        // Loser 1 paga dobro
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = losers[0].name, valor = debitValue * 2,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora
                        ))
                    } else {
                        // Ambos pagam normal - Chamada Única para evitar duplicidade
                        val combinedNames = "${losers[0].name} / ${losers[1].name}"
                        repository.registerDebit(DebitRequest(
                            data = dateStr, jogador = combinedNames, valor = debitValue,
                            pago = false, placar = placarStr, dupla_vencedora = duplaVencedora, dupla_perdedora = duplaPerdedora
                        ))
                    }
                }

                // Regra 6: Atualizar lista e perguntar sobre repetição
                repository.getMatches() // Força atualização cache
                _uiState.update { it.copy(isLoading = false, showRepeatDialog = true) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao salvar: ${e.message}") }
            }
        }
    }

    private val matchAvailabilityManager = com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager(com.marcioarruda.clubedodomino.data.HolidayRepository)

    fun onRepeatMatch(repeat: Boolean) {
        if (repeat && !matchAvailabilityManager.isModuleAvailable()) {
            _uiState.update { 
                it.copy(
                    showRepeatDialog = false, 
                    error = "Fora do horário permitido para iniciar partidas!",
                    success = false // Stay on screen to show error, or set true to close? 
                                    // User said "inactivate", so just closing dialog + error is safer.
                ) 
            }
            return
        }

        _uiState.update {
            if (repeat) {
                it.copy(
                    showRepeatDialog = false,
                    score1 = 0,
                    score2 = 0,
                    isBuchoRe = false,
                    isBuchoReEnabled = false,
                    success = false // Reset success to allow editing again
                )
            } else {
                it.copy(
                    showRepeatDialog = false,
                    selectedPlayers = listOf(null, null, null, null),
                    score1 = 0,
                    score2 = 0,
                    isBuchoRe = false,
                    isBuchoReEnabled = false,
                    success = true // Close screen
                )
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
