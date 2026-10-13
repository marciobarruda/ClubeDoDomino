package com.marcioarruda.clubedodomino.ui.util

import java.util.Locale

private val LocaleBR = Locale("pt", "BR")

// Valor em real brasileiro ("R$ 3,00"), independente do idioma configurado no aparelho.
fun formatBRL(valor: Double): String = "R$ ${String.format(LocaleBR, "%,.2f", valor)}"
