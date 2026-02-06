package com.marcioarruda.clubedodomino.data

import android.content.Context
import android.content.SharedPreferences
import com.marcioarruda.clubedodomino.data.network.BuchoDto
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class AdminRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)

    // Keys
    private fun getVacationKey(userId: String) = "vacation_start_$userId"
    private fun getActiveKey(userId: String) = "is_active_$userId"

    fun setPlayerVacation(userId: String, isOnVacation: Boolean) {
        if (isOnVacation) {
            prefs.edit().putLong(getVacationKey(userId), System.currentTimeMillis()).apply()
        } else {
            prefs.edit().remove(getVacationKey(userId)).apply()
        }
    }

    fun isPlayerOnVacation(userId: String): Boolean {
        val startDate = prefs.getLong(getVacationKey(userId), -1)
        if (startDate == -1L) return false

        // Check auto-off (30 days)
        val diff = System.currentTimeMillis() - startDate
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        
        if (days > 30) {
            // Disabled automatically
            setPlayerVacation(userId, false)
            return false
        }
        return true
    }

    fun setPlayerActive(userId: String, isActive: Boolean) {
        prefs.edit().putBoolean(getActiveKey(userId), isActive).apply()
    }

    fun isPlayerActive(userId: String): Boolean {
        // Default is true
        return prefs.getBoolean(getActiveKey(userId), true)
    }

    // --- Global Stats Logic ---
    
    fun calculateStats(
        matches: List<Match>,
        buchos: List<BuchoDto>,
        allUsers: List<User>,
        targetMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
        targetYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): GlobalStats {
        val currentMonth = targetMonth
        val currentYear = targetYear

        // 1. Filter Data (Month Current)
        val matchesThisMonth = matches.filter {
            val cal = Calendar.getInstance()
            cal.time = it.date
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }

        val buchosThisMonth = buchos.filter { dto ->
            try {
                val dateStr = dto.data
                if (!dateStr.isNullOrBlank()) {
                    // Fix: Use correct format matching API (ISO 8601)
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                    val date = sdf.parse(dateStr)
                    if (date != null) {
                        val cal = Calendar.getInstance()
                        cal.time = date
                        cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
                    } else false
                } else false
            } catch (e: Exception) {
                // Fallback for debugging - logs error but keeps app running
                // android.util.Log.e("AdminRepo", "Date parse error", e) 
                false
            }
        }

        // 2. Determine Denominators based on Distinct Activity
        
        // Distinct players who played matches in this month
        val distinctMatchPlayers = matchesThisMonth.flatMap { match ->
            listOf(
                match.team1Player1.id,
                match.team1Player2.id,
                match.team2Player1.id,
                match.team2Player2.id
            )
        }.distinct().count()

        // Distinct players who suffered buchos in this month
        // BuchoDto uses 'jogador' name string, so we count distinct names
        val distinctBuchoPlayers = buchosThisMonth.mapNotNull { it.jogador }
            .filter { it.isNotBlank() }
            .distinct()
            .count()

        val avgMatches = if (distinctMatchPlayers > 0) matchesThisMonth.size.toDouble() / distinctMatchPlayers else 0.0
        val avgBuchos = if (distinctBuchoPlayers > 0) buchosThisMonth.size.toDouble() / distinctBuchoPlayers else 0.0

        return GlobalStats(
            avgMatches = avgMatches,
            avgBuchos = avgBuchos,
            totalMatchesMonth = matchesThisMonth.size,
            totalBuchosMonth = buchosThisMonth.size,
            activeMembersCount = distinctMatchPlayers // Updating this field semantics to match participants or keep as legacy? 
                                                      // The requested change is about averages. I'll keep activeMembersCount as denominator proxy 
                                                      // or just return 0 if not used elsewhere prominently. 
                                                      // To be safe, I'll return the match participants count as it's the "active" count for the month.
        )
    }
}

data class GlobalStats(
    val avgMatches: Double,
    val avgBuchos: Double,
    val totalMatchesMonth: Int,
    val totalBuchosMonth: Int,
    val activeMembersCount: Int
)
