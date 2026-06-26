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
            throw Exception("Erro ao buscar jogadores: ${t.message}", t)
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
            if (t is Exception) throw t
            throw Exception("Erro ao autenticar: ${t.message}", t)
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
                    "SELECT id, data, jogador1, jogador2, jogador3, jogador4, " +
                    "scored1, scored2, buchore, pts, dupla_vencedora, cadastrado_por " +
                    "FROM partidas ORDER BY id DESC"
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
                        scored1        = rs.getInt("scored1"),
                        scored2        = rs.getInt("scored2"),
                        buchore        = rs.getBoolean("buchore"),
                        pts            = rs.getInt("pts"),
                        dupla_vencedora = rs.getString("dupla_vencedora"),
                        cadastrado_por = rs.getString("cadastrado_por")
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
                "scored1, scored2, buchore, pts, dupla_vencedora, cadastrado_por) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            ps.setString(1, dto.data)
            ps.setString(2, dto.jogador1)
            ps.setString(3, dto.jogador2)
            ps.setString(4, dto.jogador3)
            ps.setString(5, dto.jogador4)
            ps.setInt(6, dto.scored1 ?: 0)
            ps.setInt(7, dto.scored2 ?: 0)
            ps.setBoolean(8, dto.buchore ?: false)
            ps.setInt(9, dto.pts ?: 0)
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
                "scored1=?, scored2=?, buchore=?, pts=?, dupla_vencedora=?, cadastrado_por=? " +
                "WHERE id=?"
            )
            ps.setString(1, dto.data)
            ps.setString(2, dto.jogador1)
            ps.setString(3, dto.jogador2)
            ps.setString(4, dto.jogador3)
            ps.setString(5, dto.jogador4)
            ps.setInt(6, dto.scored1 ?: 0)
            ps.setInt(7, dto.scored2 ?: 0)
            ps.setBoolean(8, dto.buchore ?: false)
            ps.setInt(9, dto.pts ?: 0)
            ps.setString(10, dto.dupla_vencedora)
            ps.setString(11, dto.cadastrado_por)
            ps.setLong(12, match.id.toLongOrNull() ?: throw Exception("ID de partida inválido: ${match.id}"))
            ps.executeUpdate()
        }
    }

    suspend fun deleteMatch(id: String, buttonName: String = "Excluir"): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("DELETE FROM partidas WHERE id = ?")
            ps.setLong(1, id.toLong())
            ps.executeUpdate()
        }
    }

    // ─── Buchos ───────────────────────────────────────────────────────────

    suspend fun getBuchosResult(): Result<List<BuchoDto>> = safeDbCall {
        withContext(Dispatchers.IO) {
            MySqlDatabase.connect().use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT id, data, jogador, valor, pago, placar, dupla_vencedora, " +
                    "dupla_perdedora, obs, cadastrado_por, buchore FROM buchos"
                ).executeQuery()
                val list = mutableListOf<BuchoDto>()
                while (rs.next()) {
                    list.add(BuchoDto(
                        id              = rs.getLong("id"),
                        data            = rs.getString("data"),
                        jogador         = rs.getString("jogador"),
                        valor           = rs.getDouble("valor"),
                        pago            = rs.getBoolean("pago"),
                        placar          = rs.getString("placar"),
                        dupla_vencedora = rs.getString("dupla_vencedora"),
                        dupla_perdedora = rs.getString("dupla_perdedora"),
                        obs             = rs.getString("obs"),
                        cadastrado_por  = rs.getString("cadastrado_por"),
                        buchore         = rs.getBoolean("buchore")
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
                "dupla_perdedora, obs, cadastrado_por, buchore) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            ps.setString(1, debitRequest.data)
            ps.setString(2, debitRequest.jogador)
            ps.setDouble(3, debitRequest.valor)
            ps.setBoolean(4, debitRequest.pago)
            ps.setString(5, debitRequest.placar)
            ps.setString(6, debitRequest.dupla_vencedora)
            ps.setString(7, debitRequest.dupla_perdedora)
            ps.setString(8, debitRequest.obs)
            ps.setString(9, debitRequest.cadastrado_por)
            ps.setBoolean(10, debitRequest.wasBuchoRe ?: false)
            ps.executeUpdate()
        }
    }

    suspend fun deleteBucho(id: String, buttonName: String = "Excluir"): Unit = withContext(Dispatchers.IO) {
        MySqlDatabase.connect().use { conn ->
            val ps = conn.prepareStatement("DELETE FROM buchos WHERE id = ?")
            ps.setLong(1, id.toLong())
            ps.executeUpdate()
        }
    }

    // ─── Mensalidades ─────────────────────────────────────────────────────

    suspend fun getMensalidadesResult(): Result<List<MensalidadeDto>> = safeDbCall {
        withContext(Dispatchers.IO) {
            MySqlDatabase.connect().use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT id, mensalidade, jogador, pago, ano FROM mensalidades"
                ).executeQuery()
                val list = mutableListOf<MensalidadeDto>()
                while (rs.next()) {
                    list.add(MensalidadeDto(
                        id          = rs.getLong("id"),
                        mensalidade = rs.getString("mensalidade"),
                        jogador     = rs.getString("jogador"),
                        pago        = rs.getBoolean("pago"),
                        ano         = rs.getInt("ano")
                    ))
                }
                list
            }
        }
    }

    suspend fun createMensalidade(playerName: String, month: Int? = null, year: Int? = null): Unit =
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            if (year != null) cal.set(Calendar.YEAR, year)
            if (month != null) cal.set(Calendar.MONTH, month)
            val monthName = monthIndexToName[cal.get(Calendar.MONTH)] ?: "Janeiro"
            val ano = cal.get(Calendar.YEAR)
            MySqlDatabase.connect().use { conn ->
                val ps = conn.prepareStatement(
                    "INSERT INTO mensalidades (mensalidade, jogador, pago, ano) VALUES (?, ?, FALSE, ?)"
                )
                ps.setString(1, monthName)
                ps.setString(2, playerName)
                ps.setInt(3, ano)
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
            var partidas_ano: Int = 0, var pontos_ano: Int = 0
        )
        val today = Calendar.getInstance()
        val map   = mutableMapOf<String, Stats>()

        for (m in matches) {
            val date = parseAnyDate(m.data) ?: continue
            val mc   = Calendar.getInstance().apply { time = date }
            if (mc.get(Calendar.YEAR) != today.get(Calendar.YEAR)) continue

            val isToday = mc.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            val isMonth = mc.get(Calendar.MONTH) == today.get(Calendar.MONTH)
            val pts     = m.pts ?: 0
            val venc    = m.dupla_vencedora ?: ""

            listOfNotNull(m.jogador1, m.jogador2, m.jogador3, m.jogador4).forEach { raw ->
                val name = raw.trim().ifBlank { return@forEach }
                val isWinner = venc.contains(name, ignoreCase = true)
                val p = if (isWinner) pts else -pts
                val s = map.getOrPut(name) { Stats() }
                s.partidas_ano++; s.pontos_ano += p
                if (isMonth) { s.partidas_mes++; s.pontos_mes += p }
                if (isToday) { s.partidas_dia++; s.pontos_dia += p }
            }
        }
        return map.map { (name, s) ->
            RankingDto(name, s.partidas_dia, s.pontos_dia, s.partidas_mes, s.pontos_mes, s.partidas_ano, s.pontos_ano)
        }.sortedByDescending { it.pontos_mes }
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
        MySqlDatabase.connect().use { conn ->
            if (request.buchoIds.isNotEmpty()) {
                val ph = request.buchoIds.joinToString(",") { "?" }
                val ps = conn.prepareStatement("UPDATE buchos SET pago = TRUE WHERE id IN ($ph)")
                request.buchoIds.forEachIndexed { i, id -> ps.setLong(i + 1, id) }
                ps.executeUpdate()
            }
            if (request.mensalidadeIds.isNotEmpty()) {
                val ids = request.mensalidadeIds.mapNotNull { it.toLongOrNull() }
                if (ids.isNotEmpty()) {
                    val ph = ids.joinToString(",") { "?" }
                    val ps = conn.prepareStatement("UPDATE mensalidades SET pago = TRUE WHERE id IN ($ph)")
                    ids.forEachIndexed { i, id -> ps.setLong(i + 1, id) }
                    ps.executeUpdate()
                }
            }
        }
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
