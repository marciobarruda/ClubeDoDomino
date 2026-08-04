package com.marcioarruda.clubedodomino.ui.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.marcioarruda.clubedodomino.data.BestPlayer
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.data.User
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentUserId = userId,
                isNewMatchVisible = uiState.isNewMatchVisible
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Image(
                painter = painterResource(id = com.marcioarruda.clubedodomino.R.drawable.bg_dashboard),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.5f),
                                DominoBg.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DominoGreen)
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
                            onAvatarClick = { showProfileDialog = true },
                            onMatchClick = { matchId -> selectedMatch = uiState.groupedMatches.values.flatten().find { it.id == matchId } }
                        )
                    }
                }
            }
            selectedMatch?.let { MatchDetailsDialog(it) { selectedMatch = null } }
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
            Text("Tentar novamente", color = Color.Black)
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
                        Icon(Icons.Default.Edit, contentDescription = "Editar Foto", tint = Color.Black, modifier = Modifier.size(18.dp))
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
                Text("Fechar", color = Color.Black)
            }
        },
        dismissButton = { TextButton(onClick = onLogout) { Text("Sair", color = DominoError) } }
    )
}

@Composable
private fun DashboardContent(state: DashboardUiState, navController: NavController, onAvatarClick: () -> Unit, onMatchClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            TopBar(state.user!!, onAvatarClick)
        }

        item {
            Button(
                onClick = { navController.navigate("admin") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ÁREA ADMINISTRATIVA", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (state.bestPlayers.isNotEmpty() || state.worstPlayers.isNotEmpty()) {
            item { DailyAwardsCard(state.bestPlayers, state.worstPlayers) }
        }

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
                    color = DominoCyan,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(matches) { MatchItem(it, onMatchClick) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TopBar(user: User, onAvatarClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Olá, ${user.name.split(" ").first()} 👋", style = MaterialTheme.typography.headlineSmall, color = DominoLight, fontWeight = FontWeight.Bold)
            Text("Bora jogar!", color = DominoGreen, fontSize = 13.sp)
        }
        IconButton(onClick = onAvatarClick) {
            AvatarImage(url = user.photoUrl, size = 56.dp, borderWidth = 3.dp)
        }
    }
}

@Composable
private fun BottomNavigationBar(navController: NavController, currentUserId: String, isNewMatchVisible: Boolean) {
    val baseItems = listOf(
        BottomNavItem("Início", Icons.Default.Home, "dashboard/$currentUserId"),
        BottomNavItem("Finanças", Icons.Default.MonetizationOn, "finance/$currentUserId"),
        BottomNavItem("Ranking", Icons.Default.BarChart, "ranking")
    )
    val allItems = remember(isNewMatchVisible) {
        if (isNewMatchVisible) baseItems + BottomNavItem("Jogar!", Icons.Default.Add, "register_match")
        else baseItems
    }

    NavigationBar(
        containerColor = DominoSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        allItems.forEach { item ->
            val isSelected = currentRoute?.startsWith(item.route.substringBefore('/')) == true
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DominoGreen,
                    unselectedIconColor = DominoMuted,
                    selectedTextColor = DominoGreen,
                    unselectedTextColor = DominoMuted,
                    indicatorColor = DominoGreen.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
private fun DailyAwardsCard(bestPlayers: List<BestPlayer>, worstPlayers: List<BestPlayer>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF0C381E)))
                )
                .padding(16.dp)
        ) {
            Column {
                if (bestPlayers.isNotEmpty()) {
                    AwardSection(
                        title = "🂓 CRAQUE DO DIA",
                        players = bestPlayers,
                        nameColor = DominoYellow,
                        pointsColor = DominoGreen
                    )
                }
                if (bestPlayers.isNotEmpty() && worstPlayers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))
                }
                if (worstPlayers.isNotEmpty()) {
                    AwardSection(
                        title = "😬 PIORZINHO",
                        players = worstPlayers,
                        nameColor = DominoOrange,
                        pointsColor = DominoOrange.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AwardSection(title: String, players: List<BestPlayer>, nameColor: Color, pointsColor: Color) {
    Column {
        Text(title, color = DominoMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        val names = players.joinToString(", ") { it.player.name.split(" ").first() }
        Text(names, color = nameColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
        if (players.isNotEmpty()) {
            val p = players[0]
            val winRatePct = (p.winRate * 100).toInt()
            Text("$winRatePct% aproveitamento (${p.wins}/${p.matches}) · ${p.points} pontos hoje", color = pointsColor, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MatchItem(match: Match, onMatchClick: (String) -> Unit) {
    val isTeam1Winner = match.score1 > match.score2
    val accentColor = if (match.wasBuchoRe) DominoOrange else DominoGreen.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onMatchClick(match.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface)
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
                Text(match.team1Player1.displayName.substringBefore(" "), fontSize = 11.sp, color = if (isTeam1Winner) DominoGreen else DominoMuted, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
                Text(match.team1Player2.displayName.substringBefore(" "), fontSize = 11.sp, color = if (isTeam1Winner) DominoGreen else DominoMuted, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
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
                Text(match.team2Player1.displayName.substringBefore(" "), fontSize = 11.sp, color = if (!isTeam1Winner) DominoGreen else DominoMuted, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (!isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
                Text(match.team2Player2.displayName.substringBefore(" "), fontSize = 11.sp, color = if (!isTeam1Winner) DominoGreen else DominoMuted, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (!isTeam1Winner) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MatchDetailsDialog(match: Match, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = DominoGreen) } },
        containerColor = DominoSurface,
        title = { Text("Detalhes da Partida", color = DominoLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Data", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(match.date))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time 1 ${if (match.score1 > match.score2) "🂓" else ""}", fontSize = 11.sp, color = DominoMuted)
                    Text("${match.team1Player1.name} / ${match.team1Player2.name}", color = if (match.score1 > match.score2) DominoGreen else DominoLight, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time 2 ${if (match.score2 > match.score1) "🂓" else ""}", fontSize = 11.sp, color = DominoMuted)
                    Text("${match.team2Player1.name} / ${match.team2Player2.name}", color = if (match.score2 > match.score1) DominoGreen else DominoLight, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
                color = RoyalGold,
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
                            borderColor = RoyalGold
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
                        color = Color.White,
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
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    )
}

data class BottomNavItem(val title: String, val icon: ImageVector, val route: String)
