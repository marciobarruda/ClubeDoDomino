package com.marcioarruda.clubedodomino.ui.ranking

import com.marcioarruda.clubedodomino.data.User

data class PlayerRanking(
    val position: Int,
    val player: User,
    val points: Int,
    val yearlyPoints: Int,
    val matchesPlayed: Int,
    val winPercentage: Int,
    val scoreBalance: Int,
    val wins: Int
)

sealed class RankingScreenUiState {
    object Loading : RankingScreenUiState()
    data class Success(val playerRankings: List<PlayerRanking>) : RankingScreenUiState()
    data class Error(val message: String) : RankingScreenUiState()
}
