package com.marcioarruda.clubedodomino.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.ui.theme.GlassyColor
import com.marcioarruda.clubedodomino.ui.theme.RoyalGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMatchScreen(
    navController: NavController, 
    viewModel: MatchViewModel = viewModel(),
    matchId: String? = null,
    session: com.marcioarruda.clubedodomino.data.UserSession? = null
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(matchId) {
        if (matchId != null) {
            viewModel.loadMatch(matchId)
        }
    }

    // Handle Success Navigation (e.g. after Update or Auto-Close)
    LaunchedEffect(state.success) {
        if (state.success) {
            // Se houver erro (provavelmente o de fechamento automático), esperamos 5 segundos 
            // para o usuário conseguir ler o diagnóstico antes de fechar a tela.
            if (state.error != null) {
                kotlinx.coroutines.delay(5000)
            }
            navController.popBackStack()
        }
    }

    // Handle Repeat Dialog
    if (state.showRepeatDialog) {
        AlertDialog(
            onDismissRequest = { /* Force selection */ },
            title = { Text("Partida Salva!") },
            text = { Text("Deseja repetir a partida com as mesmas duplas?") },
            confirmButton = {
                Button(onClick = { viewModel.onRepeatMatch(true) }, colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)) {
                    Text("Sim", color = Color.Black)
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.onRepeatMatch(false) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Não", color = Color.White)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nova Partida", color = RoyalGold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = RoyalGold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Player Selection
            PlayerSelectionCard(state, viewModel)
            Spacer(modifier = Modifier.height(16.dp))

            // Score Input
            ScoreInputCard(state, viewModel)
            Spacer(modifier = Modifier.height(16.dp))

            // Options
            OptionsCard(state, viewModel)
            Spacer(modifier = Modifier.height(32.dp))

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            Button(
                onClick = { 
                    if (state.editingMatchId != null) {
                         viewModel.updateMatch(state.editingMatchId!!)
                    } else {
                         val currentUser = state.availablePlayers.find { it.id == session?.userEmail } 
                             ?: state.availablePlayers.firstOrNull() 
                             ?: User("0","User","User","","c1") 
                         viewModel.saveMatch(registeredBy = currentUser)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && state.isModuleAvailable
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    if (state.editingMatchId != null) {
                        Text("Atualizar Partida", color = Color.Black)
                    } else {
                        Text("Salvar Partida", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelectionCard(state: MatchRegistrationState, viewModel: MatchViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = GlassyColor), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Dupla 1", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Filter logic: Exclude players selected in OTHER slots
                val p1List = state.availablePlayers.filter { it == state.selectedPlayers[0] || it !in state.selectedPlayers }
                val p2List = state.availablePlayers.filter { it == state.selectedPlayers[1] || it !in state.selectedPlayers }
                
                PlayerDropdown(p1List, state.selectedPlayers[0], { viewModel.onPlayerSelected(0, it) }, Modifier.weight(1f))
                PlayerDropdown(p2List, state.selectedPlayers[1], { viewModel.onPlayerSelected(1, it) }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Dupla 2", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val p3List = state.availablePlayers.filter { it == state.selectedPlayers[2] || it !in state.selectedPlayers }
                val p4List = state.availablePlayers.filter { it == state.selectedPlayers[3] || it !in state.selectedPlayers }

                PlayerDropdown(p3List, state.selectedPlayers[2], { viewModel.onPlayerSelected(2, it) }, Modifier.weight(1f))
                PlayerDropdown(p4List, state.selectedPlayers[3], { viewModel.onPlayerSelected(3, it) }, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayerDropdown(players: List<User>, selectedPlayer: User?, onPlayerSelected: (User) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // Reset filter text when selection changes externally (e.g. clear)
    var filterText by remember(selectedPlayer) { mutableStateOf(selectedPlayer?.displayName ?: "") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        TextField(
            value = filterText,
            onValueChange = { 
                filterText = it 
                expanded = true
            },
            modifier = Modifier.menuAnchor(),
            label = { Text("Jogador") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedContainerColor = Color.Transparent, 
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
        )

        // Show all players if the text matches the current selection exactly (meaning user just opened it)
        // or if text is empty. Otherwise filter.
        val filteredPlayers = if (filterText == (selectedPlayer?.displayName ?: "")) {
             players
        } else {
             players.filter { it.displayName.contains(filterText, ignoreCase = true) }
        }
        
        if (filteredPlayers.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filteredPlayers.forEach { player ->
                    DropdownMenuItem(
                        text = { Text(player.displayName) },
                        onClick = {
                            onPlayerSelected(player)
                            // We don't update filterText here manually because the parent update 
                            // will trigger the LaunchedEffect/remember above.
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreInputCard(state: MatchRegistrationState, viewModel: MatchViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = GlassyColor), shape = RoundedCornerShape(16.dp)) {
        // Calculate Team Labels
        val t1Names = listOfNotNull(state.selectedPlayers[0]?.displayName, state.selectedPlayers[1]?.displayName)
        val t1Label = if (t1Names.isNotEmpty()) t1Names.sorted().joinToString(" / ") else "Dupla 1"

        val t2Names = listOfNotNull(state.selectedPlayers[2]?.displayName, state.selectedPlayers[3]?.displayName)
        val t2Label = if (t2Names.isNotEmpty()) t2Names.sorted().joinToString(" / ") else "Dupla 2"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreControl(t1Label, state.score1, { viewModel.onScoreChange(1, it) }, Modifier.weight(1f))
            Text("X", fontSize = 24.sp, color = RoyalGold, modifier = Modifier.padding(horizontal = 8.dp))
            ScoreControl(t2Label, state.score2, { viewModel.onScoreChange(2, it) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreControl(label: String, score: Int, onScoreChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Allow text to wrap if names are long
        Text(
            text = label, 
            style = MaterialTheme.typography.titleMedium, 
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { if (score > 0) onScoreChange(score - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = "Diminuir Placar", tint = RoyalGold)
            }
            Text(score.toString(), fontSize = 48.sp, textAlign = TextAlign.Center, color = Color.White)
            IconButton(onClick = { onScoreChange(score + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "Aumentar Placar", tint = RoyalGold)
            }
        }
    }
}


@Composable
private fun OptionsCard(state: MatchRegistrationState, viewModel: MatchViewModel) {
     Card(colors = CardDefaults.cardColors(containerColor = GlassyColor), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.isBuchoRe,
                    onCheckedChange = { viewModel.onBuchoReChanged(it) },
                    enabled = state.isBuchoReEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = RoyalGold,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.Black
                    )
                )
                Text("Foi Bucho de Ré?", color = if (state.isBuchoReEnabled) Color.White else Color.Gray)
            }
            // Removed "Valendo Ranking" as per instruction (always true)
        }
     }
}
