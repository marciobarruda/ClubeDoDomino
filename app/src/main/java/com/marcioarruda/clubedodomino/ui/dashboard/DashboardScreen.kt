package com.marcioarruda.clubedodomino.ui.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import com.marcioarruda.clubedodomino.ui.theme.RoyalGold
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import com.marcioarruda.clubedodomino.ui.theme.GlassyColor
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import com.marcioarruda.clubedodomino.ui.theme.GlassyColor
import com.marcioarruda.clubedodomino.ui.util.AvatarImage
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    userId: String,
    viewModel: DashboardViewModel
) {
    LaunchedEffect(key1 = userId) {
        viewModel.loadDashboardData(userId)
    }

    val uiState by viewModel.uiState.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<Match?>(null) }

    if (showProfileDialog && uiState.user != null) {
        ProfileDialog(
            user = uiState.user!!,
            onDismiss = { showProfileDialog = false },
            onImageSelected = { base64 -> 
                viewModel.updateProfileImage(userId, base64) {
                    showProfileDialog = false
                }
            },
            onLogout = {
                // viewModel.logout()
                navController.navigate("login") {
                    popUpTo(navController.graph.id) {
                        inclusive = true
                    }
                }
            }
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
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = DominoGold
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDashboardData(userId) }) {
                            Text("Tentar novamente")
                        }
                    }
                }
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
                            onMatchClick = { matchId ->
                                selectedMatch = uiState.groupedMatches.values.flatten().find { it.id == matchId }
                            }
                        )
                    }
                }
            }
 
            selectedMatch?.let { match ->
                MatchDetailsDialog(match = match, onDismiss = { selectedMatch = null })
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    onImageSelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                onImageSelected(Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perfil do Jogador") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box {
                    AvatarImage(
                        url = user.photoUrl,
                        size = 120.dp,
                        borderWidth = 2.dp
                    )
                    IconButton(
                        onClick = { launcher.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(DominoGold, CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Foto", tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(user.id, fontSize = 14.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DominoGold)) {
                Text("Fechar", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onLogout) {
                Text("Sair", color = Color.Red)
            }
        }
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    navController: NavController,
    onAvatarClick: () -> Unit,
    onMatchClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            TopBar(state.user!!, onAvatarClick)
        }
        
        item {
            Button(
                onClick = { navController.navigate("admin") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ÁREA ADMINISTRATIVA", color = Color.White)
            }
        }

        if (state.bestPlayers.isNotEmpty() || state.worstPlayers.isNotEmpty()) {
            item {
                DailyAwardsCard(
                    bestPlayers = state.bestPlayers,
                    worstPlayers = state.worstPlayers
                )
            }
        }

        item {
            Text("ÚLTIMAS PARTIDAS", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }

        state.groupedMatches.forEach { (date, matches) ->
            item {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelLarge,
                    color = DominoGold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(matches) {
                MatchItem(it, onMatchClick)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopBar(user: User, onAvatarClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Olá, ${user.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        IconButton(onClick = onAvatarClick) {
             AvatarImage(
                url = user.photoUrl,
                size = 70.dp,
                borderWidth = 2.dp
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    navController: NavController,
    currentUserId: String,
    isNewMatchVisible: Boolean
) {
    val baseItems = listOf(
        BottomNavItem("Início", Icons.Default.Home, "dashboard/$currentUserId"),
        BottomNavItem("Financeiro", Icons.Default.MonetizationOn, "finance/$currentUserId"),
        BottomNavItem("Ranking", Icons.Default.BarChart, "ranking")
    )

    val allItems = remember(isNewMatchVisible) {
        if (isNewMatchVisible) {
            baseItems + BottomNavItem("Nova Partida", Icons.Default.Add, "register_match")
        } else {
            baseItems
        }
    }

    NavigationBar(containerColor = GlassyColor.copy(alpha = 0.2f)) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        allItems.forEach { item ->
            val isSelected = currentRoute?.startsWith(item.route.substringBefore('/')) == true

            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title, tint = Color.White) },
                label = { Text(item.title, color = Color.White, fontSize = 10.sp) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DominoGold,
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = DominoGold,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = GlassyColor
                )
            )
        }
    }
}

@Composable
private fun DailyAwardsCard(bestPlayers: List<BestPlayer>, worstPlayers: List<BestPlayer>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassyColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (bestPlayers.isNotEmpty()) {
                AwardSection(
                    title = "CRAQUE DO DIA",
                    players = bestPlayers,
                    icon = Icons.Default.EmojiEvents,
                    iconColor = DominoGold
                )
            }
            
            if (bestPlayers.isNotEmpty() && worstPlayers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (worstPlayers.isNotEmpty()) {
                AwardSection(
                    title = "PIOR DO DIA",
                    players = worstPlayers,
                    icon = Icons.Default.EmojiEvents,
                    iconColor = Color.Red
                )
            }
        }
    }
}

@Composable
private fun AwardSection(title: String, players: List<BestPlayer>, icon: ImageVector, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            val names = players.joinToString(", ") { it.player.name }
            Text(names, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (players.isNotEmpty()) {
                Text("${players[0].points} pontos hoje", color = DominoGold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MatchItem(match: Match, onMatchClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMatchClick(match.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassyColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Team 1
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.Center) {
                    AvatarImage(url = match.team1Player1.photoUrl, size = 36.dp, borderWidth = 1.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    AvatarImage(url = match.team1Player2.photoUrl, size = 36.dp, borderWidth = 1.dp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val t1n1 = match.team1Player1.name.substringBefore(" ")
                val t1n2 = match.team1Player2.name.substringBefore(" ")
                Text("$t1n1 / $t1n2", fontSize = 10.sp, color = Color.LightGray, textAlign = TextAlign.Center, maxLines = 1)
            }
            
            // Score and Status
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    "${match.score1} x ${match.score2}", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                if (match.wasBuchoRe) {
                    Text(
                        "🔥 BUCHO DE RÉ",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = DominoGold, // RoyalGold equivalent or DominoGold
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            // Team 2
             Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.Center) {
                    AvatarImage(url = match.team2Player1.photoUrl, size = 36.dp, borderWidth = 1.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    AvatarImage(url = match.team2Player2.photoUrl, size = 36.dp, borderWidth = 1.dp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val t2n1 = match.team2Player1.name.substringBefore(" ")
                val t2n2 = match.team2Player2.name.substringBefore(" ")
                Text("$t2n1 / $t2n2", fontSize = 10.sp, color = Color.LightGray, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MatchDetailsDialog(match: Match, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = RoyalGold)
            }
        },
        containerColor = Color(0xFF1E293B), // Slate 800
        title = { Text("Detalhes da Partida", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MatchDetailRow(label = "Data", value = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(match.date))
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Time 1 (Vencedor: ${if(match.score1 > match.score2) "Sim" else "Não"})", fontSize = 12.sp, color = Color.Gray)
                    Text("${match.team1Player1.name} / ${match.team1Player2.name}", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Time 2 (Vencedor: ${if(match.score2 > match.score1) "Sim" else "Não"})", fontSize = 12.sp, color = Color.Gray)
                    Text("${match.team2Player1.name} / ${match.team2Player2.name}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                MatchDetailRow(label = "Placar Final", value = "${match.score1} x ${match.score2}", isHighLight = true)
                if (match.wasBuchoRe) {
                    MatchDetailRow(label = "Status", value = "🔥 BUCHO DE RÉ", isHighLight = true)
                }
                MatchDetailRow(label = "Pontos Conquistados", value = "${match.pts} pts")
                MatchDetailRow(label = "Cadastrado por", value = match.registeredBy.name)
            }
        }
    )
}

@Composable
private fun MatchDetailRow(label: String, value: String, isHighLight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(
            value, 
            color = if (isHighLight) RoyalGold else Color.White, 
            fontWeight = if (isHighLight) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

data class BottomNavItem(val title: String, val icon: ImageVector, val route: String)
