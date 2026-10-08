package com.marcioarruda.clubedodomino.ui.register

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.ui.theme.*
import com.marcioarruda.clubedodomino.ui.util.AvatarImage

// Bege claro para os "slots" de jogador e borda tracejada areia (paleta "Mesa de Dominó").
private val SlotBg = Color(0xFFF5EDD6)
private val SlotBorder = Color(0xFFCBB088)
private val Team2AvatarColor = Color(0xFF3A2A1E)

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

    if (state.showBatidaDialogForTeam != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissBatidaDialog() },
            containerColor = DominoSurface,
            title = { Text("🎯 Tipo de Batida", color = DominoGreen, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Fechas acumuladas: ${state.fechas}", color = DominoLight)
                    Spacer(Modifier.height(12.dp))
                    TipoBatida.entries.forEach { tipo ->
                        OutlinedButton(
                            onClick = { viewModel.onBatidaSelected(tipo) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DominoGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("${tipo.label} (${tipo.pontos} ${if (tipo.pontos == 1) "ponto" else "pontos"})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { viewModel.onDismissBatidaDialog() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = DominoMuted), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (state.showRepeatDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DominoSurface,
            title = { Text("⚡ Partida Salva!", color = DominoGreen, fontWeight = FontWeight.Black) },
            text = { Text("Repetir com os mesmos jogadores? As duplas serão sorteadas novamente.", color = DominoLight) },
            confirmButton = {
                Button(onClick = { viewModel.onRepeatMatch(true) }, colors = ButtonDefaults.buttonColors(containerColor = DominoYellow), shape = RoundedCornerShape(12.dp)) {
                    Text("Sim, vamos!", color = DominoGreen, fontWeight = FontWeight.Bold)
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
        containerColor = DominoBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.editingMatchId != null) "Editar Partida" else "Nova Partida",
                        color = DominoGreen,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGreen)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DominoBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DominoBg)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.remainingSecondsToClose != null) {
                    val mins = state.remainingSecondsToClose!! / 60
                    val secs = state.remainingSecondsToClose!! % 60
                    val timeStr = String.format("%02d:%02d", mins, secs)

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DominoError.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Warning, contentDescription = "Atenção", tint = DominoError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fechamento em: $timeStr",
                                color = DominoError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                val isEditing = state.editingMatchId != null
                val isGameplayEnabled = isEditing || state.isActiveMatchStarted

                PlayerSelectionCard(state, viewModel, isEditing || !state.isActiveMatchStarted)
                Spacer(Modifier.height(16.dp))
                ScoreInputCard(state, viewModel, isGameplayEnabled)
                Spacer(Modifier.height(16.dp))
                OptionsCard(state, viewModel, isGameplayEnabled)
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

                if (!isEditing && !state.isActiveMatchStarted) {
                    Button(
                        onClick = { viewModel.startMatch() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isModuleAvailable) DominoYellow else DominoMuted,
                            disabledContainerColor = DominoMuted
                        ),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !state.isLoading && state.isModuleAvailable
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DominoGreen)
                        } else {
                            Text("🎲 Confirmar Abertura da Partida", color = DominoGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (isEditing) {
                                viewModel.updateMatch(state.editingMatchId!!)
                            } else {
                                val currentUser = state.availablePlayers.find { it.id == session?.userEmail }
                                    ?: state.availablePlayers.firstOrNull()
                                    ?: User("0", "User", "User", "", "c1")
                                viewModel.saveMatch(registeredBy = currentUser)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isModuleAvailable) DominoYellow else DominoMuted,
                            disabledContainerColor = DominoMuted
                        ),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !state.isLoading && state.isModuleAvailable
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DominoGreen)
                        } else if (!state.isModuleAvailable) {
                            Text("⏰ Fora do Horário", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        } else {
                            Text(if (isEditing) "✅ Atualizar Partida" else "🎮 Salvar Partida", color = DominoGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }

                    if (!isEditing && state.isActiveMatchStarted) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelActiveMatch() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DominoOrange),
                            border = BorderStroke(1.5.dp, DominoOrange),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            enabled = !state.isLoading
                        ) {
                            Text("❌ Cancelar Partida", color = DominoOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelectionCard(state: MatchRegistrationState, viewModel: MatchViewModel, enabled: Boolean) {
    val isEditing = state.editingMatchId != null
    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Mostra os times já definidos (Time 1/Time 2) quando a partida está em edição ou já
            // foi aberta (duplas já sorteadas em startMatch()). Antes de abrir, mostra os 4 campos
            // soltos, já que a formação das duplas só é decidida ao confirmar a abertura.
            val showTeams = isEditing || state.isActiveMatchStarted
            if (showTeams) {
                TeamHeader("TIME 1", DominoGreen)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p1List = state.availablePlayers.filter { it == state.selectedPlayers[0] || it !in state.selectedPlayers }
                    val p2List = state.availablePlayers.filter { it == state.selectedPlayers[1] || it !in state.selectedPlayers }
                    PlayerSlot(p1List, state.selectedPlayers[0], { viewModel.onPlayerSelected(0, it) }, DominoGreen, Modifier.weight(1f), enabled)
                    PlayerSlot(p2List, state.selectedPlayers[1], { viewModel.onPlayerSelected(1, it) }, DominoGreen, Modifier.weight(1f), enabled)
                }
                Spacer(Modifier.height(4.dp))
                VsPill()
                Spacer(Modifier.height(4.dp))
                TeamHeader("TIME 2", DominoOrange)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p3List = state.availablePlayers.filter { it == state.selectedPlayers[2] || it !in state.selectedPlayers }
                    val p4List = state.availablePlayers.filter { it == state.selectedPlayers[3] || it !in state.selectedPlayers }
                    PlayerSlot(p3List, state.selectedPlayers[2], { viewModel.onPlayerSelected(2, it) }, Team2AvatarColor, Modifier.weight(1f), enabled)
                    PlayerSlot(p4List, state.selectedPlayers[3], { viewModel.onPlayerSelected(3, it) }, Team2AvatarColor, Modifier.weight(1f), enabled)
                }
            } else {
                TeamHeader("JOGADORES", DominoGreen)
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlotBg),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎲", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "As duplas serão sorteadas ao confirmar a abertura",
                            color = DominoMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p1List = state.availablePlayers.filter { it == state.selectedPlayers[0] || it !in state.selectedPlayers }
                    val p2List = state.availablePlayers.filter { it == state.selectedPlayers[1] || it !in state.selectedPlayers }
                    PlayerSlot(p1List, state.selectedPlayers[0], { viewModel.onPlayerSelected(0, it) }, DominoGreen, Modifier.weight(1f), enabled)
                    PlayerSlot(p2List, state.selectedPlayers[1], { viewModel.onPlayerSelected(1, it) }, DominoGreen, Modifier.weight(1f), enabled)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p3List = state.availablePlayers.filter { it == state.selectedPlayers[2] || it !in state.selectedPlayers }
                    val p4List = state.availablePlayers.filter { it == state.selectedPlayers[3] || it !in state.selectedPlayers }
                    PlayerSlot(p3List, state.selectedPlayers[2], { viewModel.onPlayerSelected(2, it) }, Team2AvatarColor, Modifier.weight(1f), enabled)
                    PlayerSlot(p4List, state.selectedPlayers[3], { viewModel.onPlayerSelected(3, it) }, Team2AvatarColor, Modifier.weight(1f), enabled)
                }
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

/** Pílula "VS" centralizada entre os dois times: fundo verde-escuro, texto dourado. */
@Composable
private fun VsPill() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .background(DominoGreen, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                "VS",
                color = DominoYellow,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * "Slot" de seleção de jogador: fundo bege claro, borda tracejada areia, avatar circular
 * (cor passada via [avatarColor] para diferenciar Time 1 / Time 2) + dropdown de seleção.
 * Mantém o PlayerDropdown existente (mesma lógica de filtro/seleção), apenas reestilizado
 * visualmente dentro do slot.
 */
@Composable
private fun PlayerSlot(
    players: List<User>,
    selectedPlayer: User?,
    onPlayerSelected: (User) -> Unit,
    avatarColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val dashColor = SlotBorder
    Column(
        modifier = modifier
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                val radius = 14.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(radius, radius),
                    style = stroke
                )
            }
            .background(SlotBg, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selectedPlayer != null) {
                    AvatarImage(url = selectedPlayer.photoUrl, size = 32.dp, borderWidth = 0.dp, borderColor = Color.Transparent)
                } else {
                    Text("?", color = DominoOnDark, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                selectedPlayer?.displayName?.substringBefore(" ") ?: "Selecionar",
                color = if (selectedPlayer != null) DominoLight else DominoMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        PlayerDropdown(players, selectedPlayer, onPlayerSelected, Modifier.fillMaxWidth(), enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDropdown(
    players: List<User>,
    selectedPlayer: User?,
    onPlayerSelected: (User) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember(selectedPlayer) { mutableStateOf(selectedPlayer?.displayName ?: "") }

    // Exibição: quando não está sendo filtrado/editado, mostra apenas o primeiro nome para evitar quebra de linha.
    val displayText = if (!expanded && filterText == (selectedPlayer?.displayName ?: "")) {
        selectedPlayer?.displayName?.substringBefore(" ") ?: filterText
    } else {
        filterText
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = modifier) {
        TextField(
            value = displayText,
            onValueChange = { if (enabled) { filterText = it; expanded = true } },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            label = { Text("Jogador", fontSize = 11.sp, maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = DominoLight,
                unfocusedTextColor = DominoLight,
                focusedLabelColor = DominoGreen,
                focusedIndicatorColor = DominoGreen,
                unfocusedIndicatorColor = SlotBorder
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
private fun ScoreInputCard(state: MatchRegistrationState, viewModel: MatchViewModel, enabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreControl(
                    label = "TIME 1",
                    p1 = state.selectedPlayers[0],
                    p2 = state.selectedPlayers[1],
                    score = state.score1,
                    onDecrement = { viewModel.onScoreChange(1, (state.score1 - 1).coerceAtLeast(0)) },
                    onIncrement = { viewModel.onScoreIncrement(1) },
                    accentColor = DominoGreen,
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                )
                Text("×", fontSize = 24.sp, color = Color(0xFFCBB088), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
                ScoreControl(
                    label = "TIME 2",
                    p1 = state.selectedPlayers[2],
                    p2 = state.selectedPlayers[3],
                    score = state.score2,
                    onDecrement = { viewModel.onScoreChange(2, (state.score2 - 1).coerceAtLeast(0)) },
                    onIncrement = { viewModel.onScoreIncrement(2) },
                    accentColor = DominoOrange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                )
            }
            HorizontalDivider(color = DominoMuted.copy(alpha = 0.15f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fechas:", color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = { viewModel.onFechasChange(state.fechas - 1) },
                    enabled = enabled,
                    modifier = Modifier.size(32.dp).background(if (enabled) DominoMuted.copy(alpha = 0.15f) else Color.Transparent, CircleShape)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-", tint = if (enabled) DominoLight else DominoMuted, modifier = Modifier.size(16.dp))
                }
                Text(
                    state.fechas.toString(),
                    fontSize = 20.sp,
                    color = DominoLight,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick = { viewModel.onFechasChange(state.fechas + 1) },
                    enabled = enabled,
                    modifier = Modifier.size(32.dp).background(if (enabled) DominoMuted.copy(alpha = 0.15f) else Color.Transparent, CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+", tint = if (enabled) DominoLight else DominoMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ScoreControl(
    label: String,
    p1: User?,
    p2: User?,
    score: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = DominoMuted,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )

        // Avatars Row
        Row(
            horizontalArrangement = Arrangement.spacedBy((-12).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.DarkGray, CircleShape)
            ) {
                if (p1 != null) {
                    AvatarImage(
                        url = p1.photoUrl,
                        size = 40.dp,
                        borderWidth = 1.5.dp,
                        borderColor = DominoSurface
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.DarkGray, CircleShape)
            ) {
                if (p2 != null) {
                    AvatarImage(
                        url = p2.photoUrl,
                        size = 40.dp,
                        borderWidth = 1.5.dp,
                        borderColor = DominoSurface
                    )
                }
            }
        }

        val name1 = p1?.displayName?.substringBefore(" ") ?: "Time"
        val name2 = p2?.displayName?.substringBefore(" ") ?: ""
        val nameLabel = if (name2.isNotEmpty()) "$name1 / $name2" else name1

        Text(
            text = nameLabel,
            style = MaterialTheme.typography.labelMedium,
            color = DominoMuted,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = { if (score > 0) onDecrement() },
                enabled = enabled,
                modifier = Modifier.size(32.dp).background(if (enabled) accentColor.copy(alpha = 0.1f) else Color.Transparent, CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "-", tint = if (enabled) accentColor else DominoMuted, modifier = Modifier.size(16.dp))
            }
            Text(
                score.toString(),
                fontFamily = FontFamily.Serif,
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
                color = if (enabled) DominoLight else DominoMuted,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(56.dp)
            )
            IconButton(
                onClick = onIncrement,
                enabled = enabled,
                modifier = Modifier.size(32.dp).background(if (enabled) accentColor.copy(alpha = 0.1f) else Color.Transparent, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "+", tint = if (enabled) accentColor else DominoMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun OptionsCard(state: MatchRegistrationState, viewModel: MatchViewModel, enabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.isBuchoRe,
                onCheckedChange = { viewModel.onBuchoReChanged(it) },
                enabled = state.isBuchoReEnabled && enabled,
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
