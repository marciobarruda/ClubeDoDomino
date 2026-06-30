package com.marcioarruda.clubedodomino.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.ui.theme.*
import com.marcioarruda.clubedodomino.ui.util.AvatarImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMatchScreen(
    navController: NavController,
    viewModel: MatchViewModel = viewModel(),
    matchId: String? = null,
    session: com.marcioarruda.clubedodomino.data.UserSession? = null
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(matchId) { if (matchId != null) viewModel.loadMatch(matchId) }
    LaunchedEffect(session) { viewModel.setCurrentUser(session?.userName) }

    LaunchedEffect(state.success) {
        if (state.success) {
            if (state.error != null) kotlinx.coroutines.delay(5000)
            navController.popBackStack()
        }
    }

    if (state.showRepeatDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DominoSurface,
            title = { Text("⚡ Partida Salva!", color = DominoGreen, fontWeight = FontWeight.Black) },
            text = { Text("Repetir as mesmas duplas?", color = DominoLight) },
            confirmButton = {
                Button(onClick = { viewModel.onRepeatMatch(true) }, colors = ButtonDefaults.buttonColors(containerColor = DominoGreen), shape = RoundedCornerShape(12.dp)) {
                    Text("Sim, vamos!", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.onRepeatMatch(false) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = DominoMuted), shape = RoundedCornerShape(12.dp)) {
                    Text("Não, obrigado")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.editingMatchId != null) "✏️ Editar Partida" else "🎯 Nova Partida",
                        color = DominoGreen,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGreen)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF051C10), DominoBg)))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayerSelectionCard(state, viewModel)
            Spacer(Modifier.height(16.dp))
            ScoreInputCard(state, viewModel)
            Spacer(Modifier.height(16.dp))
            OptionsCard(state, viewModel)
            Spacer(Modifier.height(24.dp))

            if (state.error != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DominoError.copy(alpha = 0.12f))
                ) {
                    Text(
                        state.error!!,
                        color = DominoError,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (state.editingMatchId != null) {
                        viewModel.updateMatch(state.editingMatchId!!)
                    } else {
                        val currentUser = state.availablePlayers.find { it.id == session?.userEmail }
                            ?: state.availablePlayers.firstOrNull()
                            ?: User("0", "User", "User", "", "c1")
                        viewModel.saveMatch(registeredBy = currentUser)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isModuleAvailable) DominoGreen else DominoMuted
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                enabled = !state.isLoading && state.isModuleAvailable
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else if (!state.isModuleAvailable) {
                    Text("⏰ Fora do Horário", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Text(if (state.editingMatchId != null) "✅ Atualizar Partida" else "🎮 Salvar Partida", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun PlayerSelectionCard(state: MatchRegistrationState, viewModel: MatchViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            TeamHeader("TIME 1", DominoGreen)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val p1List = state.availablePlayers.filter { it == state.selectedPlayers[0] || it !in state.selectedPlayers }
                val p2List = state.availablePlayers.filter { it == state.selectedPlayers[1] || it !in state.selectedPlayers }
                PlayerDropdown(p1List, state.selectedPlayers[0], { viewModel.onPlayerSelected(0, it) }, Modifier.weight(1f))
                PlayerDropdown(p2List, state.selectedPlayers[1], { viewModel.onPlayerSelected(1, it) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DominoGreen.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            TeamHeader("TIME 2", DominoOrange)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val p3List = state.availablePlayers.filter { it == state.selectedPlayers[2] || it !in state.selectedPlayers }
                val p4List = state.availablePlayers.filter { it == state.selectedPlayers[3] || it !in state.selectedPlayers }
                PlayerDropdown(p3List, state.selectedPlayers[2], { viewModel.onPlayerSelected(2, it) }, Modifier.weight(1f))
                PlayerDropdown(p4List, state.selectedPlayers[3], { viewModel.onPlayerSelected(3, it) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TeamHeader(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDropdown(players: List<User>, selectedPlayer: User?, onPlayerSelected: (User) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember(selectedPlayer) { mutableStateOf(selectedPlayer?.displayName ?: "") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        TextField(
            value = filterText,
            onValueChange = { filterText = it; expanded = true },
            modifier = Modifier.menuAnchor(),
            label = { Text("Jogador", fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = DominoLight,
                unfocusedTextColor = DominoLight,
                focusedLabelColor = DominoGreen,
                focusedIndicatorColor = DominoGreen
            )
        )
        val filteredPlayers = if (filterText == (selectedPlayer?.displayName ?: "")) players
        else players.filter { it.displayName.contains(filterText, ignoreCase = true) }

        if (filteredPlayers.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filteredPlayers.forEach { player ->
                    DropdownMenuItem(
                        text = { Text(player.displayName) },
                        onClick = { onPlayerSelected(player); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreInputCard(state: MatchRegistrationState, viewModel: MatchViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreControl(
                p1 = state.selectedPlayers[0],
                p2 = state.selectedPlayers[1],
                score = state.score1,
                onScoreChange = { viewModel.onScoreChange(1, it) },
                accentColor = DominoGreen,
                modifier = Modifier.weight(1f)
            )
            Text("×", fontSize = 28.sp, color = DominoMuted, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
            ScoreControl(
                p1 = state.selectedPlayers[2],
                p2 = state.selectedPlayers[3],
                score = state.score2,
                onScoreChange = { viewModel.onScoreChange(2, it) },
                accentColor = DominoOrange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScoreControl(
    p1: User?,
    p2: User?,
    score: Int,
    onScoreChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Avatars Row
        Row(
            horizontalArrangement = Arrangement.spacedBy((-12).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.DarkGray, CircleShape)
            ) {
                if (p1 != null) {
                    AvatarImage(
                        url = p1.photoUrl,
                        size = 44.dp,
                        borderWidth = 1.5.dp,
                        borderColor = accentColor
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.DarkGray, CircleShape)
            ) {
                if (p2 != null) {
                    AvatarImage(
                        url = p2.photoUrl,
                        size = 44.dp,
                        borderWidth = 1.5.dp,
                        borderColor = accentColor
                    )
                }
            }
        }

        val name1 = p1?.displayName?.substringBefore(" ") ?: "Time"
        val name2 = p2?.displayName?.substringBefore(" ") ?: ""
        val label = if (name2.isNotEmpty()) "$name1 / $name2" else name1

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DominoMuted,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = { if (score > 0) onScoreChange(score - 1) },
                modifier = Modifier.size(36.dp).background(accentColor.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "-", tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Box(
                modifier = Modifier.size(60.dp).background(accentColor.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(score.toString(), fontSize = 36.sp, textAlign = TextAlign.Center, color = accentColor, fontWeight = FontWeight.Black)
            }
            IconButton(
                onClick = { onScoreChange(score + 1) },
                modifier = Modifier.size(36.dp).background(accentColor.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "+", tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OptionsCard(state: MatchRegistrationState, viewModel: MatchViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.isBuchoRe,
                onCheckedChange = { viewModel.onBuchoReChanged(it) },
                enabled = state.isBuchoReEnabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = DominoOrange,
                    uncheckedColor = DominoMuted,
                    checkmarkColor = Color.White
                )
            )
            Column {
                Text(
                    "🔥 Foi Bucho de Ré?",
                    color = if (state.isBuchoReEnabled) DominoOrange else DominoMuted,
                    fontWeight = if (state.isBuchoReEnabled) FontWeight.Bold else FontWeight.Normal
                )
                if (state.isBuchoReEnabled) {
                    Text("Marque se o placar foi 5 a mais!", color = DominoMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
