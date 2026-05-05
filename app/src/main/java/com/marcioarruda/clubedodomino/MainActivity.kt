package com.marcioarruda.clubedodomino

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.marcioarruda.clubedodomino.ui.AppNavigation
import com.marcioarruda.clubedodomino.ui.theme.ClubeDoDominoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val updateManager = com.marcioarruda.clubedodomino.ui.util.UpdateManager(this)

        setContent {
            ClubeDoDominoTheme {
                val showDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                val updateUrl = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                val releaseNotes = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

                val isMandatory = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    updateManager.checkForUpdate { url, notes, mandatory ->
                        updateUrl.value = url
                        releaseNotes.value = notes
                        isMandatory.value = mandatory
                        showDialog.value = true
                    }
                }
                
                if (showDialog.value) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { if (!isMandatory.value) showDialog.value = false },
                        title = { androidx.compose.material3.Text(if (isMandatory.value) "Atualização Obrigatória ⚠️" else "Nova Versão Disponível 🚀") },
                        text = { androidx.compose.material3.Text(releaseNotes.value) },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                updateManager.downloadAndInstall(updateUrl.value)
                                if (!isMandatory.value) showDialog.value = false
                            }) {
                                androidx.compose.material3.Text("Atualizar Agora")
                            }
                        },
                        dismissButton = {
                            if (!isMandatory.value) {
                                androidx.compose.material3.TextButton(onClick = { showDialog.value = false }) {
                                    androidx.compose.material3.Text("Depois")
                                }
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
