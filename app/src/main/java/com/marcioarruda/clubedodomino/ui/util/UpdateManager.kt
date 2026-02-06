package com.marcioarruda.clubedodomino.ui.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.marcioarruda.clubedodomino.BuildConfig
import com.marcioarruda.clubedodomino.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UpdateManager(private val context: Context) {

    fun checkForUpdate(onUpdateAvailable: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                     Toast.makeText(context, "Verificando atualizações...", Toast.LENGTH_SHORT).show()
                }
                
                val updateInfo = RetrofitClient.instance.checkUpdate()
                
                withContext(Dispatchers.Main) {
                     Toast.makeText(context, "Server: v${updateInfo.versionCode}", Toast.LENGTH_SHORT).show()
                }

                if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                    withContext(Dispatchers.Main) {
                        onUpdateAvailable(updateInfo.apkUrl, updateInfo.releaseNotes ?: "Nova versão disponível!")
                    }
                } else {
                     withContext(Dispatchers.Main) {
                         Toast.makeText(context, "App atualizado (v${BuildConfig.VERSION_CODE})", Toast.LENGTH_SHORT).show()
                     }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                reportError("CheckUpdateFailed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro Update: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun downloadAndInstall(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Baixando atualização...", Toast.LENGTH_SHORT).show()
            }

            try {
                // Adiciona &confirm=t para bypass do virus scan warning do Google Drive
                val finalUrl = if (url.contains("drive.google.com")) {
                    if (url.contains("confirm=")) url else "$url&confirm=t"
                } else url

                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = okhttp3.Request.Builder().url(finalUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw Exception("Falha no download: HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Corpo da resposta vazio")
                val inputStream = body.byteStream()
                
                val fileName = "clube_update.apk"
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                
                file.outputStream().use { output ->
                    inputStream.copyTo(output)
                }

                val fileSizeKb = file.length() / 1024
                
                // Verifica se é um APK válido (começa com PK)
                val isValid = file.inputStream().use {
                    val sig = ByteArray(2)
                    it.read(sig) == 2 && sig[0] == 0x50.toByte() && sig[1] == 0x4B.toByte()
                }

                if (!isValid || fileSizeKb < 100) {
                    val preview = try { file.readText().take(300) } catch (e: Exception) { "N/A" }
                    throw Exception("Arquivo inválido (${fileSizeKb}KB). Início: $preview")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download OK (${fileSizeKb}KB)", Toast.LENGTH_SHORT).show()
                    installApk(file)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                reportError("DownloadInstallFailed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro no Download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isValidApk(file: File): Boolean {
        // Basic check: file size > 100KB and starts with "PK"
        if (file.length() < 100 * 1024) return false
        
        try {
            file.inputStream().use { 
                val signature = ByteArray(2)
                if (it.read(signature) != 2) return false
                // PK signature = 0x50 0x4B
                return signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte()
            }
        } catch (e: Exception) {
            return false
        }
    }

    private fun installApk(file: File) {
        try {
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                throw Exception("Arquivo não encontrado após download.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            reportError("InstallIntentFailed", e)
            Toast.makeText(context, "Erro ao instalar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun reportError(tag: String, e: Throwable) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stackTrace = e.stackTraceToString()
                RetrofitClient.instance.sendStackTrace(
                     com.marcioarruda.clubedodomino.data.network.StackTraceRequest(
                         error = "$tag: ${e.message}",
                         stackTrace = stackTrace
                     )
                )
            } catch (ignored: Exception) {
                // Fail silently on reporting
            }
        }
    }
}
