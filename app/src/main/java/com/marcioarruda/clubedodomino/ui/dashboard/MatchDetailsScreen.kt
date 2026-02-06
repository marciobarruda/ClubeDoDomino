package com.marcioarruda.clubedodomino.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.Match
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import com.marcioarruda.clubedodomino.ui.theme.GlassyColor
import com.marcioarruda.clubedodomino.ui.util.LifecycleEffect
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailsScreen(
    navController: NavController,
    matchId: String,
    viewModel: MatchDetailsViewModel = viewModel(factory = ViewModelFactory(ClubRepository()))
) {
    LifecycleEffect {
        viewModel.loadMatch(matchId)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Detalhes da Partida", color = DominoGold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when (val state = uiState) {
                is MatchDetailsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is MatchDetailsUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                is MatchDetailsUiState.Success -> {
                    MatchDetailsContent(match = state.match)
                }
            }
        }
    }
}

@Composable
fun MatchDetailsContent(match: Match) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    val team1Names = listOf(match.team1Player1.displayName, match.team1Player2.displayName).sorted()
    val team2Names = listOf(match.team2Player1.displayName, match.team2Player2.displayName).sorted()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GlassyColor)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val dateStr = try { dateFormat.format(match.date) } catch (e: Exception) { "Data inválida" }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TeamColumn(team1Names[0], team1Names[1], match.score1)
                    Text("X", fontSize = 32.sp, color = DominoGold, fontWeight = FontWeight.Bold)
                    TeamColumn(team2Names[0], team2Names[1], match.score2)
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (match.wasBuchoRe) {
                    Text("BUCHO DE RÉ!", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                } else if (match.score1 == 0 || match.score2 == 0) {
                    Text("BUCHO!", color = DominoGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                val registeredByName = try { match.registeredBy.displayName } catch(e: Exception) { "Desconhecido" }

                Text(
                    text = "Registrado por: $registeredByName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun TeamColumn(p1Name: String, p2Name: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p1Name, color = Color.White, fontWeight = FontWeight.Bold)
        Text(p2Name, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(score.toString(), fontSize = 48.sp, color = DominoGold, fontWeight = FontWeight.Bold)
    }
}
