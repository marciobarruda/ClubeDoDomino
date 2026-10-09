package com.marcioarruda.clubedodomino.ui.ranking

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.RankingPlayer
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.DominoBg
import com.marcioarruda.clubedodomino.ui.theme.DominoCyan
import com.marcioarruda.clubedodomino.ui.theme.DominoGreen
import com.marcioarruda.clubedodomino.ui.theme.DominoLight
import com.marcioarruda.clubedodomino.ui.theme.DominoMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOnDark
import com.marcioarruda.clubedodomino.ui.theme.DominoOnDarkMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOrange
import com.marcioarruda.clubedodomino.ui.theme.DominoSurface
import com.marcioarruda.clubedodomino.ui.theme.DominoYellow
import com.marcioarruda.clubedodomino.ui.util.AvatarImage
import com.marcioarruda.clubedodomino.ui.util.LifecycleEffect

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
    AvatarImage(
        url = player.photoUrl,
        size = 48.dp
    )
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
                title = {
                    Text(
                        "Ranking do mês",
                        color = DominoGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGreen)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = DominoBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DominoBg)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> RankingShimmerList()
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: "Erro desconhecido", color = DominoOrange)
                    }
                }
                uiState.rankingList.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum dado disponível no momento.", color = DominoMuted)
                    }
                }
                else -> {
                    val podium = uiState.rankingList.take(3)
                    val rest = uiState.rankingList.drop(3)

                    // Jogador exibido no dialog de detalhes ao clicar em qualquer card da lista
                    // (pódio ou "classificação geral") — null quando o dialog está fechado.
                    var detailsPlayer by remember { mutableStateOf<RankingPlayer?>(null) }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (podium.isNotEmpty()) {
                            item {
                                PodiumSection(
                                    podium = podium,
                                    selectedPlayerName = detailsPlayer?.playerName,
                                    onPlayerClick = { detailsPlayer = it }
                                )
                            }
                        }
                        if (rest.isNotEmpty()) {
                            item {
                                Text(
                                    text = "CLASSIFICAÇÃO GERAL",
                                    color = DominoMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            }
                            itemsIndexed(items = rest, key = { _, item -> item.playerName }) { index, player ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    RankingRow(player = player, position = index + 4, onClick = { detailsPlayer = player })
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    detailsPlayer?.let { player ->
                        PlayerDetailsDialog(player = player, onDismiss = { detailsPlayer = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumSection(
    podium: List<RankingPlayer>,
    selectedPlayerName: String?,
    onPlayerClick: (RankingPlayer) -> Unit
) {
    val first = podium.getOrNull(0)
    val second = podium.getOrNull(1)
    val third = podium.getOrNull(2)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (second != null) {
            PodiumSlot(
                player = second,
                position = 2,
                isSelected = second.playerName == selectedPlayerName,
                onClick = { onPlayerClick(second) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (first != null) {
            PodiumSlot(
                player = first,
                position = 1,
                isSelected = first.playerName == selectedPlayerName,
                onClick = { onPlayerClick(first) },
                modifier = Modifier.weight(1.15f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1.15f))
        }
        if (third != null) {
            PodiumSlot(
                player = third,
                position = 3,
                isSelected = third.playerName == selectedPlayerName,
                onClick = { onPlayerClick(third) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PodiumSlot(
    player: RankingPlayer,
    position: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarSize = if (position == 1) 72.dp else 56.dp
    val borderColor = when (position) {
        1 -> DominoYellow
        2 -> DominoMuted.copy(alpha = 0.5f)
        else -> DominoYellow.copy(alpha = 0.45f)
    }
    val barColor = when (position) {
        1 -> DominoGreen
        2 -> Color(0xFFE7E2D4)
        else -> Color(0xFFF2EADB)
    }
    val barTextColor = when (position) {
        1 -> DominoYellow
        2 -> DominoMuted
        else -> DominoMuted
    }
    val barHeight = when (position) {
        1 -> 76.dp
        2 -> 56.dp
        else -> 44.dp
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, DominoYellow, RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (position == 1) {
            Text("👑", fontSize = 22.sp)
            Spacer(modifier = Modifier.height(2.dp))
        }
        AvatarImage(
            url = player.photoUrl,
            size = avatarSize,
            borderColor = borderColor,
            borderWidth = if (position == 1) 3.dp else 2.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = player.playerName.substringBefore(" "),
            color = DominoLight,
            fontWeight = FontWeight.Bold,
            fontSize = if (position == 1) 14.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(barColor, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "${position}º",
                color = barTextColor,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

// Dialog de detalhes de um jogador — acionado ao clicar em qualquer card do ranking (pódio ou
// lista geral). Fecha tocando no X ou fora do card (comportamento padrão de Dialog do Compose).
@Composable
fun PlayerDetailsDialog(player: RankingPlayer, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        PlayerDetailsCard(player = player, onClose = onDismiss)
    }
}

@Composable
fun PlayerDetailsCard(player: RankingPlayer, onClose: (() -> Unit)? = null) {
    val leader = player
    val totalDecided = leader.yearlyWins + leader.yearlyLosses
    val winRatePct = if (totalDecided > 0) (leader.yearlyWins * 100 / totalDecided) else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (onClose != null) 0.dp else 16.dp, vertical = if (onClose != null) 0.dp else 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DominoGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = leader.playerName,
                    color = DominoOnDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = DominoOnDarkMuted)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LeaderMetric(label = "PONTOS NO MÊS", value = "${leader.monthlyPoints}")
                VerticalDivider()
                LeaderMetric(label = "PARTIDAS", value = "${leader.monthlyMatches}")
                VerticalDivider()
                LeaderMetric(label = "APROVEITAMENTO ANUAL", value = "$winRatePct%")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(DominoOnDark.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(vertical = 12.dp, horizontal = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "BUCHOS APLICADOS / SOFRIDOS",
                        color = DominoOnDarkMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BuchoKpi(label = "HOJE", applied = leader.dailyBuchosApplied, received = leader.dailyBuchosReceived)
                        BuchoKpi(label = "MÊS", applied = leader.monthlyBuchosApplied, received = leader.monthlyBuchosReceived)
                        BuchoKpi(label = "ANO", applied = leader.yearlyBuchosApplied, received = leader.yearlyBuchosReceived)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuchoKpi(label: String, applied: Int, received: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = DominoOnDarkMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$applied",
                color = DominoYellow,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
            Text(
                text = " / ",
                color = DominoOnDarkMuted,
                fontSize = 12.sp
            )
            Text(
                text = "$received",
                color = DominoOrange,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(DominoOnDark.copy(alpha = 0.15f))
    )
}

@Composable
private fun LeaderMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = DominoOnDarkMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            color = DominoYellow,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp
        )
    }
}

@Composable
fun RankingRow(player: RankingPlayer, position: Int, onClick: (() -> Unit)? = null) {
    val balance = player.monthlyPoints
    val isNegative = balance < 0
    val borderColor = if (isNegative && kotlin.math.abs(balance) >= 10) {
        DominoOrange.copy(alpha = 0.4f)
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$position",
                color = DominoMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(24.dp)
            )
            AvatarImage(url = player.photoUrl, size = 40.dp, borderWidth = 1.dp, borderColor = DominoMuted.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.playerName,
                    color = DominoLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${player.monthlyMatches} partidas · ${player.yearlyWins} vitórias",
                    color = DominoMuted,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${if (balance >= 0) "+" else ""}$balance pts",
                color = if (balance >= 0) DominoCyan else DominoOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun RankingShimmerList() {
    Column(modifier = Modifier.padding(16.dp)) {
        repeat(6) {
            ShimmerItem()
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ShimmerItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(DominoMuted.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
    ) {}
}
