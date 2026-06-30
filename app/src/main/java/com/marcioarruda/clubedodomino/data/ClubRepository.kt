package com.marcioarruda.clubedodomino.data

import com.marcioarruda.clubedodomino.data.database.MySqlDatabase
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
            MySqlDatabase.connect().use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT jogador, avatar, email, senha FROM jogadores"
                ).executeQuery()
                val users = mutableListOf<User>()
                while (rs.next()) {
                    val email = rs.getString("email") ?: continue
                    val name  = rs.getString("jogador") ?: continue
                    users.add(User(
                        id          = email.trim(),
                        name        = name.trim(),
                        displayName = name.trim(),
                        photoUrl    = rs.getString("avatar") ?: "",
                        clubId      = "c1",
                        isMember    = true,
                        password    = rs.getString("senha")?.trim()
                    ))
                }
                if (users.none { it.name.contains("NÃO MEMBRO", ignoreCase = true) }) {
                    users.add(User("7", "JOGADOR NÃO MEMBRO", "NÃO MEMBRO", "", "c1", false))
                }
                allUsers = users
                users
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            val rootCause = generateSequence(t) { it.cause }.lastOrNull() ?: t
            throw Exception("Erro ao buscar jogadores: $t\nCausa: $rootCause\n${t.stackTrace.take(3).joinToString("\n")}", t)
        }
    }

    suspend fun login(email: String, pass: String): LoginResponse = withContext(Dispatchers.IO) {
        try {
            MySqlDatabase.connect().use { conn ->
                val ps = conn.prepareStatement(
                    "SELECT email FROM jogadores WHERE email = ? AND senha = ?"
                )
                ps.setString(1, email)
                ps.setString(2, pass)
                val rs = ps.executeQuery()
                if (rs.next()) LoginResponse("Login bem sucedido")
                else throw Exception("Email ou senha inválidos")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            if (t is Exception && t.message == "Email ou senha inválidos") throw t
            val rootCause = generateSequence(t) { it.cause }.lastOrNull() ?: t
            throw Exception("Erro ao autenticar: $t\nCausa: $rootCause\n${t.stackTrace.take(3).joinToString("\n")}", t)
        }
    }

    suspend fun updatePassword(email: String, pass: String): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("UPDATE jogadores SET senha = ? WHERE email = ?")
            ps.setString(1, pass)
            ps.setString(2, email)
            ps.executeUpdate()
        }
    }

    suspend fun updateProfile(email: String, base64Image: String): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("UPDATE jogadores SET avatar = ? WHERE email = ?")
            ps.setString(1, base64Image)
            ps.setString(2, email)
            ps.executeUpdate()
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
            MySqlDatabase.connect().use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT id_tabela as id, data, jogador1, jogador2, jogador3, jogador4, " +
                    "scored1, scored2, buchore, pts, dupla_vencedora, cadastrador " +
                    "FROM partidas ORDER BY id_tabela DESC"
                ).executeQuery()
                val list = mutableListOf<MatchDTO>()
                while (rs.next()) {
                    list.add(MatchDTO(
                        id             = rs.getLong("id"),
                        data           = rs.getString("data"),
                        jogador1       = rs.getString("jogador1"),
                        jogador2       = rs.getString("jogador2"),
                        jogador3       = rs.getString("jogador3"),
                        jogador4       = rs.getString("jogador4"),
                        scored1        = rs.getString("scored1")?.toIntOrNull() ?: 0,
                        scored2        = rs.getString("scored2")?.toIntOrNull() ?: 0,
                        buchore        = rs.getString("buchore")?.toBoolean() ?: (rs.getString("buchore") == "true" || rs.getString("buchore") == "1"),
                        pts            = rs.getString("pts")?.toIntOrNull() ?: 0,
                        dupla_vencedora = rs.getString("dupla_vencedora"),
                        cadastrado_por = rs.getString("cadastrador")
                    ))
                }
                list.distinctBy { it.id }
            }
        } catch (t: Throwable) {
            throw Exception("Erro ao buscar partidas: ${t.message}", t)
        }
    }

    suspend fun registerMatch(match: Match): Unit = withContext(Dispatchers.IO) {
        val dto = match.toDTO()
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement(
                "INSERT INTO partidas (data, jogador1, jogador2, jogador3, jogador4, " +
                "scored1, scored2, buchore, pts, dupla_vencedora, cadastrador) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            ps.setString(1, dto.data)
            ps.setString(2, dto.jogador1)
            ps.setString(3, dto.jogador2)
            ps.setString(4, dto.jogador3)
            ps.setString(5, dto.jogador4)
            ps.setString(6, (dto.scored1 ?: 0).toString())
            ps.setString(7, (dto.scored2 ?: 0).toString())
            ps.setString(8, (dto.buchore ?: false).toString())
            ps.setString(9, (dto.pts ?: 0).toString())
            ps.setString(10, dto.dupla_vencedora)
            ps.setString(11, dto.cadastrado_por)
            ps.executeUpdate()
        }
    }

    suspend fun updateMatch(match: Match): Unit = withContext(Dispatchers.IO) {
        val dto = match.toDTO()
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement(
                "UPDATE partidas SET data=?, jogador1=?, jogador2=?, jogador3=?, jogador4=?, " +
                "scored1=?, scored2=?, buchore=?, pts=?, dupla_vencedora=?, cadastrador=? " +
                "WHERE id_tabela=?"
            )
            ps.setString(1, dto.data)
            ps.setString(2, dto.jogador1)
            ps.setString(3, dto.jogador2)
            ps.setString(4, dto.jogador3)
            ps.setString(5, dto.jogador4)
            ps.setString(6, (dto.scored1 ?: 0).toString())
            ps.setString(7, (dto.scored2 ?: 0).toString())
            ps.setString(8, (dto.buchore ?: false).toString())
            ps.setString(9, (dto.pts ?: 0).toString())
            ps.setString(10, dto.dupla_vencedora)
            ps.setString(11, dto.cadastrado_por)
            ps.setLong(12, match.id.toLongOrNull() ?: throw Exception("ID de partida inválido: ${match.id}"))
            ps.executeUpdate()
        }
    }

    suspend fun deleteMatch(id: String, buttonName: String = "Excluir"): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("DELETE FROM partidas WHERE id_tabela = ?")
            ps.setLong(1, id.toLong())
            ps.executeUpdate()
        }
    }

    // ─── Buchos ───────────────────────────────────────────────────────────

    suspend fun getBuchosResult(): Result<List<BuchoDto>> = safeDbCall {
        withContext(Dispatchers.IO) {
            MySqlDatabase.connect().use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT id_tabela as id, data, jogador, valor, pago, placar, dupla_vencedora, " +
                    "dupla_perdedora, obs FROM buchos"
                ).executeQuery()
                val list = mutableListOf<BuchoDto>()
                while (rs.next()) {
                    list.add(BuchoDto(
                        id              = rs.getLong("id"),
                        data            = rs.getString("data"),
                        jogador         = rs.getString("jogador"),
                        valor           = rs.getString("valor")?.toDoubleOrNull() ?: 0.0,
                        pago            = rs.getString("pago")?.toBoolean() ?: (rs.getString("pago") == "true" || rs.getString("pago") == "1"),
                        placar          = rs.getString("placar"),
                        dupla_vencedora = rs.getString("dupla_vencedora"),
                        dupla_perdedora = rs.getString("dupla_perdedora"),
                        obs             = rs.getString("obs"),
                        cadastrado_por  = null,
                        buchore         = null
                    ))
                }
                list
            }
        }
    }

    suspend fun registerDebit(debitRequest: DebitRequest): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement(
                "INSERT INTO buchos (data, jogador, valor, pago, placar, dupla_vencedora, " +
                "dupla_perdedora, obs) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )
            ps.setString(1, debitRequest.data)
            ps.setString(2, debitRequest.jogador)
            ps.setString(3, debitRequest.valor.toString())
            ps.setString(4, debitRequest.pago.toString())
            ps.setString(5, debitRequest.placar)
            ps.setString(6, debitRequest.dupla_vencedora)
            ps.setString(7, debitRequest.dupla_perdedora)
            val finalObs = if (debitRequest.wasBuchoRe == true) {
                "[BUCHO DE RÉ] ${debitRequest.obs ?: ""}".trim()
            } else {
                debitRequest.obs
            }
            ps.setString(8, finalObs)
            ps.executeUpdate()
        }
    }

    suspend fun deleteBucho(id: String, buttonName: String = "Excluir"): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("DELETE FROM buchos WHERE id_tabela = ?")
            ps.setLong(1, id.toLong())
            ps.executeUpdate()
        }
    }

    suspend fun markBuchoAsPaid(id: Long): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("UPDATE buchos SET pago = 'true' WHERE id_tabela = ?")
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    // ─── Mensalidades ─────────────────────────────────────────────────────

    suspend fun getMensalidadesResult(): Result<List<MensalidadeDto>> = safeDbCall {
        withContext(Dispatchers.IO) {
            MySqlDatabase.connect().use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT id_tabela as id, mensalidade, jogador, pago FROM mensalidades"
                ).executeQuery()
                val list = mutableListOf<MensalidadeDto>()
                while (rs.next()) {
                    list.add(MensalidadeDto(
                        id          = rs.getLong("id"),
                        mensalidade = rs.getString("mensalidade"),
                        jogador     = rs.getString("jogador"),
                        pago        = rs.getString("pago")?.toBoolean() ?: (rs.getString("pago") == "true" || rs.getString("pago") == "1"),
                        ano         = null
                    ))
                }
                list
            }
        }
    }

    suspend fun createMensalidade(playerName: String, month: Int? = null, year: Int? = null): Unit =
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 10)
                if (year != null) set(Calendar.YEAR, year)
                if (month != null) set(Calendar.MONTH, month)
            }
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            MySqlDatabase.connect().use { conn ->
                val ps = conn.prepareStatement(
                    "INSERT INTO mensalidades (mensalidade, jogador, pago) VALUES (?, ?, 'false')"
                )
                ps.setString(1, dateStr)
                ps.setString(2, playerName)
                ps.executeUpdate()
            }
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
            var vitorias_ano: Int = 0, var derrotas_ano: Int = 0
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
                derrotas_ano = s.derrotas_ano
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
        val dueDate = Calendar.getInstance().apply { set(year, monthIndex, 10) }
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
            id = this.email.trim(), name = this.jogador.trim(), displayName = this.jogador.trim(),
            photoUrl = this.avatar ?: "", clubId = "c1", isMember = true, password = this.senha?.trim()
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
}
