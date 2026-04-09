package com.marcioarruda.clubedodomino.data.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// --- DATA TRANSFER OBJECTS (DTOs) ---

data class PlayerDTO(
    val jogador: String?,
    val avatar: String?,
    val email: String?,
    val senha: String? = null
)

data class MatchDTO(
    val id: Long?,
    val data: String?,
    val jogador1: String?,
    val jogador2: String?,
    val jogador3: String?,
    val jogador4: String?,
    val scored1: Int?,
    val scored2: Int?,
    val buchore: Boolean?,
    val pts: Int?,
    val dupla_vencedora: String?,
    val cadastrado_por: String? = null,
    val buttonName: String? = null
)

// DTO para Histórico de Buchos (gravar-buchos)
data class BuchoDto(
    @SerializedName("id") val id: Long? = null,
    val data: String? = null,
    val jogador: String? = null,
    val valor: Double? = null,
    val pago: Boolean? = false,
    val placar: String? = null,
    val dupla_vencedora: String? = null,
    val dupla_perdedora: String? = null,
    val obs: String? = null
)

// DTO para Status de Mensalidade (buscar-info-mensalidade)
data class MensalidadeDto(
    @SerializedName("id") val id: Long? = null,
    val mensalidade: String? = null, // ex: "Janeiro"
    val jogador: String? = null,
    val pago: Boolean? = false,
    val ano: Int? = null
)

data class DebitRequest(
    val data: String,
    val jogador: String,
    val valor: Double,
    val pago: Boolean,
    val placar: String? = null,
    val dupla_vencedora: String? = null,
    val dupla_perdedora: String? = null,
    val obs: String? = null
)

data class LoginRequest(
    val email: String,
    val senha: String
)

data class LoginResponse(
    val status: String
)

data class UpdatePlayerRequest(
    val email: String,
    val senha: String
)

data class UpdateProfileRequest(
    val email: String,
    val avatar: String // Base64
)

// DTO para criar mensalidade
data class CreateMensalidadeRequest(
    val jogador: String,
    @SerializedName("data_vencimento") val dataVencimento: String
)

// DTO para o corpo da requisição do comprovante
data class ComprovanteRequest(
    @SerializedName("jogador_nome")
    val jogadorNome: String,

    @SerializedName("valor_total")
    val valorTotal: Double,

    @SerializedName("bucho_ids")
    val buchoIds: List<Long>,

    @SerializedName("mensalidade_ids")
    val mensalidadeIds: List<String>,

    @SerializedName("mensalidade_vencimentos")
    val mensalidadeVencimentos: List<String>,

    @SerializedName("imagem_base64")
    val imagemBase64: String
)



// --- API Service Interface ---

interface ApiService {
    @GET("webhook/buscar-jogadores")
    suspend fun getPlayers(): List<PlayerDTO>

    @POST("webhook/buscar-jogadores")
    suspend fun updatePlayer(@Body request: UpdatePlayerRequest): ResponseBody

    @GET("webhook/partidas")
    suspend fun getMatches(): List<MatchDTO>

    @POST("webhook/partidas")
    suspend fun registerMatch(@Body match: MatchDTO): retrofit2.Response<Unit>

    @POST("webhook/gravar-buchos")
    suspend fun registerDebit(@Body debit: DebitRequest): retrofit2.Response<Unit>

    // Endpoints Financeiros
    @GET("https://n8ndev.devlogconsultoria.com.br/webhook/gravar-buchos")
    suspend fun getBuchos(): List<BuchoDto>
    
    @GET("webhook/buscar-info-mensalidade")
    suspend fun getMensalidades(): List<MensalidadeDto>

    @POST("webhook/buscar-info-mensalidade")
    suspend fun createMensalidade(@Body request: CreateMensalidadeRequest): retrofit2.Response<Unit>

    @POST("webhook/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("webhook/atualizar-dados")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ResponseBody

    @POST("webhook/receber-comprovante")
    suspend fun uploadComprovante(@Body request: ComprovanteRequest)

    @POST("webhook/apagar-partida")
    suspend fun deleteMatch(@Body request: DeleteRequest): retrofit2.Response<Unit>

    @POST("webhook/apagar-partida")
    suspend fun updateMatch(@Body match: MatchDTO): retrofit2.Response<Unit>

    @POST("webhook/apagar-bucho")
    suspend fun deleteBucho(@Body request: DeleteRequest): retrofit2.Response<Unit>

    @POST("webhook/stack-trace")
    suspend fun sendStackTrace(@Body request: StackTraceRequest): retrofit2.Response<Unit>

    @GET("webhook/checar-atualizacao")
    suspend fun checkUpdate(): UpdateInfo

    @POST("webhook/estatisticas-globais")
    suspend fun triggerTaxasExtras(@Body body: Map<String, String> = emptyMap()): retrofit2.Response<Unit>
}

data class StackTraceRequest(
    val error: String,
    val stackTrace: String,
    val device: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val appVersion: String = com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME
)

data class UpdateInfo(
    @SerializedName("version_code", alternate = ["versionCode"]) val versionCode: Int,
    @SerializedName("version_name", alternate = ["versionName"]) val versionName: String,
    @SerializedName("apk_url", alternate = ["apkUrl", "url"]) val apkUrl: String,
    @SerializedName("release_notes", alternate = ["releaseNotes", "notes"]) val releaseNotes: String?
)

data class DeleteRequest(
    val id: String,
    val buttonName: String? = "Excluir"
)

// --- Retrofit Singleton Client ---

object RetrofitClient {
    private const val BASE_URL = "https://n8ndev.devlogconsultoria.com.br/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
