package com.marcioarruda.clubedodomino.ui.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.BestPlayer
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.ui.theme.*
import com.marcioarruda.clubedodomino.ui.util.AvatarImage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, userId: String, viewModel: DashboardViewModel) {
    LaunchedEffect(key1 = userId) { viewModel.loadDashboardData(userId) }

    val uiState by viewModel.uiState.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<Match?>(null) }
    var showCelebrationDialog by remember { mutableStateOf(true) }

    if (showProfileDialog && uiState.user != null) {
        ProfileDialog(
            user = uiState.user!!,
            onDismiss = { showProfileDialog = false },
            onImageSelected = { base64 -> viewModel.updateProfileImage(userId, base64) { showProfileDialog = false } },
            onLogout = { navController.navigate("login") { popUpTo(navController.graph.id) { inclusive = true } } }
        )
    }

    if (uiState.isLoading) {
        // Mesma tela do Splash (fundo verde, peça grande, textos) — evita a "piscada" de trocar
        // para o fundo creme da Dashboard só para mostrar um indicador de carregamento genérico,
        // já que o Splash e este primeiro carregamento acontecem em sequência imediata.
        // skipEntranceAnimation=true: esta tela é a CONTINUAÇÃO visual do Splash (outra instância
        // do mesmo composable), então já entra com opacidade total em vez de reiniciar o fade-in
        // do zero — é esse replay da animação, não a cor de fundo, que fazia parecer duas telas
        // piscando uma depois da outra.
        com.marcioarruda.clubedodomino.ui.ClubeDominoLoadingScreen(skipEntranceAnimation = true)
        return
    }

    Scaffold(
        containerColor = DominoBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // "Mesa de Dominó": warm cream background with a subtle gold radial glow
            // in the top-right corner (kept lightweight for Compose — a two-stop
            // radial brush rather than a full image-based gradient).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DominoBg)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(DominoYellow.copy(alpha = 0.10f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(x = Float.POSITIVE_INFINITY, y = 0f),
                            radius = 900f
                        )
                    )
            )
            when {
                uiState.error != null -> ErrorView(uiState.error!!) { viewModel.loadDashboardData(userId) }
                uiState.user != null -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.loadDashboardData(userId, isRefreshing = true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        DashboardContent(
                            state = uiState,
                            navController = navController,
                            userId = userId,
                            onAvatarClick = { showProfileDialog = true },
                            onMatchClick = { matchId -> selectedMatch = uiState.groupedMatches.values.flatten().find { it.id == matchId } }
                        )
                    }
                }
            }
            selectedMatch?.let { match ->
                MatchDetailsDialog(
                    match = match,
                    currentUserName = uiState.user?.name,
                    onDismiss = { selectedMatch = null },
                    onEdit = {
                        selectedMatch = null
                        navController.navigate("register_match?matchId=${match.id}")
                    }
                )
            }
            if (showCelebrationDialog && uiState.championCelebration != null) {
                ChampionCelebrationDialog(uiState.championCelebration!!) { showCelebrationDialog = false }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)) {
            Text("Tentar novamente", color = DominoOnDark)
        }
    }
}

@Composable
private fun ProfileDialog(user: User, onDismiss: () -> Unit, onImageSelected: (String) -> Unit, onLogout: () -> Unit) {
    var showReleaseNotes by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showAvatarSelector by remember { mutableStateOf(false) }

    if (showAvatarSelector) {
        AlertDialog(
            onDismissRequest = { showAvatarSelector = false },
            containerColor = DominoSurface,
            title = { Text("Escolha seu Avatar 🎲", color = DominoLight, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Escolha seu avatar — Sport, Náutico, Santa Cruz ou outro 🎲:", color = DominoMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    
                    val avatars = listOf(
                        "avatar_1", "avatar_2", "avatar_3",
                        "avatar_4", "avatar_5", "avatar_6",
                        "avatar_7", "avatar_8", "avatar_9",
                        "avatar_10", "avatar_11", "tenorio",
                        "avatar_13", "avatar_14", "avatar_15",
                        "tercio",
                        "arnaldo", "tatu", "calabria",
                        "sakaki", "breno", "molinho",
                        "ruan", "pedro", "marcio",
                        "amilton", "frodo", "geraldo", "emerson"
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        avatars.chunked(3).forEach { rowAvatars ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowAvatars.forEach { avatarId ->
                                    Box(
                                        modifier = Modifier
                                            .clickable {
                                                onImageSelected(avatarId)
                                                showAvatarSelector = false
                                            }
                                            .padding(4.dp)
                                    ) {
                                        AvatarImage(
                                            url = avatarId,
                                            size = 72.dp,
                                            borderWidth = if (user.photoUrl == avatarId) 3.dp else 1.dp,
                                            borderColor = if (user.photoUrl == avatarId) DominoGreen else Color.Gray
                                        )
                                    }
                                }
                                if (rowAvatars.size < 3) {
                                    for (i in 0 until (3 - rowAvatars.size)) {
                                        Spacer(modifier = Modifier.size(72.dp).padding(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarSelector = false }) {
                    Text("Cancelar", color = DominoError)
                }
            }
        )
    }

    if (showReleaseNotes) {
        AlertDialog(
            onDismissRequest = { showReleaseNotes = false },
            containerColor = DominoSurface,
            title = { Text("Notas da Versão", color = DominoLight) },
            text = {
                Column {
                    Text("Sua Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME} (${com.marcioarruda.clubedodomino.BuildConfig.VERSION_CODE})", fontWeight = FontWeight.Bold, color = DominoLight)
                    if (releaseInfo != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Última: v${releaseInfo?.second}", color = DominoGreen)
                        Spacer(Modifier.height(12.dp))
                        Text(releaseInfo?.third ?: "", color = DominoMuted)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = DominoGreen)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReleaseNotes = false }) { Text("Fechar", color = DominoGreen) } }
        )
    }

    LaunchedEffect(showReleaseNotes) {
        if (showReleaseNotes && releaseInfo == null) {
            val info = com.marcioarruda.clubedodomino.ui.util.UpdateManager.fetchLatestVersionInfo()
            releaseInfo = if (info != null) {
                Triple(com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME, info.versionName ?: info.versionCode.toString(), info.releaseNotes ?: "")
            } else {
                Triple(com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME, "Indisponível", "Erro ao buscar notas.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DominoSurface,
        title = { Text("Perfil do Jogador", color = DominoLight) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box {
                    AvatarImage(url = user.photoUrl, size = 120.dp, borderWidth = 3.dp)
                    IconButton(
                        onClick = { showAvatarSelector = true },
                        modifier = Modifier.align(Alignment.BottomEnd).background(DominoGreen, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Foto", tint = DominoOnDark, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DominoLight)
                Text(user.id, fontSize = 13.sp, color = DominoMuted)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showReleaseNotes = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DominoGreen),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Notas da Versão") }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)) {
                Text("Fechar", color = DominoOnDark)
            }
        },
        dismissButton = { TextButton(onClick = onLogout) { Text("Sair", color = DominoError) } }
    )
}

@Composable
private fun DashboardContent(state: DashboardUiState, navController: NavController, userId: String, onAvatarClick: () -> Unit, onMatchClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            TopBar(state.user!!, onAvatarClick)
        }

        if (state.bestPlayers.isNotEmpty() || state.worstPlayers.isNotEmpty()) {
            item { DailyAwardsRow(state.bestPlayers, state.worstPlayers) }
        }

        item { ShortcutsGrid(navController, userId) }

        item { StatsRow(state, navController, userId) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 20.dp).background(DominoGreen, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text("ÚLTIMAS PARTIDAS", style = MaterialTheme.typography.titleMedium, color = DominoLight, fontWeight = FontWeight.Bold)
            }
        }

        state.groupedMatches.forEach { (date, matches) ->
            item {
                Text(
                    text = "📅 $date",
                    style = MaterialTheme.typography.labelLarge,
                    color = DominoMuted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(matches) { MatchItem(it, userId, onMatchClick) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TopBar(user: User, onAvatarClick: () -> Unit) {
    val today = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(java.util.Date()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = today.replaceFirstChar { it.uppercase() },
                color = DominoMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            // TODO: fonte "Fraunces" (serifada) pendente — usando peso Black da fonte padrão
            // para simular destaque serifado até integrarmos Downloadable Fonts com segurança.
            Text(
                text = "Olá, ${user.name.split(" ").first()}",
                fontSize = 28.sp,
                color = DominoLight,
                fontWeight = FontWeight.Black
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DominoGreen)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            AvatarImage(url = user.photoUrl, size = 56.dp, borderWidth = 3.dp, borderColor = DominoYellow)
        }
    }
}

@Composable
private fun ShortcutsGrid(navController: NavController, userId: String) {
    val shortcuts = listOf(
        ShortcutItem("Jogar", "🎲") {
            navController.navigate("register_match")
        },
        ShortcutItem("Ranking", "🏅") {
            navController.navigate("ranking")
        },
        ShortcutItem("Finanças", "💳") {
            navController.navigate("finance/$userId")
        },
        ShortcutItem("Admin", "⚙️") {
            navController.navigate("admin")
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        shortcuts.forEach { shortcut ->
            ShortcutCard(shortcut, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShortcutCard(item: ShortcutItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .aspectRatio(0.85f)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(item.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                item.label,
                fontSize = 11.sp,
                color = DominoLight,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

data class ShortcutItem(val label: String, val emoji: String, val onClick: () -> Unit)

@Composable
private fun StatsRow(state: DashboardUiState, navController: NavController, userId: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PartidasNoMesCard(
            minhasPartidas = state.minhasPartidasNoMes,
            meta = state.metaPartidasNoMes,
            partidasHoje = state.totalMatchesToday,
            modifier = Modifier.weight(2f)
        )
        MeuDebitoCard(
            totalVencido = state.totalVencido,
            totalAVencer = state.totalAVencer,
            modifier = Modifier.weight(1f),
            onClick = { navController.navigate("finance/$userId") }
        )
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DominoLight,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = DominoMuted, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

// Card de participação: duas colunas lado a lado — "minhas partidas / meta" no mês (colorido
// conforme a proximidade da média: vermelho 0-50%, âmbar 50-99%, verde 100%+) e o total de
// partidas do dia, separadas por um divisor fino.
@Composable
private fun PartidasNoMesCard(minhasPartidas: Int, meta: Int, partidasHoje: Int, modifier: Modifier = Modifier) {
    val percentualDaMeta = if (meta > 0) minhasPartidas.toFloat() / meta else 1f
    val valueColor = when {
        percentualDaMeta >= 1f -> DominoGreen
        percentualDaMeta >= 0.5f -> DominoAmber
        else -> DominoError
    }
    Card(
        modifier = modifier.height(StatsCardHeight),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VOCÊ/CLUBE", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = DominoMuted, letterSpacing = 0.3.sp, maxLines = 1)
                Text("$minhasPartidas/$meta", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1)
                Spacer(Modifier.height(1.dp))
                Text("Partidas no mês", fontSize = 9.sp, color = DominoMuted, textAlign = TextAlign.Center, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(DominoMuted.copy(alpha = 0.15f))
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HOJE", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = DominoMuted, letterSpacing = 0.3.sp, maxLines = 1)
                Text(partidasHoje.toString(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = DominoLight, maxLines = 1)
                Spacer(Modifier.height(1.dp))
                Text("Partidas", fontSize = 9.sp, color = DominoMuted, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

// Altura fixa compartilhada pelos dois KPIs da StatsRow (Partidas e Débito), para garantir que
// fiquem com a mesma altura independente de quantas linhas de texto cada um tiver internamente —
// confiar que o conteúdo interno "coincidiria" por acaso não funcionou (Débito tem 4 linhas de
// texto, Partidas tem 3).
private val StatsCardHeight = 72.dp

// Card "Meu débito": mesma altura do card de Partidas, com o valor vencido e a vencer em duas
// linhas (em vez de um único total somado), para dar a mesma informação que já existe em
// Finanças sem precisar abrir a tela.
@Composable
private fun MeuDebitoCard(totalVencido: Double, totalAVencer: Double, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.height(StatsCardHeight).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("VENCIDO", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = DominoMuted, letterSpacing = 0.3.sp, maxLines = 1)
            Text(
                "R$ ${String.format(Locale("pt", "BR"), "%.2f", totalVencido)}",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = if (totalVencido > 0) DominoError else DominoGreen,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text("A VENCER", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = DominoMuted, letterSpacing = 0.3.sp, maxLines = 1)
            Text(
                "R$ ${String.format(Locale("pt", "BR"), "%.2f", totalAVencer)}",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = if (totalAVencer > 0) DominoAmber else DominoGreen,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DailyAwardsRow(bestPlayers: List<BestPlayer>, worstPlayers: List<BestPlayer>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (bestPlayers.isNotEmpty()) {
            AwardCard(
                modifier = Modifier.weight(1f),
                backgroundColor = DominoGreen,
                label = "🏆 CRAQUE DO DIA",
                labelColor = DominoYellow,
                player = bestPlayers[0]
            )
        }
        if (worstPlayers.isNotEmpty()) {
            AwardCard(
                modifier = Modifier.weight(1f),
                backgroundColor = DominoPiorBg,
                label = "🫠 PIORZINHO",
                labelColor = DominoPiorAccent,
                player = worstPlayers[0]
            )
        }
    }
}

@Composable
private fun AwardCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    label: String,
    labelColor: Color,
    player: BestPlayer,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, color = labelColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            // TODO: fonte "Fraunces" pendente — peso Black simula o destaque serifado por ora.
            Text(
                text = player.player.name.split(" ").first(),
                color = DominoOnDark,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DominoOnDark.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))
            // Pontos ganhos no dia é a métrica que de fato decide o craque/piorzinho — vitórias e
            // partidas aparecem como contexto dos critérios de desempate (vitórias, depois buchos
            // aplicados, depois partidas jogadas), não como a métrica principal.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("PONTOS NO DIA", color = DominoOnDarkMuted, fontSize = 9.sp, letterSpacing = 0.3.sp)
                    Text("${player.points}", color = labelColor, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VITÓRIAS", color = DominoOnDarkMuted, fontSize = 9.sp, letterSpacing = 0.3.sp)
                    Text("${player.wins}", color = DominoOnDark, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${player.wins}V–${player.matches - player.wins}D em ${player.matches} partidas",
                color = DominoOnDarkMuted,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MatchItem(match: Match, currentUserId: String, onMatchClick: (String) -> Unit) {
    val isTeam1Winner = match.score1 > match.score2
    val accentColor = if (match.wasBuchoRe) DominoOrange else DominoGreen.copy(alpha = 0.6f)
    val isCurrentUserInMatch = currentUserId.isNotBlank() && listOf(
        match.team1Player1, match.team1Player2, match.team2Player1, match.team2Player2
    ).any { it.id.equals(currentUserId, ignoreCase = true) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onMatchClick(match.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCurrentUserInMatch) DominoYellow.copy(alpha = 0.14f) else DominoSurface),
        border = if (isCurrentUserInMatch) BorderStroke(1.dp, DominoYellow.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent bar
            Box(modifier = Modifier.width(3.dp).height(40.dp).background(accentColor, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))

            // Team 1
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val team1Color = if (isTeam1Winner) DominoGreen else DominoOrange
                Text(match.team1Player1.displayName.substringBefore(" "), fontSize = 11.sp, color = team1Color, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
                Text(match.team1Player2.displayName.substringBefore(" "), fontSize = 11.sp, color = team1Color, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
            }

            // Score
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("${match.score1} × ${match.score2}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = DominoLight)
                if (match.wasBuchoRe) {
                    Text("🔥 BUCHO DE RÉ", fontSize = 8.sp, fontWeight = FontWeight.Black, color = DominoOrange)
                } else {
                    Text("${match.pts} pts", fontSize = 10.sp, color = DominoMuted)
                }
            }

            // Team 2
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val team2Color = if (!isTeam1Winner) DominoGreen else DominoOrange
                Text(match.team2Player1.displayName.substringBefore(" "), fontSize = 11.sp, color = team2Color, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (!isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
                Text(match.team2Player2.displayName.substringBefore(" "), fontSize = 11.sp, color = team2Color, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (!isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MatchDetailsDialog(
    match: Match,
    currentUserName: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    var canEdit by remember(match.id) { mutableStateOf(false) }

    val isOwner = currentUserName != null &&
        currentUserName.trim().equals(match.registeredBy.name.trim(), ignoreCase = true)

    LaunchedEffect(match.id, currentUserName) {
        canEdit = isOwner && com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager
            .canEditMatch(context, currentUserName, match.date)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = DominoGreen) } },
        dismissButton = {
            if (canEdit) {
                TextButton(onClick = onEdit) { Text("✏️ Editar", color = DominoOrange, fontWeight = FontWeight.Bold) }
            }
        },
        containerColor = DominoSurface,
        title = { Text("Detalhes da Partida", color = DominoLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Data", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(match.date))
                HorizontalDivider(color = DominoMuted.copy(alpha = 0.2f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time 1 ${if (match.score1 > match.score2) "🂓" else ""}", fontSize = 11.sp, color = DominoMuted)
                    Text("${match.team1Player1.name} / ${match.team1Player2.name}", color = if (match.score1 > match.score2) DominoGreen else DominoLight, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time 2 ${if (match.score2 > match.score1) "🂓" else ""}", fontSize = 11.sp, color = DominoMuted)
                    Text("${match.team2Player1.name} / ${match.team2Player2.name}", color = if (match.score2 > match.score1) DominoGreen else DominoLight, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = DominoMuted.copy(alpha = 0.2f))
                DetailRow("Placar Final", "${match.score1} × ${match.score2}", highlight = true)
                if (match.wasBuchoRe) DetailRow("Status", "🔥 BUCHO DE RÉ", highlight = true)
                DetailRow("Pontos", "${match.pts} pts")
                DetailRow("Registrado por", match.registeredBy.name)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DominoMuted, fontSize = 13.sp)
        Text(value, color = if (highlight) DominoGreen else DominoLight, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
    }
}

@Composable
fun ConfettiEffect(modifier: Modifier = Modifier) {
    val particles = remember {
        List(80) {
            ConfettiParticle(
                x = kotlin.random.Random.nextFloat(),
                y = kotlin.random.Random.nextFloat() * -1f,
                size = kotlin.random.Random.nextFloat() * 24f + 16f,
                color = listOf(
                    Color.Red, Color.Green, Color.Blue, Color.Yellow,
                    Color.Cyan, Color.Magenta, Color(0xFFFFA500)
                ).random(),
                speed = kotlin.random.Random.nextFloat() * 0.15f + 0.05f,
                rotationSpeed = kotlin.random.Random.nextFloat() * 360f,
                shape = kotlin.random.Random.nextInt(2)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val currentY = ((particle.y + progress * particle.speed * 10f) % 1.2f)
            val yPos = currentY * size.height
            val xPos = particle.x * size.width
            val rotation = progress * particle.rotationSpeed

            if (currentY > 0f) {
                rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(xPos, yPos)) {
                    if (particle.shape == 0) {
                        drawCircle(
                            color = particle.color,
                            radius = particle.size / 2,
                            center = androidx.compose.ui.geometry.Offset(xPos, yPos)
                        )
                    } else {
                        drawRect(
                            color = particle.color,
                            topLeft = androidx.compose.ui.geometry.Offset(xPos - particle.size / 2, yPos - particle.size / 2),
                            size = androidx.compose.ui.geometry.Size(particle.size, particle.size / 2)
                        )
                    }
                }
            }
        }
    }
}

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val speed: Float,
    val rotationSpeed: Float,
    val shape: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampionCelebrationDialog(
    celebration: com.marcioarruda.clubedodomino.data.ChampionCelebration,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Ignora clique fora */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        containerColor = DominoSurface,
        title = {
            Text(
                text = "🂓 CAMPEÃO DE ${celebration.monthName.uppercase()} 🂓",
                color = DominoGreen,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                ConfettiEffect(modifier = Modifier.fillMaxSize())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.size(140.dp)
                    ) {
                        AvatarImage(
                            url = celebration.player.photoUrl,
                            size = 120.dp,
                            borderWidth = 4.dp,
                            borderColor = DominoYellow
                        )
                        Text(
                            text = "👑",
                            fontSize = 32.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-20).dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = celebration.player.name,
                        color = DominoLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${celebration.points} pontos conquistados!",
                        color = DominoGreen,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DominoGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = "Reconhecer Campeão! 🤝",
                    color = DominoOnDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    )
}
