package com.marcioarruda.clubedodomino.data

import java.util.Date

// 1. Club
data class Club(
    val id: String,
    val name: String
)

// 2. User
data class User(
    val id: String,
    val name: String,
    val displayName: String,
    val photoUrl: String,
    val clubId: String,
    val isMember: Boolean = false,
    val pixarAvatarUrl: String? = null,
    val password: String? = null
)

// 3. Match
data class Match(
    val id: String,
    val date: Date = Date(),
    val team1Player1: User,
    val team1Player2: User,
    val team2Player1: User,
    val team2Player2: User,
    val score1: Int,
    val score2: Int,
    val wasBuchoRe: Boolean = false,
    val registeredBy: User,
    val pts: Int = 0 // Added pts field
)

// 4. FinancialEntry
enum class FinancialEntryType { MONTHLY_FEE, BUCHO, BUCHO_RE, EXTRA_TAX }
enum class FinancialEntryStatus { PENDING, PAID, UNDER_REVIEW }

data class FinancialEntry(
    val id: String,
    val userId: String,
    val type: FinancialEntryType,
    val amount: Double,
    val status: FinancialEntryStatus = FinancialEntryStatus.PENDING,
    val dueDate: Date,
    val description: String = "",
    val winningPair: String? = null,
    val losingPair: String? = null,
    val originalRemoteId: Long? = null, // For Buchos
    val originalReference: String? = null // For Mensalidades (Data/Ref)
)

data class RankingPlayer(
    val playerName: String,
    val photoUrl: String = "", // Optional for UI
    val dailyPoints: Int = 0,
    val dailyMatches: Int = 0,
    val monthlyPoints: Int = 0,
    val monthlyMatches: Int = 0,
    val yearlyPoints: Int = 0,
    val yearlyMatches: Int = 0,
    val dailyBuchosApplied: Int = 0,
    val dailyBuchosReceived: Int = 0,
    val monthlyBuchosApplied: Int = 0,
    val monthlyBuchosReceived: Int = 0,
    val yearlyBuchosApplied: Int = 0,
    val yearlyBuchosReceived: Int = 0,
    val yearlyWins: Int = 0,
    val yearlyLosses: Int = 0
)

data class ChampionCelebration(
    val player: User,
    val points: Int,
    val monthName: String
)

data class ActiveMatch(
    val id: String,
    val player1: String,
    val player2: String,
    val player3: String,
    val player4: String,
    val cadastrador: String,
    val createdAt: Date = Date()
)
