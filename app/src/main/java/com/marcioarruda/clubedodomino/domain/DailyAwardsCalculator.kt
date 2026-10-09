package com.marcioarruda.clubedodomino.domain

import com.marcioarruda.clubedodomino.data.BestPlayer
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.network.RankingDto

data class DailyAwards(
    val best: List<BestPlayer>,
    val worst: List<BestPlayer>
)

// Calcula o Craque e o Piorzinho do dia a partir do ranking bruto do servidor. Extraído de
// DashboardViewModel para ser compartilhado com qualquer outra tela que precise do mesmo
// destaque do dia (ex.: popup na tela de partida em andamento), sem duplicar a regra.
//
// Critério: total de pontos ganhos no dia. Desempate, em ordem: mais vitórias -> mais buchos
// aplicados -> mais partidas jogadas. Só concorre quem jogou pelo menos 5 partidas no dia.
// Piorzinho usa a mesma regra invertida, excluindo quem já foi eleito craque (evita a mesma
// pessoa aparecer nos dois cards quando há poucos elegíveis ou empate total).
fun calculateDailyAwards(ranking: List<RankingDto>, allPlayers: List<User>): DailyAwards {
    val eligibleToday = ranking.filter { it.partidas_dia >= 5 && !it.jogador.contains("NÃO MEMBRO", ignoreCase = true) }
    if (eligibleToday.isEmpty()) return DailyAwards(emptyList(), emptyList())

    fun isSameRank(a: RankingDto, b: RankingDto) =
        a.pontos_dia == b.pontos_dia &&
            a.vitorias_dia == b.vitorias_dia &&
            a.buchos_aplicados_dia == b.buchos_aplicados_dia &&
            a.partidas_dia == b.partidas_dia

    fun toBestPlayer(r: RankingDto): BestPlayer? {
        val playerUser = allPlayers.find { u ->
            u.name.equals(r.jogador.trim(), ignoreCase = true) || u.displayName.equals(r.jogador.trim(), ignoreCase = true)
        }
        return playerUser?.let { BestPlayer(it, r.pontos_dia, r.vitorias_dia, r.partidas_dia, r.saldo_dia) }
    }

    val bestRanked = eligibleToday.sortedWith(
        compareByDescending<RankingDto> { it.pontos_dia }
            .thenByDescending { it.vitorias_dia }
            .thenByDescending { it.buchos_aplicados_dia }
            .thenByDescending { it.partidas_dia }
    )
    val bestTop = bestRanked.first()
    val bestTied = bestRanked.filter { isSameRank(it, bestTop) }
    val best = bestTied.mapNotNull(::toBestPlayer)

    val worstCandidates = eligibleToday.filterNot { candidate -> bestTied.any { it.jogador == candidate.jogador } }
    val worst = if (worstCandidates.isEmpty()) {
        emptyList()
    } else {
        val worstRanked = worstCandidates.sortedWith(
            compareBy<RankingDto> { it.pontos_dia }
                .thenBy { it.vitorias_dia }
                .thenBy { it.buchos_aplicados_dia }
                .thenBy { it.partidas_dia }
        )
        val worstTop = worstRanked.first()
        val worstTied = worstRanked.filter { isSameRank(it, worstTop) }
        worstTied.mapNotNull(::toBestPlayer)
    }

    return DailyAwards(best, worst)
}
