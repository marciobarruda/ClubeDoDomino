package com.marcioarruda.clubedodomino.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object HolidayRepository {

    // Lista de feriados no formato "MM-dd" para recorrência anual.
    // Inclui feriados nacionais e locais (Recife).
    private val holidays = setOf(
        // Nacionais
        "01-01", // Confraternização Universal
        "04-21", // Tiradentes
        "05-01", // Dia do Trabalho
        "09-07", // Independência do Brasil
        "10-12", // Nossa Senhora Aparecida
        "11-02", // Finados
        "11-15", // Proclamação da República
        "12-24", // Véspera de Natal
        "12-25", // Natal
        "12-31", // Véspera de Ano Novo

        // Locais (Recife) e Pontos Facultativos Comuns
        "03-06", // Data Magna de Pernambuco
        "06-23", // Véspera de São João (Ponto Facultativo Comum)
        "06-24", // São João
        "07-16", // Nossa Senhora do Carmo (Padroeira de Recife)
        "10-28", // Dia do Servidor Público (Ponto Facultativo Comum)
        "12-08"  // Nossa Senhora da Conceição
    )

    // Feriados móveis que precisam ser calculados ou definidos anualmente.
    // Formato "yyyy-MM-dd"
    private val movingHolidays = setOf(
        // Carnaval e Sexta-feira Santa 2024
        "2024-02-12",
        "2024-02-13",
        "2024-03-29",
        // Carnaval e Sexta-feira Santa 2025
        "2025-03-03",
        "2025-03-04",
        "2025-04-18",
        // Adicionar anos futuros conforme necessário
        "2026-02-16",
        "2026-02-17",
        "2026-04-03",
        "2026-06-04"  // Corpus Christi
    )

    private val dayMonthFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun isHoliday(date: LocalDate): Boolean {
        // Verifica primeiro os feriados recorrentes (pelo dia e mês)
        val isFixedHoliday = holidays.contains(date.format(dayMonthFormatter))
        if (isFixedHoliday) return true

        // Depois, verifica os feriados móveis (pela data completa)
        return movingHolidays.contains(date.format(fullDateFormatter))
    }
}
