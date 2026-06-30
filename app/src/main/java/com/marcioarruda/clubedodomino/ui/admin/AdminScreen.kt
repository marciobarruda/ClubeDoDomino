package com.marcioarruda.clubedodomino.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import androidx.compose.ui.platform.LocalContext
import com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onEditMatch: (String) -> Unit,
    session: com.marcioarruda.clubedodomino.data.UserSession? // New parameter
) {
    val viewModel: AdminViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Partidas", "Buchos", "Jogadores")

    // Determine permissions
    val userName = session?.userName?.trim() ?: ""
    val canEdit = userName.equals("MÁRCIO", ignoreCase = true) || userName.equals("CALÁBRIA", ignoreCase = true)

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    var showReleaseNotes by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) } // Local, Server, Notes

    if (showReleaseNotes) {
        AlertDialog(
            onDismissRequest = { showReleaseNotes = false },
            title = { Text("Notas da Versão") },
            text = {
                Column {
                    Text("Sua Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME} (${com.marcioarruda.clubedodomino.BuildConfig.VERSION_CODE})", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    if (releaseInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Última disponível: v${releaseInfo?.second}", color = DominoGold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("O que mudou:", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(releaseInfo?.third ?: "Nenhuma nota disponível.", color = Color.White)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseNotes = false }) { Text("Fechar") }
            }
        )
    }

    LaunchedEffect(showReleaseNotes) {
        if (showReleaseNotes && releaseInfo == null) {
            try {
                val info = com.marcioarruda.clubedodomino.data.network.RetrofitClient.instance.checkUpdate()
                releaseInfo = Triple(com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME, info.versionName ?: info.versionCode.toString(), info.releaseNotes ?: "")
            } catch (e: Exception) {
                releaseInfo = Triple(com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME, "Indisponível", "Erro ao buscar notas.")
            }
        }
    }

    if (uiState.message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") } },
            text = { Text(uiState.message!!) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administração") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, 
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black 
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            
            val isMarcio = userName.equals("MÁRCIO", ignoreCase = true)
            if (isMarcio) {
                val context = LocalContext.current
                var bypassEnabled by remember { 
                    mutableStateOf(MatchAvailabilityManager.getBypassEnabled(context)) 
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liberar Horário de Cadastro",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Permite que você cadastre partidas fora do horário padrão (11h45 às 14h)",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = bypassEnabled,
                            onCheckedChange = { checked ->
                                MatchAvailabilityManager.setBypassEnabled(context, checked)
                                bypassEnabled = checked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = com.marcioarruda.clubedodomino.ui.theme.DominoGreen,
                                checkedTrackColor = com.marcioarruda.clubedodomino.ui.theme.DominoGreen.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = DominoGold) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    when (selectedTab) {
                        0 -> MatchesList(
                            matches = uiState.matches, 
                            onDelete = { viewModel.deleteMatch(it) },
                            onEdit = { matchId -> onEditMatch(matchId) },
                            canEdit = canEdit 
                        )
                        1 -> {
                            var expanded by remember { mutableStateOf(false) }
                            var selectedPlayerName by remember { mutableStateOf("Todos os Jogadores") }
                            val playerNames = remember(uiState.players) {
                                listOf("Todos os Jogadores") + uiState.players.map { it.user.name }.distinct().sorted()
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedPlayerName,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Filtrar por Jogador", color = DominoGold) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = Color(0xFF2C2C2C),
                                                unfocusedContainerColor = Color(0xFF2C2C2C),
                                                focusedBorderColor = DominoGold,
                                                unfocusedBorderColor = Color.Gray
                                            )
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(Color(0xFF2C2C2C))
                                        ) {
                                            playerNames.forEach { name ->
                                                DropdownMenuItem(
                                                    text = { Text(name, color = Color.White) },
                                                    onClick = {
                                                        selectedPlayerName = name
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                val filteredBuchos = remember(uiState.buchos, selectedPlayerName) {
                                    if (selectedPlayerName == "Todos os Jogadores") {
                                        uiState.buchos
                                    } else {
                                        uiState.buchos.filter { it.jogador?.trim()?.equals(selectedPlayerName.trim(), ignoreCase = true) == true }
                                    }
                                }

                                BuchosList(
                                    buchos = filteredBuchos,
                                    onDelete = { viewModel.deleteBucho(it) },
                                    onMarkPaid = { viewModel.markBuchoAsPaid(it) },
                                    canEdit = canEdit,
                                    isMarcio = userName.equals("MÁRCIO", ignoreCase = true)
                                )
                            }
                        }
                        2 -> PlayersList(
                            players = uiState.players,
                            onToggleActive = { u, a -> viewModel.togglePlayerActive(u, a) },
                            onToggleVacation = { u, v -> viewModel.togglePlayerVacation(u, v) },
                            canEdit = canEdit 
                        )
                    }
                }
            }
            
            // Footer with Version
            TextButton(
                onClick = { showReleaseNotes = true },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
            ) {
                Text(
                    text = "Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME} (Ver Notas)",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun MatchesList(
    matches: List<com.marcioarruda.clubedodomino.data.Match>, 
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    canEdit: Boolean 
) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(matches) { match ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dateFormat.format(match.date), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text("${match.team1Player1.displayName}/${match.team1Player2.displayName} vs ${match.team2Player1.displayName}/${match.team2Player2.displayName}", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Placar: ${match.score1} x ${match.score2}", color = DominoGold)
                            if (match.wasBuchoRe) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 BUCHO DE RÉ", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text("Cadastrado por: ${match.registeredBy.name}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                    if (canEdit) {
                        Row {
                            IconButton(onClick = { onEdit(match.id) }) {
                                 Text("✏️") 
                            }
                            IconButton(onClick = { onDelete(match.id) }) {
                                 Text("🗑️") 
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuchosList(
    buchos: List<com.marcioarruda.clubedodomino.data.network.BuchoDto>,
    onDelete: (Long?) -> Unit,
    onMarkPaid: (Long) -> Unit,
    canEdit: Boolean,
    isMarcio: Boolean
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(buchos) { bucho ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bucho.data ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text(bucho.jogador ?: "", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Valor: R$ ${bucho.valor}", color = DominoGold)
                            if (bucho.buchore == true) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 BUCHO DE RÉ", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (!bucho.cadastrado_por.isNullOrBlank()) {
                            Text("Cadastrado por: ${bucho.cadastrado_por}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        if (isMarcio) {
                            IconButton(onClick = { bucho.id?.let { onMarkPaid(it) } }) {
                                Text("✔️")
                            }
                        }
                        if (canEdit) {
                            IconButton(onClick = { onDelete(bucho.id) }) {
                                Text("🗑️")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayersList(
    players: List<AdminPlayerItem>,
    onToggleActive: (com.marcioarruda.clubedodomino.data.User, Boolean) -> Unit,
    onToggleVacation: (com.marcioarruda.clubedodomino.data.User, Boolean) -> Unit,
    canEdit: Boolean
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(players) { item ->
            Card( //... keeping existing card structure but disabling toggles if !canEdit
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.user.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ativo", color = Color.LightGray)
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { if(canEdit) onToggleActive(item.user, it) },
                            enabled = canEdit
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Modo Férias", color = Color.LightGray)
                        Switch(
                            checked = item.isOnVacation,
                            onCheckedChange = { if(canEdit) onToggleVacation(item.user, it) },
                            enabled = canEdit
                        )
                    }
                }
            }
        }
    }
}
