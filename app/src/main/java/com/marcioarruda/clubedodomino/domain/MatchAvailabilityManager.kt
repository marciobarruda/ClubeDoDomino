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

    fun isModuleAvailable(context: Context): Boolean {
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
        val isWithinTimeWindow = !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)

        val result = isWorkingDay && isWithinTimeWindow && !isHoliday

        Log.d(
            "MatchAvailability",
            "Check: AutoTime=${!isTimeManipulated}, Time=${currentTime}, Result=${result}"
        )

        return result
    }
}
