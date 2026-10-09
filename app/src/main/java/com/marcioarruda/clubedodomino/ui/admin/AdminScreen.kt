package com.marcioarruda.clubedodomino.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcioarruda.clubedodomino.data.FinancialEntryType
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.DominoBg
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import com.marcioarruda.clubedodomino.ui.theme.DominoGreen
import com.marcioarruda.clubedodomino.ui.theme.DominoLight
import com.marcioarruda.clubedodomino.ui.theme.DominoMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOrange
import com.marcioarruda.clubedodomino.ui.theme.DominoSurface
import com.marcioarruda.clubedodomino.ui.util.AvatarImage
import androidx.compose.ui.platform.LocalContext
import com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.marcioarruda.clubedodomino.data.network.ComprovanteHistoricoDto

// Borda sutil para itens de alerta/pendência (mensalidade vencida, bucho não pago)
private val AlertBorderColor = Color(0xFFD1573F).copy(alpha = 0.27f) // DominoOrange ~ #D1573F44

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

    // Determine permissions
    val userName = session?.userName?.trim() ?: ""
    val canEdit = userName.equals("MÁRCIO", ignoreCase = true) || userName.equals("CALÁBRIA", ignoreCase = true)
    val isMarcioTab = userName.equals("MÁRCIO", ignoreCase = true)

    val tabs = if (isMarcioTab) {
        listOf("Partidas", "Buchos", "Mensalidades", "Jogadores", "Inadimplentes", "Comprovantes")
    } else {
        listOf("Partidas", "Buchos", "Mensalidades", "Jogadores", "Inadimplentes")
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    var showReleaseNotes by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) } // Local, Server, Notes
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showDbPasswordDialog by remember { mutableStateOf(false) }
    var buchoPlayerFilter by remember { mutableStateOf("Todos os Jogadores") }
    var mensalidadePlayerFilter by remember { mutableStateOf("Todos os Jogadores") }

    if (showDbPasswordDialog) {
        UpdateDbPasswordDialog(
            onDismiss = { showDbPasswordDialog = false },
            onConfirm = { senhaLogin, novaSenha ->
                viewModel.updateDbPassword(session?.userEmail ?: "", senhaLogin, novaSenha)
                showDbPasswordDialog = false
            },
            isLoading = uiState.isUpdatingDbPassword
        )
    }

    if (showAddPlayerDialog) {
        AddPlayerDialog(
            onDismiss = { showAddPlayerDialog = false },
            onConfirm = { name, email, password, avatarId, billingStart ->
                viewModel.createPlayer(name, email, password, avatarId, billingStart)
                showAddPlayerDialog = false
            },
            isLoading = uiState.isCreatingPlayer
        )
    }

    if (showReleaseNotes) {
        AlertDialog(
            onDismissRequest = { showReleaseNotes = false },
            containerColor = DominoSurface,
            title = { Text("Notas da Versão", color = DominoLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Sua Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME} (${com.marcioarruda.clubedodomino.BuildConfig.VERSION_CODE})", fontWeight = FontWeight.Bold, color = DominoLight)
                    if (releaseInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Última disponível: v${releaseInfo?.second}", color = DominoGreen, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("O que mudou:", fontWeight = FontWeight.Medium, color = DominoLight)
                        Text(releaseInfo?.third ?: "Nenhuma nota disponível.", color = DominoMuted)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(androidx.compose.ui.Alignment.CenterHorizontally), color = DominoGreen)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseNotes = false }) { Text("Fechar", color = DominoGreen) }
            }
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

    if (uiState.message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            containerColor = DominoSurface,
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK", color = DominoGreen) } },
            text = { Text(uiState.message!!, color = DominoLight) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Administração",
                        color = DominoGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DominoBg,
                    titleContentColor = DominoGreen
                )
            )
        },
        containerColor = DominoBg
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            val isMarcio = userName.equals("MÁRCIO", ignoreCase = true)
            if (isMarcio) {
                val context = LocalContext.current
                var bypassEnabled by remember {
                    mutableStateOf(MatchAvailabilityManager.getBypassEnabled(context))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(1.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DominoSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liberar Horário de Cadastro",
                                color = DominoLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Permite que você cadastre partidas fora do horário padrão (11h45 às 14h)",
                                color = DominoMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = bypassEnabled,
                            onCheckedChange = { checked ->
                                MatchAvailabilityManager.setBypassEnabled(context, checked)
                                bypassEnabled = checked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DominoGreen,
                                checkedTrackColor = DominoGreen.copy(alpha = 0.5f)
                            )
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE6DAB8), modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(
                        onClick = { showAddPlayerDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DominoGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Cadastrar Novo Jogador", color = DominoGreen, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Color(0xFFE6DAB8), modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(
                        onClick = { showDbPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = DominoMuted)
                        Spacer(Modifier.width(8.dp))
                        Text("Alterar Senha do Banco de Dados", color = DominoMuted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Abas em formato de "pills" roláveis horizontalmente
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs.size) { index ->
                    val title = tabs[index]
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) DominoGreen else DominoSurface)
                            .border(
                                width = 1.dp,
                                color = if (selected) DominoGreen else Color(0xFFE6DAB8),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (selected) DominoGold else DominoMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DominoGreen)
                } else {
                    when (selectedTab) {
                        0 -> MatchesList(
                            matches = uiState.matches,
                            onDelete = { viewModel.deleteMatch(it) },
                            onEdit = { matchId -> onEditMatch(matchId) },
                            canEdit = canEdit
                        )
                        1 -> {
                            var expanded by remember { mutableStateOf(false) }
                            val playerNames = remember(uiState.players) {
                                listOf("Todos os Jogadores") + uiState.players.map { it.user.name }.distinct().sorted()
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {
                                        OutlinedTextField(
                                            value = buchoPlayerFilter,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Filtrar por Jogador", color = DominoMuted) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = DominoLight,
                                                unfocusedTextColor = DominoLight,
                                                focusedContainerColor = DominoSurface,
                                                unfocusedContainerColor = DominoSurface,
                                                focusedBorderColor = DominoGreen,
                                                unfocusedBorderColor = Color(0xFFE6DAB8)
                                            )
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(DominoSurface)
                                        ) {
                                            playerNames.forEach { name ->
                                                DropdownMenuItem(
                                                    text = { Text(name, color = DominoLight) },
                                                    onClick = {
                                                        buchoPlayerFilter = name
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                val filteredBuchos = remember(uiState.buchos, buchoPlayerFilter) {
                                    if (buchoPlayerFilter == "Todos os Jogadores") {
                                        uiState.buchos
                                    } else {
                                        uiState.buchos.filter { it.jogador?.trim()?.equals(buchoPlayerFilter.trim(), ignoreCase = true) == true }
                                    }
                                }

                                BuchosList(
                                    buchos = filteredBuchos,
                                    onDelete = { viewModel.deleteBucho(it) },
                                    onMarkPaid = { viewModel.markBuchoAsPaid(it) },
                                    canEdit = canEdit,
                                    isMarcio = userName.equals("MÁRCIO", ignoreCase = true)
                                )
                            }
                        }
                        2 -> {
                            var mensalidadeExpanded by remember { mutableStateOf(false) }
                            val playerNames = remember(uiState.players) {
                                listOf("Todos os Jogadores") + uiState.players.map { it.user.name }.distinct().sorted()
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                                    ExposedDropdownMenuBox(
                                        expanded = mensalidadeExpanded,
                                        onExpandedChange = { mensalidadeExpanded = !mensalidadeExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = mensalidadePlayerFilter,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Filtrar por Jogador", color = DominoMuted) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mensalidadeExpanded) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = DominoLight,
                                                unfocusedTextColor = DominoLight,
                                                focusedContainerColor = DominoSurface,
                                                unfocusedContainerColor = DominoSurface,
                                                focusedBorderColor = DominoGreen,
                                                unfocusedBorderColor = Color(0xFFE6DAB8)
                                            )
                                        )
                                        ExposedDropdownMenu(
                                            expanded = mensalidadeExpanded,
                                            onDismissRequest = { mensalidadeExpanded = false },
                                            modifier = Modifier.background(DominoSurface)
                                        ) {
                                            playerNames.forEach { name ->
                                                DropdownMenuItem(
                                                    text = { Text(name, color = DominoLight) },
                                                    onClick = {
                                                        mensalidadePlayerFilter = name
                                                        mensalidadeExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                val filteredMensalidades = remember(uiState.mensalidades, mensalidadePlayerFilter) {
                                    if (mensalidadePlayerFilter == "Todos os Jogadores") {
                                        uiState.mensalidades
                                    } else {
                                        uiState.mensalidades.filter { it.jogador?.trim()?.equals(mensalidadePlayerFilter.trim(), ignoreCase = true) == true }
                                    }
                                }

                                MensalidadesList(
                                    mensalidades = filteredMensalidades,
                                    onDelete = { viewModel.deleteMensalidade(it) },
                                    onMarkPaid = { viewModel.markMensalidadeAsPaid(it) },
                                    canEdit = canEdit,
                                    isMarcio = userName.equals("MÁRCIO", ignoreCase = true)
                                )
                            }
                        }
                        3 -> PlayersList(
                            players = uiState.players,
                            onToggleActive = { u, a -> viewModel.togglePlayerActive(u, a) },
                            onToggleVacation = { u, v -> viewModel.togglePlayerVacation(u, v) },
                            canEdit = canEdit
                        )
                        4 -> DebtorsList(debtors = uiState.debtors)
                        5 -> ComprovantesTab(
                            uiState = uiState,
                            onLoadHistorico = { viewModel.loadComprovantesHistorico() },
                            onTestarAnalise = { valor, imagemBase64 -> viewModel.testarAnaliseComprovante(valor, imagemBase64) },
                            onDismissTeste = { viewModel.dismissTesteComprovante() }
                        )
                    }
                }
            }

            // Footer with Version
            TextButton(
                onClick = { showReleaseNotes = true },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
            ) {
                Text(
                    text = "Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME} (Ver Notas)",
                    color = DominoMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun AdminAvatarBadge(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    backgroundColor: Color = DominoGreen,
    alert: Boolean = false,
    icon: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (alert) DominoOrange else backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon ?: if (alert) "!" else "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
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
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdminAvatarBadge(backgroundColor = DominoGreen, icon = "🁣")
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dateFormat.format(match.date), color = DominoMuted, style = MaterialTheme.typography.bodySmall)
                        Text("${match.team1Player1.displayName}/${match.team1Player2.displayName} vs ${match.team2Player1.displayName}/${match.team2Player2.displayName}", color = DominoLight, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Placar: ${match.score1} x ${match.score2}", color = DominoGreen, fontWeight = FontWeight.SemiBold)
                            if (match.wasBuchoRe) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 BUCHO DE RÉ", color = DominoOrange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Cadastrado por: ${match.registeredBy.name}", color = DominoMuted, style = MaterialTheme.typography.labelSmall)
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
fun BuchosList(
    buchos: List<com.marcioarruda.clubedodomino.data.network.BuchoDto>,
    onDelete: (Long?) -> Unit,
    onMarkPaid: (Long) -> Unit,
    canEdit: Boolean,
    isMarcio: Boolean
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(buchos) { bucho ->
            val isPending = isMarcio // um bucho listado aqui ainda não foi marcado como pago
            Card(
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                border = if (isPending) BorderStroke(1.dp, AlertBorderColor) else null,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdminAvatarBadge(backgroundColor = DominoMuted, alert = isPending)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bucho.data ?: "", color = DominoMuted, style = MaterialTheme.typography.bodySmall)
                        Text(bucho.jogador ?: "", color = DominoLight, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Valor: R$ ${bucho.valor}", color = DominoGreen, fontWeight = FontWeight.SemiBold)
                            if (bucho.buchore == true) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 BUCHO DE RÉ", color = DominoOrange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!bucho.cadastrado_por.isNullOrBlank()) {
                            Text("Cadastrado por: ${bucho.cadastrado_por}", color = DominoMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        if (isMarcio) {
                            IconButton(onClick = { bucho.id?.let { onMarkPaid(it) } }) {
                                Text("✔️")
                            }
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
}

@Composable
fun MensalidadesList(
    mensalidades: List<com.marcioarruda.clubedodomino.data.network.MensalidadeDto>,
    onDelete: (Long?) -> Unit,
    onMarkPaid: (Long?) -> Unit,
    canEdit: Boolean,
    isMarcio: Boolean
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(mensalidades) { mensalidade ->
            val isPending = isMarcio // mensalidade listada aqui ainda está em aberto
            Card(
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                border = if (isPending) BorderStroke(1.dp, AlertBorderColor) else null,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdminAvatarBadge(backgroundColor = DominoMuted, alert = isPending)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatMensalidadeReferencia(mensalidade.mensalidade), color = DominoMuted, style = MaterialTheme.typography.bodySmall)
                        Text(mensalidade.jogador ?: "", color = DominoLight, fontWeight = FontWeight.Bold)
                        Text("Valor: R$ 10,00", color = DominoGreen, fontWeight = FontWeight.SemiBold)
                    }
                    Row {
                        if (isMarcio) {
                            IconButton(onClick = { onMarkPaid(mensalidade.id) }) {
                                Text("✔️")
                            }
                        }
                        if (canEdit) {
                            IconButton(onClick = { onDelete(mensalidade.id) }) {
                                Text("🗑️")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMensalidadeReferencia(raw: String?): String {
    if (raw.isNullOrBlank()) return "N/A"
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(raw.take(10))
        if (date != null) SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(date) else raw
    } catch (_: Exception) {
        raw
    }
}

@Composable
fun DebtorsList(debtors: List<DebtorItem>) {
    val dateFormat = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val totalGeral = debtors.sumOf { it.totalDue }

    if (debtors.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✅", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Nenhum inadimplente!", color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Todos os jogadores estão em dia.", color = DominoMuted, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AlertBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .shadow(1.dp, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total em Aberto", color = DominoMuted, fontSize = 12.sp)
                        Text(
                            "${debtors.size} inadimplente${if (debtors.size > 1) "s" else ""}",
                            color = DominoLight, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "R$ ${"%.2f".format(totalGeral)}",
                        color = DominoOrange, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }

        items(debtors, key = { it.user.id }) { debtor ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AlertBorderColor),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
            ) {
                Column {
                    // Header clicável
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AvatarImage(
                                url = debtor.user.photoUrl,
                                size = 44.dp,
                                borderColor = DominoOrange,
                                borderWidth = 2.dp
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(DominoOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                debtor.user.displayName,
                                color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 15.sp
                            )
                            Text(
                                "${debtor.debts.size} débito${if (debtor.debts.size > 1) "s" else ""} em aberto",
                                color = DominoMuted, fontSize = 12.sp
                            )
                        }
                        Text(
                            "R$ ${"%.2f".format(debtor.totalDue)}",
                            color = DominoOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = DominoMuted
                        )
                    }

                    // Detalhes expansíveis
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF2EADB))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFFE6DAB8), modifier = Modifier.padding(bottom = 8.dp))
                            debtor.debts.forEach { entry ->
                                val icon = when (entry.type) {
                                    FinancialEntryType.MONTHLY_FEE -> "📅"
                                    FinancialEntryType.EXTRA_TAX -> "⚡"
                                    else -> "🁣"
                                }
                                val typeLabel = when (entry.type) {
                                    FinancialEntryType.MONTHLY_FEE -> "Mensalidade"
                                    FinancialEntryType.EXTRA_TAX -> "Taxa Extra"
                                    else -> "Bucho"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(icon, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(typeLabel, color = DominoLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            Text(
                                                when (entry.type) {
                                                    FinancialEntryType.MONTHLY_FEE, FinancialEntryType.EXTRA_TAX ->
                                                        dateFormat.format(entry.dueDate)
                                                    else ->
                                                        fullDateFormat.format(entry.dueDate)
                                                },
                                                color = DominoMuted, fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        "R$ ${"%.2f".format(entry.amount)}",
                                        color = DominoOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE6DAB8), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("Total: ", color = DominoMuted, fontSize = 13.sp)
                                Text(
                                    "R$ ${"%.2f".format(debtor.totalDue)}",
                                    color = DominoOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlayerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, password: String, avatarId: String, billingStart: Calendar) -> Unit,
    isLoading: Boolean
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("avatar_1") }

    // Date pickers — day/month/year for billing start
    val today = Calendar.getInstance()
    var billingDay by remember { mutableStateOf(1) }
    var billingMonth by remember { mutableStateOf(today.get(Calendar.MONTH) + 1) } // 1-based for display
    var billingYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }

    val availableAvatars = listOf(
        "marcio", "ruan", "tenorio", "frodo",
        "arnaldo", "sakaki", "molinho",
        "amilton", "breno", "calabria", "tatu", "pedro", "tercio", "geraldo", "emerson",
        "avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5",
        "avatar_6", "avatar_7", "avatar_8", "avatar_9", "avatar_10",
        "avatar_11", "avatar_13", "avatar_14", "avatar_15"
    )

    val nameError = name.isBlank()
    val emailError = email.isBlank() || !email.contains("@")
    val passwordError = password.length < 4

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DominoSurface,
        title = { Text("Cadastrar Jogador", color = DominoLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.uppercase() },
                    label = { Text("Nome do Jogador", color = DominoMuted) },
                    singleLine = true,
                    isError = name.isNotBlank() && nameError,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                        focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                    )
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim().lowercase() },
                    label = { Text("E-mail (login)", color = DominoMuted) },
                    singleLine = true,
                    isError = email.isNotBlank() && emailError,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                        focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                    )
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha (mín. 4 caracteres)", color = DominoMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = password.isNotBlank() && passwordError,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                        focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                    )
                )

                Text("Início das cobranças", color = DominoMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billingDay.toString(),
                        onValueChange = { billingDay = it.toIntOrNull()?.coerceIn(1, 28) ?: billingDay },
                        label = { Text("Dia", color = DominoMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                            focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                            focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                        )
                    )
                    OutlinedTextField(
                        value = billingMonth.toString(),
                        onValueChange = { billingMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: billingMonth },
                        label = { Text("Mês", color = DominoMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                            focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                            focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                        )
                    )
                    OutlinedTextField(
                        value = billingYear.toString(),
                        onValueChange = { billingYear = it.toIntOrNull() ?: billingYear },
                        label = { Text("Ano", color = DominoMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                            focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                            focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                        )
                    )
                }

                Text("Avatar", color = DominoMuted, fontSize = 12.sp)
                availableAvatars.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { avatarId ->
                            Box(
                                modifier = Modifier
                                    .clickable { selectedAvatar = avatarId }
                                    .padding(2.dp)
                            ) {
                                AvatarImage(
                                    url = avatarId,
                                    size = 48.dp,
                                    borderWidth = if (selectedAvatar == avatarId) 3.dp else 1.dp,
                                    borderColor = if (selectedAvatar == avatarId) DominoGreen else Color(0xFFE6DAB8)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!nameError && !emailError && !passwordError) {
                        val billingStart = Calendar.getInstance().apply {
                            set(Calendar.YEAR, billingYear)
                            set(Calendar.MONTH, billingMonth - 1) // back to 0-based
                            set(Calendar.DAY_OF_MONTH, billingDay)
                        }
                        onConfirm(name.trim(), email.trim(), password, selectedAvatar, billingStart)
                    }
                },
                enabled = !nameError && !emailError && !passwordError && !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DominoGold, disabledContainerColor = DominoGold.copy(alpha = 0.5f))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DominoGreen, strokeWidth = 2.dp)
                } else {
                    Text("Cadastrar", color = DominoGreen, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = DominoMuted) }
        }
    )
}

@Composable
fun PlayersList(
    players: List<AdminPlayerItem>,
    onToggleActive: (com.marcioarruda.clubedodomino.data.User, Boolean) -> Unit,
    onToggleVacation: (com.marcioarruda.clubedodomino.data.User, Boolean) -> Unit,
    canEdit: Boolean
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(players, key = { it.user.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdminAvatarBadge(
                            size = 36.dp,
                            backgroundColor = DominoGreen,
                            alert = !item.isActive
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(item.user.displayName, color = DominoLight, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ativo", color = DominoMuted)
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { if(canEdit) onToggleActive(item.user, it) },
                            enabled = canEdit,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DominoGreen,
                                checkedTrackColor = DominoGreen.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Modo Férias", color = DominoMuted)
                        Switch(
                            checked = item.isOnVacation,
                            onCheckedChange = { if(canEdit) onToggleVacation(item.user, it) },
                            enabled = canEdit,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DominoGold,
                                checkedTrackColor = DominoGold.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDbPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (senhaLogin: String, novaSenha: String) -> Unit,
    isLoading: Boolean
) {
    var senhaLogin by remember { mutableStateOf("") }
    var novaSenha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }

    val senhasNaoConferem = confirmarSenha.isNotEmpty() && novaSenha != confirmarSenha
    val senhaMuitoCurta = novaSenha.isNotEmpty() && novaSenha.length < 4
    val podeConfirmar = senhaLogin.isNotBlank() && novaSenha.isNotBlank() && !senhasNaoConferem && !senhaMuitoCurta

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DominoSurface,
        title = { Text("Alterar Senha do Banco de Dados", color = DominoLight, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Esta é a senha de acesso ao MySQL usada pelo servidor. Confirme com sua senha de " +
                    "login atual. Só é aplicada se a conexão com a nova senha for validada com sucesso.",
                    color = DominoMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = senhaLogin,
                    onValueChange = { senhaLogin = it },
                    label = { Text("Sua senha de login", color = DominoMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                        focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                    )
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFE6DAB8))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = novaSenha,
                    onValueChange = { novaSenha = it },
                    label = { Text("Nova senha do banco", color = DominoMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = senhaMuitoCurta,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                        focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmarSenha,
                    onValueChange = { confirmarSenha = it },
                    label = { Text("Confirmar nova senha do banco", color = DominoMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = senhasNaoConferem,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8),
                        focusedContainerColor = Color(0xFFF2EADB), unfocusedContainerColor = Color(0xFFF2EADB)
                    )
                )
                if (senhasNaoConferem) {
                    Text("As senhas não conferem.", color = DominoOrange, fontSize = 11.sp)
                } else if (senhaMuitoCurta) {
                    Text("A senha deve ter pelo menos 4 caracteres.", color = DominoOrange, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(senhaLogin, novaSenha) },
                enabled = podeConfirmar && !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DominoGold, disabledContainerColor = DominoGold.copy(alpha = 0.5f))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DominoGreen, strokeWidth = 2.dp)
                } else {
                    Text("Atualizar", color = DominoGreen, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = DominoMuted) }
        }
    )
}

// ─── Aba "Comprovantes" (visível só para o Márcio) ────────────────────────────
// Duas seções: histórico de comprovantes já submetidos (auditoria da baixa
// automática por IA) e um testador manual, que roda a mesma análise de IA sobre
// uma imagem qualquer sem dar baixa em nenhum débito — serve para conferir a
// eficiência da IA antes de confiar nela em comprovantes reais.
@Composable
fun ComprovantesTab(
    uiState: AdminUiState,
    onLoadHistorico: () -> Unit,
    onTestarAnalise: (valorEsperado: Double, imagemBase64: String) -> Unit,
    onDismissTeste: () -> Unit
) {
    LaunchedEffect(Unit) {
        onLoadHistorico()
    }

    var showTesteDialog by remember { mutableStateOf(false) }

    if (showTesteDialog) {
        TestarComprovanteDialog(
            uiState = uiState,
            onDismiss = {
                showTesteDialog = false
                onDismissTeste()
            },
            onTestarAnalise = onTestarAnalise
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .shadow(1.dp, RoundedCornerShape(16.dp))
                    .clickable { showTesteDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🧪 Testar Análise de IA", color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Envie uma imagem de teste e veja o que a IA detectaria, sem afetar nenhum débito real.",
                            color = DominoMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Text(
                "Histórico de Comprovantes",
                color = DominoLight,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (uiState.isLoadingComprovantes) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DominoGreen)
                }
            }
        } else if (uiState.comprovantesHistorico.isEmpty()) {
            item {
                Text("Nenhum comprovante submetido ainda.", color = DominoMuted, fontSize = 14.sp)
            }
        } else {
            items(uiState.comprovantesHistorico) { c ->
                ComprovanteHistoricoCard(c)
            }
        }
    }
}

@Composable
private fun ComprovanteHistoricoCard(c: ComprovanteHistoricoDto) {
    val aprovado = c.decisao == "BAIXA_AUTOMATICA"
    val borderColor = if (aprovado) DominoGreen.copy(alpha = 0.3f) else AlertBorderColor

    Card(
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(c.jogadorNome ?: "—", color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (aprovado) "✅ Baixa automática" else "⚠️ Enviado ao Telegram",
                    color = if (aprovado) DominoGreen else DominoOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Valor esperado: R$ ${"%.2f".format(c.valorEsperado ?: 0.0)}", color = DominoMuted, fontSize = 13.sp)
            if (c.valorDetectado != null) {
                Text("Valor detectado pela IA: R$ ${"%.2f".format(c.valorDetectado)}", color = DominoMuted, fontSize = 13.sp)
            }
            if (c.bancoOrigem != null) {
                Text("Banco de origem: ${c.bancoOrigem}", color = DominoMuted, fontSize = 13.sp)
            }
            if (c.tipoTransacao != null) {
                Text("Tipo: ${c.tipoTransacao}", color = DominoMuted, fontSize = 13.sp)
            }
            if (c.dataHoraDetectada != null) {
                Text("Data/hora: ${c.dataHoraDetectada}", color = DominoMuted, fontSize = 13.sp)
            } else if (c.dataDetectada != null) {
                Text("Data detectada: ${c.dataDetectada}", color = DominoMuted, fontSize = 13.sp)
            }
            if (c.idTransacaoDetectado != null) {
                Text("ID da transação: ${c.idTransacaoDetectado}", color = DominoMuted, fontSize = 13.sp)
            }
            if (c.credorDetectado != null) {
                val credorInfo = buildString {
                    append(c.credorDetectado)
                    if (c.credorDocumento != null) append(", CPF/CNPJ ${c.credorDocumento}")
                    if (c.credorInstituicao != null) append(", ${c.credorInstituicao}")
                    if (c.credorChavePix != null) append(", chave ${c.credorChavePix}")
                }
                Text("Destino (credor): $credorInfo", color = DominoMuted, fontSize = 13.sp)
            }
            if (c.pagadorDetectado != null) {
                val pagadorInfo = buildString {
                    append(c.pagadorDetectado)
                    if (c.pagadorDocumento != null) append(", CPF/CNPJ ${c.pagadorDocumento}")
                }
                Text("Origem (pagador): $pagadorInfo", color = DominoMuted, fontSize = 13.sp)
            }
            if (!c.motivo.isNullOrBlank()) {
                Text(c.motivo, color = if (aprovado) DominoGreen else DominoOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            if (c.createdAt != null) {
                Text(c.createdAt, color = DominoMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TestarComprovanteDialog(
    uiState: AdminUiState,
    onDismiss: () -> Unit,
    onTestarAnalise: (valorEsperado: Double, imagemBase64: String) -> Unit
) {
    val context = LocalContext.current
    var valorEsperadoTexto by remember { mutableStateOf("") }
    var imagemBase64 by remember { mutableStateOf<String?>(null) }
    var imagemNome by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                imagemBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                imagemNome = uri.lastPathSegment ?: "imagem selecionada"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DominoSurface,
        title = { Text("Testar Análise de IA", color = DominoLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Essa imagem NÃO vai dar baixa em nenhum débito — serve só para conferir o que a IA detectaria.",
                    color = DominoMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = valorEsperadoTexto,
                    onValueChange = { valorEsperadoTexto = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Valor esperado (R$)", color = DominoMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DominoLight, unfocusedTextColor = DominoLight,
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color(0xFFE6DAB8)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(imagemNome ?: "Selecionar imagem de teste", color = DominoGreen)
                }

                val resultado = uiState.testeComprovanteResultado
                val erro = uiState.testeComprovanteError

                if (uiState.isTestingComprovante) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DominoGreen)
                    }
                }

                if (erro != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(erro, color = DominoOrange, fontSize = 13.sp)
                }

                if (resultado != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFE6DAB8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (resultado.aprovado) "✅ Aprovaria baixa automática" else "⚠️ Não aprovaria — iria para o Telegram",
                        color = if (resultado.aprovado) DominoGreen else DominoOrange,
                        fontWeight = FontWeight.Bold
                    )
                    if (!resultado.motivo.isNullOrBlank()) {
                        Text(resultado.motivo, color = DominoMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    resultado.analise?.let { a ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Dados visíveis na imagem:", color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Parece comprovante bancário: ${if (a.pareceComprovanteBancario == true) "Sim" else "Não"}", color = DominoLight, fontSize = 13.sp)
                        Text("Possui autenticação: ${if (a.possuiAutenticacao == true) "Sim" else "Não"}", color = DominoLight, fontSize = 13.sp)
                        a.bancoOrigem?.let { Text("Banco de origem: $it", color = DominoLight, fontSize = 13.sp) }
                        a.tipoTransacao?.let { Text("Tipo: $it", color = DominoLight, fontSize = 13.sp) }
                        (a.dataHoraPagamento ?: a.dataPagamento)?.let { Text("Data/hora: $it", color = DominoLight, fontSize = 13.sp) }
                        a.valorPago?.let { Text("Valor: R$ %.2f".format(it), color = DominoLight, fontSize = 13.sp) }
                        a.idTransacao?.let { Text("ID da transação: $it", color = DominoLight, fontSize = 13.sp) }
                        if (a.credor != null || a.credorDocumento != null || a.credorInstituicao != null || a.credorChavePix != null) {
                            val credorInfo = buildString {
                                append(a.credor ?: "—")
                                a.credorDocumento?.let { append(", CPF/CNPJ $it") }
                                a.credorInstituicao?.let { append(", $it") }
                                a.credorChavePix?.let { append(", chave $it") }
                            }
                            Text("Destino (credor): $credorInfo", color = DominoLight, fontSize = 13.sp)
                        }
                        if (a.pagador != null || a.pagadorDocumento != null) {
                            val pagadorInfo = buildString {
                                append(a.pagador ?: "—")
                                a.pagadorDocumento?.let { append(", CPF/CNPJ $it") }
                            }
                            Text("Origem (pagador): $pagadorInfo", color = DominoLight, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val valor = valorEsperadoTexto.replace(",", ".").toDoubleOrNull()
            Button(
                onClick = { if (valor != null && imagemBase64 != null) onTestarAnalise(valor, imagemBase64!!) },
                enabled = valor != null && imagemBase64 != null && !uiState.isTestingComprovante,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DominoGold, disabledContainerColor = DominoGold.copy(alpha = 0.5f))
            ) {
                Text("Testar", color = DominoGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = DominoMuted) }
        }
    )
}
