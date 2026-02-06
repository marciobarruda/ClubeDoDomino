package com.marcioarruda.clubedodomino.data.remote

import com.google.gson.annotations.SerializedName

data class Debit(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("id_jogador") val playerId: Int,
    @SerializedName("nome_jogador") val playerName: String,
    @SerializedName("valor") val value: Double,
    @SerializedName("data_vencimento") val dueDate: String,
    @SerializedName("pago") val paid: Boolean
)
