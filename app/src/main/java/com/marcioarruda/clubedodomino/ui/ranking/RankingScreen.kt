package com.marcioarruda.clubedodomino.ui.ranking

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.RankingPlayer
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.RoyalGold
import com.marcioarruda.clubedodomino.ui.util.LifecycleEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun base64ToBitmap(base64Str: String?): Bitmap? {
    if (base64Str.isNullOrBlank()) return null
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: IllegalArgumentException) {
        null
    }
}

@Composable
fun AvatarFromBase64(player: RankingPlayer) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(player.photoUrl) {
        withContext(Dispatchers.Default) {
            bitmap = base64ToBitmap(player.photoUrl)
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Avatar de ${player.playerName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = player.playerName.firstOrNull()?.uppercaseChar() ?: '?'
            Text(
                text = initial.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    navController: NavController,
    rankingViewModel: RankingViewModel = viewModel(factory = ViewModelFactory(ClubRepository()))
) {
    val uiState by rankingViewModel.uiState.collectAsState()

    LifecycleEffect {
        rankingViewModel.loadRanking()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ranking", color = RoyalGold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = RoyalGold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> RankingShimmerList()
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: "Erro desconhecido", color = Color.Red)
                    }
                }
                uiState.rankingList.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum dado disponível no momento.", color = Color.Gray)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(items = uiState.rankingList, key = { _, item -> item.playerName }) { index, player ->
                            RankingItem(player = player, position = index + 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RankingItem(player: RankingPlayer, position: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$position",
                    color = if (position <= 3) RoyalGold else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.width(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AvatarFromBase64(player = player)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = player.playerName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatColumn("Dia", player.dailyPoints, player.dailyMatches)
                StatColumn("Mês", player.monthlyPoints, player.monthlyMatches)
                StatColumn("Ano", player.yearlyPoints, player.yearlyMatches, highlight = true)
            }
        }
    }
}

@Composable
fun StatColumn(period: String, points: Int, matches: Int, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(period, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$points pts",
            color = if (highlight) RoyalGold else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "$matches jgs",
            color = Color.LightGray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun RankingShimmerList() {
    Column(modifier = Modifier.padding(16.dp)) {
        repeat(8) {
            ShimmerItem()
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ShimmerItem() {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.Gray.copy(alpha = 0.3f),
            Color.Gray.copy(alpha = 0.1f),
            Color.Gray.copy(alpha = 0.3f)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(brush, RoundedCornerShape(12.dp))
    ) {}
}
