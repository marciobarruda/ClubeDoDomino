package com.marcioarruda.clubedodomino.ui.admin

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
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
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
                        1 -> BuchosList(
                            buchos = uiState.buchos, 
                            onDelete = { viewModel.deleteBucho(it) },
                            canEdit = canEdit 
                        )
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
            Text(
                text = "Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME}",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
            )
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
                        Text("Placar: ${match.score1} x ${match.score2}", color = DominoGold)
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
fun BuchosList(buchos: List<com.marcioarruda.clubedodomino.data.network.BuchoDto>, onDelete: (Long?) -> Unit, canEdit: Boolean) {
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
                        Text("Valor: R$ ${bucho.valor}", color = DominoGold)
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
