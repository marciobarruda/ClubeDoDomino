package com.marcioarruda.clubedodomino.domain

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.marcioarruda.clubedodomino.data.database.MySqlDatabase

object MatchAvailabilityManager {
    
    private val holidayRepository = com.marcioarruda.clubedodomino.data.HolidayRepository
    private val zoneId = ZoneId.of("America/Recife")
    private val startTime: LocalTime = LocalTime.of(11, 45)
    private val endTime: LocalTime = LocalTime.of(14, 0)

    private var baseTimeMillis: Long = 0
    private var baseElapsedMillis: Long = 0
    
    var isTimeManipulated: Boolean = false
        private set

    fun initialize(context: Context) {
        baseTimeMillis = System.currentTimeMillis()
        baseElapsedMillis = SystemClock.elapsedRealtime()
        isTimeManipulated = !isAutoTimeEnabled(context)
        Log.d("MatchAvailability", "Initialized. AutoTime=${!isTimeManipulated}")
    }

    private fun isAutoTimeEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME) == 1
        } catch (e: Exception) {
            true // Por segurança, se não conseguir ler, assume true
        }
    }

    private fun checkDrift(): Boolean {
        if (baseElapsedMillis == 0L) return false
        
        val currentElapsed = SystemClock.elapsedRealtime()
        val currentTime = System.currentTimeMillis()
        
        val expectedTime = baseTimeMillis + (currentElapsed - baseElapsedMillis)
        val drift = Math.abs(currentTime - expectedTime)
        
        // Se o relógio do sistema mudou mais de 30 segundos em relação ao tempo decorrido, houve manipulação
        if (drift > 30_000) {
            isTimeManipulated = true
            Log.w("MatchAvailability", "Time manipulation detected! Drift: $drift ms")
            return true
        }
        return false
    }

    fun getExtendedDiagnosticInfo(context: Context): String {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), zoneId)
        val dayOfWeek = now.dayOfWeek
        val isAuto = isAutoTimeEnabled(context)
        
        val dayOfWeekPt = when(dayOfWeek) {
            DayOfWeek.MONDAY -> "Segunda"
            DayOfWeek.TUESDAY -> "Terça"
            DayOfWeek.WEDNESDAY -> "Quarta"
            DayOfWeek.THURSDAY -> "Quinta"
            DayOfWeek.FRIDAY -> "Sexta"
            DayOfWeek.SATURDAY -> "Sábado"
            DayOfWeek.SUNDAY -> "Domingo"
            else -> dayOfWeek.name
        }
        
        return "Hora: ${now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))} ($dayOfWeekPt)\n" +
               "Data Automática: ${if(isAuto) "Ativa" else "DESATIVADA"}\n" +
               "Manipulação: ${if(isTimeManipulated) "Detectada" else "Não"}"
    }

    private fun isBypassEnabled(context: Context, username: String?): Boolean {
        if (username?.trim()?.uppercase() != "MÁRCIO") return false
        val prefs = context.getSharedPreferences("club_domino_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("bypass_time_limit_marcio", false)
    }

    fun setBypassEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("club_domino_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bypass_time_limit_marcio", enabled).apply()
    }

    fun getBypassEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("club_domino_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("bypass_time_limit_marcio", false)
    }

    suspend fun isModuleAvailable(context: Context, username: String? = null): Boolean {
        // Se for o Márcio e o bypass estiver ativo, libera sem nenhuma validação!
        if (username != null && isBypassEnabled(context, username)) {
            Log.d("MatchAvailability", "Bypass active for MÁRCIO - match registration allowed.")
            return true
        }

        // 1. Verifica integridade primeiro
        if (!isAutoTimeEnabled(context)) {
            isTimeManipulated = true
            return false
        }
        
        if (checkDrift()) {
            return false
        }
 
        // 2. Verifica horários
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), zoneId)
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()
        val dayOfWeek = now.dayOfWeek
 
        val isHoliday = holidayRepository.isHoliday(today)
        val isWorkingDay = dayOfWeek >= DayOfWeek.MONDAY && dayOfWeek <= DayOfWeek.FRIDAY
        
        // Exceção para hoje (30/06/2026) estendendo o horário até 17h para testes
        val currentEndTime = if (today.year == 2026 && today.monthValue == 6 && today.dayOfMonth == 30) {
            LocalTime.of(17, 0)
        } else {
            endTime
        }
        val isWithinTimeWindow = !currentTime.isBefore(startTime) && currentTime.isBefore(currentEndTime)
 
        var result = isWorkingDay && isWithinTimeWindow && !isHoliday
 
        if (!result && username != null) {
            val hasActive = withContext(Dispatchers.IO) {
                try {
                    MySqlDatabase.connect().use { conn ->
                        val ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM partidas_em_andamento " +
                            "WHERE (jogador1 = ? OR jogador2 = ? OR jogador3 = ? OR jogador4 = ?) " +
                            "AND DATE(data_criacao) = CURDATE()"
                        )
                        ps.setString(1, username)
                        ps.setString(2, username)
                        ps.setString(3, username)
                        ps.setString(4, username)
                        val rs = ps.executeQuery()
                        if (rs.next()) rs.getInt(1) > 0 else false
                    }
                } catch (t: Throwable) {
                    false
                }
            }
            if (hasActive) {
                result = true
                Log.d("MatchAvailability", "Bypass: User $username has an active match in progress after hours.")
            }
        }

        Log.d(
            "MatchAvailability",
            "Check: AutoTime=${!isTimeManipulated}, Time=${currentTime}, Result=${result}"
        )
 
        return result
    }

    fun getRemainingSecondsToClose(context: Context, username: String? = null): Long? {
        if (username != null && isBypassEnabled(context, username)) {
            return null
        }
        
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), zoneId)
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()
        val dayOfWeek = now.dayOfWeek
        
        val isHoliday = holidayRepository.isHoliday(today)
        val isWorkingDay = dayOfWeek >= DayOfWeek.MONDAY && dayOfWeek <= DayOfWeek.FRIDAY
        
        if (!isWorkingDay || isHoliday) return null
        
        val currentEndTime = if (today.year == 2026 && today.monthValue == 6 && today.dayOfMonth == 30) {
            LocalTime.of(17, 0)
        } else {
            endTime
        }
        
        if (currentTime.isBefore(startTime) || currentTime.isAfter(currentEndTime)) return null
        
        val seconds = currentEndTime.toSecondOfDay() - currentTime.toSecondOfDay()
        return if (seconds in 0..600) seconds.toLong() else null
    }
}
