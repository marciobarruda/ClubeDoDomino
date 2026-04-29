package com.marcioarruda.clubedodomino.domain

import android.util.Log
import com.marcioarruda.clubedodomino.data.HolidayRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MatchAvailabilityManager(private val holidayRepository: HolidayRepository) {

    // Fuso horário fixo para consistência
    private val zoneId = ZoneId.of("America/Recife")

    // Horários de início e fim da janela de disponibilidade
    private val startTime: LocalTime = LocalTime.of(11, 45)
    private val endTime: LocalTime = LocalTime.of(14, 0)

    private var timeOffsetMillis: Long = 0

    suspend fun syncWithServer(apiService: com.marcioarruda.clubedodomino.data.network.ApiService) {
        try {
            val start = System.currentTimeMillis()
            val response = apiService.getServerTime()
            val serverTime = java.time.OffsetDateTime.parse(response.datetime).toInstant().toEpochMilli()
            val latency = (System.currentTimeMillis() - start) / 2
            timeOffsetMillis = (serverTime + latency) - System.currentTimeMillis()
            Log.d("MatchAvailability", "Time synced! Offset: $timeOffsetMillis ms")
        } catch (e: Exception) {
            Log.e("MatchAvailability", "Failed to sync time", e)
        }
    }

    fun isModuleAvailable(): Boolean {
        val syncedNow = System.currentTimeMillis() + timeOffsetMillis
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(syncedNow), zoneId)
        
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()
        val dayOfWeek = now.dayOfWeek

        val isHoliday = holidayRepository.isHoliday(today)
        val isWorkingDay = dayOfWeek >= DayOfWeek.MONDAY && dayOfWeek <= DayOfWeek.FRIDAY
        val isWithinTimeWindow = !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)

        val result = isWorkingDay && isWithinTimeWindow && !isHoliday

        Log.d(
            "MatchAvailability",
            "Availability Check (Synced): Date=${today}, Time=${currentTime}, Day=${dayOfWeek}, IsWorkingDay=${isWorkingDay}, IsInTime=${isWithinTimeWindow}, IsHoliday=${isHoliday}, Result=${result}"
        )

        return result
    }
}
