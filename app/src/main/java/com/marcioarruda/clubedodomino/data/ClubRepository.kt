package com.marcioarruda.clubedodomino.data

import com.marcioarruda.clubedodomino.data.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class ClubRepository {

    private val api = RetrofitClient.instance

    private var allUsers: List<User> = emptyList()
    private var allMatches: List<Match> = emptyList()
    private var allMatchDTOs: List<MatchDTO> = emptyList()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    private val monthNameToIndex = mapOf(
        "Janeiro" to 0, "Fevereiro" to 1, "Março" to 2, "Abril" to 3,
        "Maio" to 4, "Junho" to 5, "Julho" to 6, "Agosto" to 7,
        "Setembro" to 8, "Outubro" to 9, "Novembro" to 10, "Dezembro" to 11
    )
    private val monthIndexToName = mapOf(
        0 to "Janeiro", 1 to "Fevereiro", 2 to "Março", 3 to "Abril",
        4 to "Maio", 5 to "Junho", 6 to "Julho", 7 to "Agosto",
        8 to "Setembro", 9 to "Outubro", 10 to "Novembro", 11 to "Dezembro"
    )

    private suspend fun <T> safeDbCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try { Result.success(block()) } catch (t: Throwable) { Result.failure(Exception(t)) }
        }

    // ─── Players ──────────────────────────────────────────────────────────

    suspend fun getPlayer(userId: String): User? {
        if (allUsers.isEmpty()) getPlayers()
        return allUsers.find { it.id == userId }
    }

    suspend fun getTotalPlayers(): Int {
        if (allUsers.isEmpty()) getPlayers()
        return allUsers.size
    }

    suspend fun getPlayers(): List<User> = withContext(Dispatchers.IO) {
        try {
            val dtos = api.getPlayers()
            val users = dtos.mapNotNull { it.toUser() }.toMutableList()
            if (users.none { it.name.contains("NÃO MEMBRO", ignoreCase = true) }) {
                users.add(User("7", "JOGADOR NÃO MEMBRO", "NÃO MEMBRO", "", "c1", false))
            }
            allUsers = users
            users
        } catch (t: Throwable) {
            t.printStackTrace()
            val rootCause = generateSequence(t) { it.cause }.lastOrNull() ?: t
            throw Exception("Erro ao buscar jogadores: $t\nCausa: $rootCause\n${t.stackTrace.take(3).joinToString("\n")}", t)
        }
    }

    suspend fun setPlayerActive(email: String, isActive: Boolean): Unit = withContext(Dispatchers.IO) {
        api.setPlayerActive(SetPlayerActiveRequest(email, isActive))
        allUsers = allUsers.map { if (it.id == email) it.copy(isActive = isActive) else it }
    }

    suspend fun setPlayerVacation(email: String, isOnVacation: Boolean): Unit = withContext(Dispatchers.IO) {
        api.setPlayerVacation(SetPlayerVacationRequest(email, isOnVacation))
        allUsers = allUsers.map { if (it.id == email) it.copy(isOnVacation = isOnVacation) else it }
    }

    suspend fun login(email: String, pass: String): LoginResponse = withContext(Dispatchers.IO) {
        api.login(LoginRequest(email, pass))
    }

    suspend fun updatePassword(email: String, pass: String): Unit = withContext(Dispatchers.IO) {
        api.resetPassword(ResetPasswordRequest(email, pass))
        Unit
    }

    suspend fun updateProfile(email: String, base64Image: String): Unit = withContext(Dispatchers.IO) {
        api.updateProfile(UpdateAvatarRequest(email, base64Image))
        Unit
    }

    // ─── Admin: senha do banco de dados ─────────────────────────────────────

    suspend fun updateDbPassword(requesterEmail: String, senhaLogin: String, novaSenha: String): Unit = withContext(Dispatchers.IO) {
        val response = api.updateDbPassword(UpdateDbPasswordRequest(requesterEmail, senhaLogin, novaSenha))
        if (!response.status.equals("success", ignoreCase = true)) {
            throw Exception(response.message ?: "Falha ao atualizar a senha do banco de dados.")
        }
    }

    // Rota de emergência: não exige login, apenas a chave de administração do servidor.
    // Usada quando a senha do banco em uso pelo servidor está desalinhada da senha real,
    // a ponto do login (que também depende do banco) estar quebrado.
    suspend fun emergencyUpdateDbPassword(adminKey: String, novaSenha: String): Unit = withContext(Dispatchers.IO) {
        val response = api.emergencyUpdateDbPassword(adminKey, EmergencyUpdateDbPasswordRequest(novaSenha))
        if (!response.status.equals("success", ignoreCase = true)) {
            throw Exception(response.message ?: "Falha ao atualizar a senha do banco de dados.")
        }
    }

    // ─── Matches ──────────────────────────────────────────────────────────

    suspend fun getMatchesCountToday(): Int {
        if (allMatches.isEmpty()) getMatches()
        val today = Calendar.getInstance()
        return allMatches.count {
            val mc = Calendar.getInstance().apply { time = it.date }
            today.get(Calendar.YEAR) == mc.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == mc.get(Calendar.DAY_OF_YEAR)
        }
    }

    suspend fun getMatches(): List<Match> {
        val dtos  = getMatchDTOsFromDb()
        val users = if (allUsers.isEmpty()) getPlayers() else allUsers
        allMatchDTOs = dtos
        allMatches   = dtos.mapNotNull { it.toMatch(users) }
        return allMatches
    }

    suspend fun getMatch(matchId: String): Match? {
        if (allMatches.isEmpty()) getMatches()
        return allMatches.find { it.id == matchId }
    }

    suspend fun getRawMatchesResult(): Result<List<MatchDTO>> = safeDbCall { getMatchDTOsFromDb() }

    private suspend fun getMatchDTOsFromDb(): List<MatchDTO> = withContext(Dispatchers.IO) {
        try {
            api.getMatches().distinctBy { it.id }
        } catch (t: Throwable) {
            throw Exception("Erro ao buscar partidas: ${t.message}", t)
        }
    }

    suspend fun registerMatch(match: Match): Unit = withContext(Dispatchers.IO) {
        val dto = match.toDTO()
        api.registerMatch(dto)
        Unit
    }

    suspend fun updateMatch(match: Match): Unit = withContext(Dispatchers.IO) {
        val dto = match.toDTO()
        val id = match.id.toLongOrNull()?.toString() ?: throw Exception("ID de partida inválido: ${match.id}")
        api.updateMatch(id, dto)
        Unit
    }

    suspend fun deleteMatch(id: String, buttonName: String = "Excluir"): Unit = withContext(Dispatchers.IO) {
        api.deleteMatch(id)
        Unit
    }

    // ─── Buchos ───────────────────────────────────────────────────────────

    suspend fun getBuchosResult(): Result<List<BuchoDto>> = safeDbCall {
        withContext(Dispatchers.IO) { api.getBuchos() }
    }

    suspend fun registerDebit(debitRequest: DebitRequest): Unit = withContext(Dispatchers.IO) {
        val finalObs = if (debitRequest.wasBuchoRe == true) {
            "[BUCHO DE RÉ] ${debitRequest.obs ?: ""}".trim()
        } else {
            debitRequest.obs
        }
        api.registerDebit(debitRequest.copy(obs = finalObs))
        Unit
    }

    suspend fun deleteBucho(id: String, buttonName: String = "Excluir"): Unit = withContext(Dispatchers.IO) {
        api.deleteBucho(id)
        Unit
    }

    suspend fun markBuchoAsPaid(id: Long): Unit = withContext(Dispatchers.IO) {
        api.markBuchoAsPaid(id.toString())
        Unit
    }

    // ─── Mensalidades ─────────────────────────────────────────────────────

    suspend fun getMensalidadesResult(): Result<List<MensalidadeDto>> = safeDbCall {
        withContext(Dispatchers.IO) { api.getMensalidades() }
    }

    suspend fun createMensalidade(playerName: String, month: Int? = null, year: Int? = null): Unit =
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                if (year != null) set(Calendar.YEAR, year)
                if (month != null) set(Calendar.MONTH, month)
            }
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            api.createMensalidade(CreateMensalidadeRequest(playerName, dateStr))
            Unit
        }

    suspend fun deleteMensalidade(id: String): Unit = withContext(Dispatchers.IO) {
        api.deleteMensalidade(id)
        Unit
    }

    suspend fun markMensalidadeAsPaid(id: Long): Unit = withContext(Dispatchers.IO) {
        api.markMensalidadeAsPaid(id.toString())
        Unit
    }

    suspend fun createPlayer(
        name: String,
        email: String,
        password: String,
        avatarId: String,
        startDate: Calendar
    ): Unit = withContext(Dispatchers.IO) {
        api.createPlayer(
            CreatePlayerRequest(
                name = name.trim(),
                email = email.trim().lowercase(),
                password = password,
                avatarId = avatarId,
                startYear = startDate.get(Calendar.YEAR),
                startMonth = startDate.get(Calendar.MONTH) + 1
            )
        )
        // invalidate cache so the new player appears
        allUsers = emptyList()
    }

    // ─── Ranking (computado localmente) ───────────────────────────────────

    suspend fun getRankingResult(): Result<List<RankingDto>> = safeDbCall {
        val matches = if (allMatchDTOs.isEmpty()) getMatchDTOsFromDb() else allMatchDTOs
        computeRankingFromMatches(matches)
    }

    suspend fun calcularPontosAno(usuarioLogado: User): Int {
        return try {
            val ranking = getRankingResult().getOrThrow()
            val name = usuarioLogado.name.trim()
            ranking.find {
                it.jogador.equals(name, ignoreCase = true) ||
                it.jogador.equals(usuarioLogado.displayName.trim(), ignoreCase = true)
            }?.pontos_ano ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun computeRankingFromMatches(matches: List<MatchDTO>): List<RankingDto> {
        data class Stats(
            var partidas_dia: Int = 0, var pontos_dia: Int = 0,
            var partidas_mes: Int = 0, var pontos_mes: Int = 0,
            var partidas_ano: Int = 0, var pontos_ano: Int = 0,
            var buchos_aplicados_dia: Int = 0, var buchos_sofridos_dia: Int = 0,
            var buchos_aplicados_mes: Int = 0, var buchos_sofridos_mes: Int = 0,
            var buchos_aplicados_ano: Int = 0, var buchos_sofridos_ano: Int = 0,
            var vitorias_ano: Int = 0, var derrotas_ano: Int = 0,
            var vitorias_dia: Int = 0, var derrotas_dia: Int = 0
        )
        // Brazilian Timezone
        val tz = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = tz }
        val todayStr = format.format(java.util.Date())
        val dateParts = todayStr.split("-").mapNotNull { it.toIntOrNull() }
        if (dateParts.size < 3) return emptyList()
        val anoAtual = dateParts[0]
        val mesAtual = dateParts[1]
        val diaAtual = dateParts[2]

        val ignorados = setOf("ÍNDIO", "XAMÃ", "EX-MEMBRO", "JOSELITRO", "JOGADOR NÃO MEMBRO", "POLÍCIA FEMININA", "YAN")
        val map = mutableMapOf<String, Stats>()

        for (m in matches) {
            val dataStr = m.data?.split("T")?.firstOrNull() ?: continue
            val parts = dataStr.split("-")
            if (parts.size < 3) continue
            val anoPartida = parts[0].toIntOrNull() ?: continue
            val mesPartida = parts[1].toIntOrNull() ?: continue
            val diaPartida = parts[2].toIntOrNull() ?: continue

            val team1 = listOfNotNull(m.jogador1, m.jogador2).map { it.trim().uppercase() }
            val team2 = listOfNotNull(m.jogador3, m.jogador4).map { it.trim().uppercase() }

            val scored1 = m.scored1 ?: 0
            val scored2 = m.scored2 ?: 0
            val isTeam1Winner = scored1 > scored2

            val winnerTeam = if (isTeam1Winner) team1 else team2
            val loserTeam = if (isTeam1Winner) team2 else team1

            val isBucho = (scored1 == 0 || scored2 == 0) || m.buchore == true
            val pontos = m.pts ?: 0

            val participantes = listOfNotNull(m.jogador1, m.jogador2, m.jogador3, m.jogador4)
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() && it !in ignorados }

            participantes.forEach { jogador ->
                val s = map.getOrPut(jogador) { Stats() }
                val isWinner = winnerTeam.contains(jogador)

                if (anoPartida == anoAtual) {
                    s.partidas_ano++
                    if (isWinner) {
                        s.pontos_ano += pontos
                        s.vitorias_ano++
                    } else {
                        s.derrotas_ano++
                    }
                    if (isBucho) {
                        if (isWinner) s.buchos_aplicados_ano++ else s.buchos_sofridos_ano++
                    }

                    if (mesPartida == mesAtual) {
                        s.partidas_mes++
                        if (isWinner) {
                            s.pontos_mes += pontos
                        }
                        if (isBucho) {
                            if (isWinner) s.buchos_aplicados_mes++ else s.buchos_sofridos_mes++
                        }

                        if (diaPartida == diaAtual) {
                            s.partidas_dia++
                            if (isWinner) {
                                s.pontos_dia += pontos
                                s.vitorias_dia++
                            } else {
                                s.derrotas_dia++
                            }
                            if (isBucho) {
                                if (isWinner) s.buchos_aplicados_dia++ else s.buchos_sofridos_dia++
                            }
                        }
                    }
                }
            }
        }

        return map.map { (name, s) ->
            RankingDto(
                jogador = name,
                partidas_dia = s.partidas_dia,
                pontos_dia = s.pontos_dia,
                partidas_mes = s.partidas_mes,
                pontos_mes = s.pontos_mes,
                partidas_ano = s.partidas_ano,
                pontos_ano = s.pontos_ano,
                buchos_aplicados_dia = s.buchos_aplicados_dia,
                buchos_sofridos_dia = s.buchos_sofridos_dia,
                buchos_aplicados_mes = s.buchos_aplicados_mes,
                buchos_sofridos_mes = s.buchos_sofridos_mes,
                buchos_aplicados_ano = s.buchos_aplicados_ano,
                buchos_sofridos_ano = s.buchos_sofridos_ano,
                vitorias_ano = s.vitorias_ano,
                derrotas_ano = s.derrotas_ano,
                vitorias_dia = s.vitorias_dia,
                derrotas_dia = s.derrotas_dia
            )
        }.sortedByDescending { it.pontos_ano }
    }

    suspend fun getChampionCelebration(year: Int, month: Int): ChampionCelebration? = withContext(Dispatchers.IO) {
        try {
            val matches = if (allMatchDTOs.isEmpty()) getMatchDTOsFromDb() else allMatchDTOs
            val players = getPlayers()
            val ignorados = setOf("ÍNDIO", "XAMÃ", "EX-MEMBRO", "JOSELITRO", "JOGADOR NÃO MEMBRO", "POLÍCIA FEMININA", "YAN")

            val pointsMap = mutableMapOf<String, Int>()
            for (m in matches) {
                val dataStr = m.data?.split("T")?.firstOrNull() ?: continue
                val parts = dataStr.split("-")
                if (parts.size < 3) continue
                val anoPartida = parts[0].toIntOrNull() ?: continue
                val mesPartida = parts[1].toIntOrNull() ?: continue

                if (anoPartida == year && mesPartida == month) {
                    val vencedores = m.dupla_vencedora
                        ?.split("&", "/")
                        ?.map { it.trim().uppercase() }
                        ?: emptyList()
                    val pontos = m.pts ?: 0

                    vencedores.forEach { jogador ->
                        val name = jogador.trim().uppercase()
                        if (name !in ignorados && name.isNotBlank()) {
                            pointsMap[name] = (pointsMap[name] ?: 0) + pontos
                        }
                    }
                }
            }

            val entry = pointsMap.maxByOrNull { it.value }
            if (entry != null) {
                val champUser = players.find { it.name.trim().uppercase() == entry.key || it.displayName.trim().uppercase() == entry.key }
                if (champUser != null) {
                    val monthNamesPt = listOf(
                        "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
                    )
                    val monthName = monthNamesPt.getOrElse(month) { "" }
                    ChampionCelebration(champUser, entry.value, monthName)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ─── Finance ──────────────────────────────────────────────────────────

    suspend fun getTotalDebt(userId: String): Double {
        val users = if (allUsers.isEmpty()) getPlayers() else allUsers
        var total = 0.0
        getBuchosResult().onSuccess { buchos ->
            total += buchos.mapNotNull { it.toFinancialEntry(users) }
                .filter { it.userId == userId && it.status == FinancialEntryStatus.PENDING }
                .sumOf { it.amount }
        }
        getMensalidadesResult().onSuccess { mensalidades ->
            total += mensalidades.mapNotNull { it.toFinancialEntry(users) }
                .filter { it.userId == userId && it.status == FinancialEntryStatus.PENDING }
                .sumOf { it.amount }
        }
        return total
    }

    suspend fun uploadComprovante(request: ComprovanteRequest): Unit = withContext(Dispatchers.IO) {
        RetrofitClient.instance.uploadComprovante(request)
    }

    suspend fun triggerTaxasExtras() { /* migrado para banco direto — sem operação pendente */ }

    // ─── Helpers e extensões ──────────────────────────────────────────────

    fun parseAnyDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        for (fmt in formats) {
            try {
                return SimpleDateFormat(fmt, Locale.getDefault()).apply { isLenient = false }.parse(dateStr)
            } catch (_: Exception) {}
        }
        return null
    }

    fun BuchoDto.toFinancialEntry(users: List<User>): FinancialEntry? {
        if (this.id == null) return null
        val cleanName = this.jogador?.trim() ?: return null
        val parts = cleanName.split("/").map { it.trim() }
        var user = users.find { u -> parts.any { p -> p.equals(u.name, ignoreCase = true) } }
        if (user == null) user = users.find { u -> parts.any { p -> p.equals(u.displayName, ignoreCase = true) } }
        val userId = user?.id ?: return null
        val parsedDate = parseAnyDate(this.data) ?: Date()
        val isTaxaExtra = this.obs?.contains("Taxa extra", ignoreCase = true) == true
        return FinancialEntry(
            id = UUID.randomUUID().toString(),
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
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(monthName.substring(0, 10))
                    if (date != null) {
                        val cal = Calendar.getInstance().apply { time = date }
                        monthIndex = cal.get(Calendar.MONTH)
                        year = cal.get(Calendar.YEAR)
                        monthName = monthIndexToName[monthIndex] ?: "N/A"
                    }
                }
            } catch (_: Exception) {}
        }
        if (monthIndex == null) return null
        val dueDate = Calendar.getInstance().apply { set(year, monthIndex, 1) }
        val ref = "$monthName/$year"
        return FinancialEntry(
            id = UUID.randomUUID().toString(),
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
        if (this.email.isNullOrBlank() || this.jogador.isNullOrBlank()) return null
        return User(
            id = this.email.trim(),
            name = this.jogador.trim(),
            displayName = this.jogador.trim(),
            photoUrl = this.avatar ?: "",
            clubId = "c1",
            isMember = true,
            password = this.senha?.trim(),
            isActive = (this.ativo ?: 1) == 1,
            isOnVacation = (this.ferias ?: 0) == 1
        )
    }

    private fun MatchDTO.toMatch(users: List<User>): Match? {
        val t1p1 = users.find { it.name.equals(this.jogador1?.trim(), ignoreCase = true) } ?: return null
        val t1p2 = users.find { it.name.equals(this.jogador2?.trim(), ignoreCase = true) } ?: return null
        val t2p1 = users.find { it.name.equals(this.jogador3?.trim(), ignoreCase = true) } ?: return null
        val t2p2 = users.find { it.name.equals(this.jogador4?.trim(), ignoreCase = true) } ?: return null
        val date = parseAnyDate(this.data) ?: Date()
        val registeredBy = if (!this.cadastrado_por.isNullOrBlank()) {
            users.find { it.name.equals(this.cadastrado_por.trim(), ignoreCase = true) }
                ?: User("ext", this.cadastrado_por.trim(), this.cadastrado_por.trim(), "", "c1")
        } else {
            User("0", "N/A", "N/A", "", "c1")
        }
        return Match(
            id = this.id?.toString() ?: "match_${this.data}_${this.jogador1}".hashCode().toString(),
            date = date,
            team1Player1 = t1p1, team1Player2 = t1p2, team2Player1 = t2p1, team2Player2 = t2p2,
            score1 = this.scored1 ?: 0, score2 = this.scored2 ?: 0,
            wasBuchoRe = this.buchore ?: false, registeredBy = registeredBy, pts = this.pts ?: 0
        )
    }

    fun Match.toDTO(buttonName: String? = null): MatchDTO {
        val winnerScore = maxOf(this.score1, this.score2)
        val loserScore  = minOf(this.score1, this.score2)
        val points = when {
            this.wasBuchoRe              -> winnerScore + 2
            winnerScore == 6 && loserScore == 0 -> winnerScore + 1
            else                         -> abs(this.score1 - this.score2)
        }
        return MatchDTO(
            id = this.id.toLongOrNull(),
            data = dateFormat.format(this.date),
            jogador1 = this.team1Player1.name, jogador2 = this.team1Player2.name,
            jogador3 = this.team2Player1.name, jogador4 = this.team2Player2.name,
            scored1 = this.score1, scored2 = this.score2,
            buchore = this.wasBuchoRe, pts = points,
            dupla_vencedora = if (score1 > score2) "${team1Player1.name}/${team1Player2.name}"
                              else "${team2Player1.name}/${team2Player2.name}",
            cadastrado_por = this.registeredBy.name,
            buttonName = buttonName
        )
    }

    private fun String.normalize(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase()

    suspend fun getActiveMatches(): List<ActiveMatch> = withContext(Dispatchers.IO) {
        try {
            api.getActiveMatches().map { it.toActiveMatch() }
        } catch (t: Throwable) {
            t.printStackTrace()
            emptyList()
        }
    }

    suspend fun startActiveMatch(activeMatch: ActiveMatch): Boolean = withContext(Dispatchers.IO) {
        try {
            api.startActiveMatch(activeMatch.toDto())
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    suspend fun deleteActiveMatch(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            api.deleteActiveMatch(id)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    suspend fun getActiveMatchForUser(username: String): ActiveMatch? = withContext(Dispatchers.IO) {
        try {
            api.getActiveMatches(jogador = username).firstOrNull()?.toActiveMatch()
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun ActiveMatchDto.toActiveMatch(): ActiveMatch = ActiveMatch(
        id = this.id,
        player1 = this.jogador1 ?: "",
        player2 = this.jogador2 ?: "",
        player3 = this.jogador3 ?: "",
        player4 = this.jogador4 ?: "",
        cadastrador = this.cadastrador ?: "",
        createdAt = parseAnyDate(this.dataCriacao) ?: Date()
    )

    private fun ActiveMatch.toDto(): ActiveMatchDto = ActiveMatchDto(
        id = this.id,
        jogador1 = this.player1,
        jogador2 = this.player2,
        jogador3 = this.player3,
        jogador4 = this.player4,
        cadastrador = this.cadastrador,
        dataCriacao = null
    )
}
