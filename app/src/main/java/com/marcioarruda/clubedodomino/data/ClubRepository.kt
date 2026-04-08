package com.marcioarruda.clubedodomino.data

import com.google.gson.JsonSyntaxException
import com.marcioarruda.clubedodomino.data.network.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class ClubRepository(private val apiService: ApiService = RetrofitClient.instance) {

    private var allUsers: List<User> = emptyList()
    private var allMatches: List<Match> = emptyList()
    private var allMatchDTOs: List<MatchDTO> = emptyList()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    private val monthNameToIndex = mapOf(
        "Janeiro" to 0, "Fevereiro" to 1, "Março" to 2, "Abril" to 3, "Maio" to 4, "Junho" to 5,
        "Julho" to 6, "Agosto" to 7, "Setembro" to 8, "Outubro" to 9, "Novembro" to 10, "Dezembro" to 11
    )
    private val monthIndexToName = mapOf(
        0 to "Janeiro", 1 to "Fevereiro", 2 to "Março", 3 to "Abril", 4 to "Maio", 5 to "Junho",
        6 to "Julho", 7 to "Agosto", 8 to "Setembro", 9 to "Outubro", 10 to "Novembro", 11 to "Dezembro"
    )

    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> {
        return try {
            Result.success(apiCall())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlayer(userId: String): User? {
        if (allUsers.isEmpty()) {
            getPlayers()
        }
        return allUsers.find { it.id == userId }
    }

    suspend fun getTotalPlayers(): Int {
        if (allUsers.isEmpty()) {
            getPlayers()
        }
        return allUsers.size
    }

    suspend fun getMatchesCountToday(): Int {
        if (allMatches.isEmpty()) {
            getMatches()
        }
        val today = Calendar.getInstance()
        return allMatches.count {
            val matchDate = Calendar.getInstance()
            matchDate.time = it.date
            today.get(Calendar.YEAR) == matchDate.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == matchDate.get(Calendar.DAY_OF_YEAR)
        }
    }

    suspend fun getTotalDebt(userId: String): Double {
        val users = if (allUsers.isEmpty()) getPlayers() else allUsers

        val buchosResult = getBuchosResult()
        val mensalidadesResult = getMensalidadesResult()

        var totalDebt = 0.0

        buchosResult.onSuccess { buchos ->
            totalDebt += buchos.mapNotNull { it.toFinancialEntry(users) }
                .filter { it.userId == userId && it.status == FinancialEntryStatus.PENDING }
                .sumOf { it.amount }
        }

        mensalidadesResult.onSuccess { mensalidades ->
            totalDebt += mensalidades.mapNotNull { it.toFinancialEntry(users) }
                .filter { it.userId == userId && it.status == FinancialEntryStatus.PENDING }
                .sumOf { it.amount }
        }

        return totalDebt
    }

    suspend fun calcularPontosAno(usuarioLogado: User): Int {
        if (allMatchDTOs.isEmpty()) {
            getMatches()
        }
        
        val normalizedUserName = usuarioLogado.name.trim()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        
        return allMatchDTOs.filter { match ->
            // Critério A: Dupla Vencedora contains name
            val winnerPair = match.dupla_vencedora ?: ""
            val isUserWinner = winnerPair.contains(normalizedUserName, ignoreCase = true)
            
            // Critério B: Temporal
            var matchYear = -1
            match.data?.let {
                 try {
                     val date = dateFormat.parse(it)
                     if (date != null) {
                         val cal = Calendar.getInstance()
                         cal.time = date
                         matchYear = cal.get(Calendar.YEAR)
                     }
                 } catch (e: Exception) { }
            }
            
            isUserWinner && matchYear == currentYear
        }.sumOf { it.pts ?: 0 }
    }

    suspend fun getPlayers(): List<User> {
        return try {
            val playerDTOs = apiService.getPlayers()
            allUsers = playerDTOs.mapNotNull { it.toUser() }
            allUsers
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun login(email: String, pass: String): LoginResponse {
        return apiService.login(LoginRequest(email, pass))
    }

    suspend fun updatePassword(email: String, pass: String) {
        apiService.updatePlayer(UpdatePlayerRequest(email, pass))
    }

    suspend fun updateProfile(email: String, base64Image: String) {
        apiService.updateProfile(UpdateProfileRequest(email, base64Image))
    }

    suspend fun getMatches(): List<Match> {
        return try {
            val rawDTOs = apiService.getMatches()
            // Dedup matches based on ID
            val matchDTOs = rawDTOs.distinctBy { it.id }
            
            allMatchDTOs = matchDTOs
            val users = getPlayers()
            val matches = matchDTOs.mapNotNull { it.toMatch(users) }
            allMatches = matches
            matches
        } catch (e: Exception) {
            if (e is JsonSyntaxException || e is IllegalStateException) {
                emptyList()
            } else {
                throw e
            }
        }
    }

    suspend fun getMatch(matchId: String): Match? {
        if (allMatches.isEmpty()) {
            getMatches()
        }
        return allMatches.find { it.id == matchId }
    }

    suspend fun getRawMatchesResult(): Result<List<MatchDTO>> {
        return safeApiCall { 
            apiService.getMatches().distinctBy { it.id }
        }
    }

    suspend fun getBuchosResult(): Result<List<BuchoDto>> {
        return safeApiCall { apiService.getBuchos() }
    }

    suspend fun getMensalidadesResult(): Result<List<MensalidadeDto>> {
        return safeApiCall { apiService.getMensalidades() }
    }

    suspend fun createMensalidade(playerName: String, month: Int? = null, year: Int? = null) {
        val cal = Calendar.getInstance()
        if (year != null) cal.set(Calendar.YEAR, year)
        if (month != null) cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 10)
        
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = simpleDateFormat.format(cal.time)
        
        apiService.createMensalidade(CreateMensalidadeRequest(playerName, dateString))
    }

    suspend fun uploadComprovante(request: ComprovanteRequest) {
        apiService.uploadComprovante(request)
    }

    suspend fun getTaxasExtras(): List<TaxaExtraDto>? {
        return try {
            apiService.getTaxasExtras()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun registerMatch(match: Match) {
        apiService.registerMatch(match.toDTO())
    }

    suspend fun registerDebit(debitRequest: DebitRequest) {
        apiService.registerDebit(debitRequest)
    }

    suspend fun deleteMatch(id: String, buttonName: String = "Excluir") {
        apiService.deleteMatch(com.marcioarruda.clubedodomino.data.network.DeleteRequest(id, buttonName))
    }

    suspend fun updateMatch(match: Match) {
        apiService.updateMatch(match.toDTO(buttonName = "atualizar"))
    }

    suspend fun deleteBucho(id: String, buttonName: String = "Excluir") {
        apiService.deleteBucho(com.marcioarruda.clubedodomino.data.network.DeleteRequest(id, buttonName))
    }

    fun parseAnyDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.getDefault()).apply { isLenient = false }.parse(dateStr)
            } catch (e: Exception) {}
        }
        return null
    }

    fun BuchoDto.toFinancialEntry(users: List<User>): FinancialEntry? {
        if (this.id == null) return null 

        val cleanName = this.jogador?.trim() ?: return null
        
        val parts = cleanName.split("/").map { it.trim() }
        
        var user = users.find { u -> parts.any { p -> p.equals(u.name, ignoreCase = true) } }
        if (user == null) {
             user = users.find { u -> parts.any { p -> p.equals(u.displayName, ignoreCase = true) } }
        }
        
        val userId = user?.id ?: return null
        
        val parsedDate = parseAnyDate(this.data) ?: Date()

        val isTaxaExtra = this.obs?.contains("Taxa extra", ignoreCase = true) == true

        return FinancialEntry(
            id = UUID.randomUUID().toString(), // UI Key
            userId = userId,
            type = if (isTaxaExtra) FinancialEntryType.EXTRA_TAX else FinancialEntryType.BUCHO,
            amount = this.valor ?: 0.0,
            status = if (this.pago == true) FinancialEntryStatus.PAID else FinancialEntryStatus.PENDING,
            dueDate = parsedDate,
            description = if (isTaxaExtra) "Taxa Extra (Déficit Mês Anterior)" else (this.placar ?: "N/A"),
            winningPair = this.dupla_vencedora,
            losingPair = this.dupla_perdedora,
            originalRemoteId = this.id,
            originalReference = null
        )
    }

    fun MensalidadeDto.toFinancialEntry(users: List<User>): FinancialEntry? {
        if (this.id == null) return null 

        val cleanName = this.jogador?.trim() ?: return null
        val user = users.find { it.name.equals(cleanName, ignoreCase = true) } 
            ?: users.find { it.displayName.equals(cleanName, ignoreCase = true) }
        
        val userId = user?.id ?: return null
        
        var monthName = this.mensalidade ?: "N/A"
        var year = this.ano ?: Calendar.getInstance().get(Calendar.YEAR)
        var monthIndex: Int? = monthNameToIndex[monthName]

        if (monthIndex == null) {
            try {
                if (monthName.length >= 10) {
                    val datePart = monthName.substring(0, 10)
                    val simpleFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = simpleFormat.parse(datePart)
                    if (date != null) {
                        val cal = Calendar.getInstance()
                        cal.time = date
                        monthIndex = cal.get(Calendar.MONTH)
                        year = cal.get(Calendar.YEAR)
                        monthName = monthIndexToName[monthIndex] ?: "N/A"
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (monthIndex == null) return null

        val dueDate = Calendar.getInstance().apply { set(year, monthIndex, 10) }
        
        val ref = "$monthName/$year"

        return FinancialEntry(
            id = UUID.randomUUID().toString(), // UI Key
            userId = userId,
            type = FinancialEntryType.MONTHLY_FEE,
            amount = 10.0,
            status = if (this.pago == true) FinancialEntryStatus.PAID else FinancialEntryStatus.PENDING,
            dueDate = dueDate.time,
            description = "Mensalidade $ref",
            originalRemoteId = this.id,
            originalReference = ref
        )
    }



    private fun PlayerDTO.toUser(): User? {
        if (this.email.isNullOrBlank() || this.jogador.isNullOrBlank()) {
            return null
        }
        return User(
            id = this.email.trim(),
            name = this.jogador.trim(),
            displayName = this.jogador.trim(),
            photoUrl = this.avatar ?: "",
            clubId = "c1",
            isMember = true,
            password = this.senha?.trim()
        )
    }

    private fun MatchDTO.toMatch(users: List<User>): Match? {
        val t1p1 = users.find { it.name.equals(this.jogador1?.trim(), ignoreCase = true) } ?: return null
        val t1p2 = users.find { it.name.equals(this.jogador2?.trim(), ignoreCase = true) } ?: return null
        val t2p1 = users.find { it.name.equals(this.jogador3?.trim(), ignoreCase = true) } ?: return null
        val t2p2 = users.find { it.name.equals(this.jogador4?.trim(), ignoreCase = true) } ?: return null
        
        val stableId = "match_${this.data}_${this.jogador1}".hashCode().toString()
        val date = parseAnyDate(this.data) ?: Date()

        return Match(
            id = this.id?.toString() ?: stableId,
            date = date,
            team1Player1 = t1p1, team1Player2 = t1p2, team2Player1 = t2p1, team2Player2 = t2p2,
            score1 = this.scored1 ?: 0, score2 = this.scored2 ?: 0, wasBuchoRe = this.buchore ?: false, registeredBy = users.first(),
            pts = this.pts ?: 0
        )
    }

    fun Match.toDTO(buttonName: String? = null): MatchDTO {
        val winnerScore = if (this.score1 > this.score2) this.score1 else this.score2
        val loserScore = if (this.score1 > this.score2) this.score2 else this.score1
        val isBuchoSimple = (winnerScore == 6 && loserScore == 0)
        
        val points = if (this.wasBuchoRe) {
            winnerScore + 2
        } else if (isBuchoSimple) {
            winnerScore + 1
        } else {
            abs(this.score1 - this.score2)
        }

        return MatchDTO(
            id = this.id.toLongOrNull(),
            data = dateFormat.format(this.date), jogador1 = this.team1Player1.name,
            jogador2 = this.team1Player2.name, jogador3 = this.team2Player1.name, jogador4 = this.team2Player2.name,
            scored1 = this.score1, scored2 = this.score2, buchore = this.wasBuchoRe, pts = points,
            dupla_vencedora = if (score1 > score2) "${team1Player1.name}/${team1Player2.name}" else "${team2Player1.name}/${team2Player2.name}",
            buttonName = buttonName
        )
    }

    private fun String.normalize(): String {
        return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase()
    }
}
