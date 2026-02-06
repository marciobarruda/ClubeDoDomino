// File: data/remote/ApiService.kt
package com.marcioarruda.clubedodomino.data.remote

import com.google.gson.annotations.SerializedName
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.remote.Debit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// DTO para o corpo da requisição do comprovante
data class ComprovanteRequest(
    @SerializedName("jogador_nome")
    val jogadorNome: String,

    @SerializedName("valor_total")
    val valorTotal: Double,

    @SerializedName("bucho_ids")
    val buchoIds: List<String>,

    @SerializedName("mensalidade_ids")
    val mensalidadeIds: List<String>,

    @SerializedName("imagem_base64")
    val imagemBase64: String
)

interface ApiService {

    @GET("webhook/buscar-jogadores")
    suspend fun getUsers(): List<User>

    @GET("webhook/partidas")
    suspend fun getMatches(): List<Match>

    @POST("webhook/partidas")
    suspend fun createMatch(@Body match: Match): Match

    @POST("webhook/mensalidades")
    suspend fun createDebit(@Body debit: Debit): Debit

    // Endpoint corrigido com a URL absoluta
    @POST("https://n8ndev.devlogconsultoria.com.br/webhook/receber-comprovante")
    suspend fun uploadComprovante(@Body request: ComprovanteRequest)


    companion object {
        private const val BASE_URL = "https://n8ndev.devlogconsultoria.com.br/"

        fun create(): ApiService {
            val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
