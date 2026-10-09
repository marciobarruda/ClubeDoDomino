package com.marcioarruda.clubedodomino.data

data class BestPlayer(
    val player: User,
    val points: Int,
    val wins: Int = 0,
    val matches: Int = 0,
    // Saldo de pontos do dia (ganhos nas vitórias menos perdidos nas derrotas) — é a métrica
    // real usada para eleger Craque/Piorzinho do dia (saldo médio por partida), diferente de
    // `points`, que soma só os pontos das partidas vencidas e por si só não explica o título.
    val balance: Int = 0
) {
    val winRate: Double get() = if (matches > 0) wins.toDouble() / matches else 0.0
    val avgBalance: Double get() = if (matches > 0) balance.toDouble() / matches else 0.0
}
