package com.marcioarruda.clubedodomino.data

data class BestPlayer(
    val player: User,
    val points: Int,
    val wins: Int = 0,
    val matches: Int = 0
) {
    val winRate: Double get() = if (matches > 0) wins.toDouble() / matches else 0.0
}
