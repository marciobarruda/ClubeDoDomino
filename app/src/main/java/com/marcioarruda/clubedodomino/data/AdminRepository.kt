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

        // 1. Filter Data (Target Month)
        val matchesThisMonth = matches.filter {
            val cal = Calendar.getInstance()
            cal.time = it.date
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }

        val buchosThisMonth = buchos.filter { dto ->
            val date = parseAnyDate(dto.data)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date
                cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
            } else false
        }

        // 2. Determine Denominator based on 'Ativo' switch (as per new requirement)
        // Note: Non-members and special accounts are excluded
        val activeMembers = allUsers.filter { user ->
            !user.name.contains("NÃO MEMBRO", ignoreCase = true) && 
            user.id != "7" && 
            isPlayerActive(user.id)
        }
        val activeCount = if (activeMembers.isNotEmpty()) activeMembers.size else 1 // Avoid div by zero

        val avgMatches = if (activeCount > 0) matchesThisMonth.size.toDouble() / activeCount else 0.0
        val avgBuchosValue = if (activeCount > 0) buchosThisMonth.sumOf { it.valor ?: 0.0 } / activeCount else 0.0

        return GlobalStats(
            avgMatches = avgMatches,
            avgBuchos = avgBuchosValue,
            totalMatchesMonth = matchesThisMonth.size,
            totalBuchosMonth = buchosThisMonth.size,
            activeMembersCount = activeCount
        )
    }

    fun getLastBusinessDayOfMonth(year: Int, month: Int): Date {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))

        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.time
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
                return java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).apply { isLenient = false }.parse(dateStr)
            } catch (e: Exception) {}
        }
        return null
    }
}

data class GlobalStats(
    val avgMatches: Double,
    val avgBuchos: Double,
    val totalMatchesMonth: Int = 0,
    val totalBuchosMonth: Int = 0,
    val activeMembersCount: Int = 0,
    val playerMatches: Int? = null,
    val playerBuchosValue: Double? = null
)
