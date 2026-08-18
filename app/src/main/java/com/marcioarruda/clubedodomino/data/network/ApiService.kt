package com.marcioarruda.clubedodomino.data.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- DATA TRANSFER OBJECTS (DTOs) ---

data class PlayerDTO(
    val jogador: String?,
    val avatar: String?,
    val email: String?,
    val senha: String? = null,
    val ativo: Int? = 1,
    val ferias: Int? = 0
)

data class SetPlayerActiveRequest(val email: String, val ativo: Boolean)
data class SetPlayerVacationRequest(val email: String, val ferias: Boolean)
data class UpdateAvatarRequest(val email: String, val avatar: String)

data class CreatePlayerRequest(
    val name: String,
    val email: String,
    val password: String,
    val avatarId: String,
    val startYear: Int? = null,
    val startMonth: Int? = null
)

data class ActiveMatchDto(
    val id: String,
    val jogador1: String?,
    val jogador2: String?,
    val jogador3: String?,
    val jogador4: String?,
    val cadastrador: String?,
    @SerializedName("data_criacao") val dataCriacao: String?
)

data class UpdateDbPasswordRequest(
    val email: String,
    val senhaLogin: String,
    val novaSenha: String
)

data class EmergencyUpdateDbPasswordRequest(
    val novaSenha: String
)

data class SimpleStatusResponse(
    val status: String,
    val message: String? = null
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
    @SerializedName("cadastrado_por", alternate = ["cadastrador"]) val cadastrado_por: String? = null,
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
    val obs: String? = null,
    val cadastrado_por: String? = null,
    val buchore: Boolean? = null
)

// DTO para Status de Mensalidade (buscar-info-mensalidade)
data class MensalidadeDto(
    @SerializedName("id") val id: Long? = null,
    val mensalidade: String? = null, // ex: "Janeiro"
    val jogador: String? = null,
    val pago: Boolean? = false,
    val ano: Int? = null
)

// DTO para Ranking (listar-ranking)
data class RankingDto(
    val jogador: String,
    val partidas_dia: Int,
    val pontos_dia: Int,
    val partidas_mes: Int,
    val pontos_mes: Int,
    val partidas_ano: Int,
    val pontos_ano: Int,
    val buchos_aplicados_dia: Int = 0,
    val buchos_sofridos_dia: Int = 0,
    val buchos_aplicados_mes: Int = 0,
    val buchos_sofridos_mes: Int = 0,
    val buchos_aplicados_ano: Int = 0,
    val buchos_sofridos_ano: Int = 0,
    val vitorias_ano: Int = 0,
    val derrotas_ano: Int = 0,
    val vitorias_dia: Int = 0,
    val derrotas_dia: Int = 0
)

data class DebitRequest(
    val data: String,
    val jogador: String,
    val valor: Double,
    val pago: Boolean,
    val placar: String? = null,
    val dupla_vencedora: String? = null,
    val dupla_perdedora: String? = null,
    val obs: String? = null,
    val cadastrado_por: String? = null,
    val wasBuchoRe: Boolean? = null
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

data class ResetPasswordRequest(
    val email: String,
    val nova_senha: String
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

data class WorldTimeResponse(
    val datetime: String
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

    @GET("webhook/listar-ranking")
    suspend fun getRanking(): List<RankingDto>

    @POST("webhook/gravar-buchos")
    suspend fun registerDebit(@Body debit: DebitRequest): retrofit2.Response<Unit>

    // Endpoints Financeiros
    @GET("webhook/gravar-buchos")
    suspend fun getBuchos(): List<BuchoDto>
    
    @GET("webhook/buscar-info-mensalidade")
    suspend fun getMensalidades(): List<MensalidadeDto>

    @POST("webhook/buscar-info-mensalidade")
    suspend fun createMensalidade(@Body request: CreateMensalidadeRequest): retrofit2.Response<Unit>

    @POST("webhook/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("webhook/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): LoginResponse

    @POST("webhook/jogador/avatar")
    suspend fun updateProfile(@Body request: UpdateAvatarRequest): SimpleStatusResponse

    @POST("webhook/receber-comprovante")
    suspend fun uploadComprovante(@Body request: ComprovanteRequest)

    @PUT("webhook/partidas/{id}")
    suspend fun updateMatch(@Path("id") id: String, @Body match: MatchDTO): SimpleStatusResponse

    @DELETE("webhook/partidas/{id}")
    suspend fun deleteMatch(@Path("id") id: String): SimpleStatusResponse

    @DELETE("webhook/gravar-buchos/{id}")
    suspend fun deleteBucho(@Path("id") id: String): SimpleStatusResponse

    @POST("webhook/gravar-buchos/{id}/pagar")
    suspend fun markBuchoAsPaid(@Path("id") id: String): SimpleStatusResponse

    @DELETE("webhook/buscar-info-mensalidade/{id}")
    suspend fun deleteMensalidade(@Path("id") id: String): SimpleStatusResponse

    @POST("webhook/buscar-info-mensalidade/{id}/pagar")
    suspend fun markMensalidadeAsPaid(@Path("id") id: String): SimpleStatusResponse

    @POST("webhook/jogador/ativo")
    suspend fun setPlayerActive(@Body request: SetPlayerActiveRequest): SimpleStatusResponse

    @POST("webhook/jogador/ferias")
    suspend fun setPlayerVacation(@Body request: SetPlayerVacationRequest): SimpleStatusResponse

    @POST("webhook/criar-jogador")
    suspend fun createPlayer(@Body request: CreatePlayerRequest): SimpleStatusResponse

    @GET("webhook/partidas-em-andamento")
    suspend fun getActiveMatches(@Query("jogador") jogador: String? = null): List<ActiveMatchDto>

    @POST("webhook/partidas-em-andamento")
    suspend fun startActiveMatch(@Body activeMatch: ActiveMatchDto): SimpleStatusResponse

    @DELETE("webhook/partidas-em-andamento/{id}")
    suspend fun deleteActiveMatch(@Path("id") id: String): SimpleStatusResponse

    @POST("webhook/admin/atualizar-senha-db")
    suspend fun updateDbPassword(@Body request: UpdateDbPasswordRequest): SimpleStatusResponse

    // Rota de emergência: corrige a senha do banco usada pelo servidor sem exigir login —
    // usada quando a senha está desalinhada a ponto do login em si estar quebrado.
    @POST("webhook/admin/emergencia/atualizar-senha-db")
    suspend fun emergencyUpdateDbPassword(
        @Header("X-Admin-Key") adminKey: String,
        @Body request: EmergencyUpdateDbPasswordRequest
    ): SimpleStatusResponse

    @POST("webhook/stack-trace")
    suspend fun sendStackTrace(@Body request: StackTraceRequest): retrofit2.Response<Unit>

    @POST("webhook/estatisticas-globais")
    suspend fun triggerTaxasExtras(@Body body: Map<String, String> = emptyMap()): retrofit2.Response<Unit>

    @GET("https://worldtimeapi.org/api/timezone/America/Recife")
    suspend fun getServerTime(): WorldTimeResponse
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
    @SerializedName("release_notes", alternate = ["releaseNotes", "notes"]) val releaseNotes: String?,
    @SerializedName("min_version", alternate = ["minVersion"]) val minVersionCode: Int? = 0
)

// --- Retrofit Singleton Client ---

object RetrofitClient {
    private const val BASE_URL = "https://geral-clube-domino-api.ep9oni.easypanel.host/"

    // Corpo completo (incluindo senhas) só é logado em builds de debug — em release, apenas
    // linha básica (método/URL/status), para não vazar credenciais no Logcat de produção.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.marcioarruda.clubedodomino.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
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
