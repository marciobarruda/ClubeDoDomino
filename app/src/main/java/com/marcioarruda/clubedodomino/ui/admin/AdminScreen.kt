package com.marcioarruda.clubedodomino.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcioarruda.clubedodomino.data.FinancialEntryType
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import com.marcioarruda.clubedodomino.ui.theme.DominoGreen
import com.marcioarruda.clubedodomino.ui.theme.DominoLight
import com.marcioarruda.clubedodomino.ui.theme.DominoMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoSurface
import com.marcioarruda.clubedodomino.ui.util.AvatarImage
import androidx.compose.ui.platform.LocalContext
import com.marcioarruda.clubedodomino.domain.MatchAvailabilityManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    val tabs = listOf("Partidas", "Buchos", "Jogadores", "Inadimplentes")

    // Determine permissions
    val userName = session?.userName?.trim() ?: ""
    val canEdit = userName.equals("MÁRCIO", ignoreCase = true) || userName.equals("CALÁBRIA", ignoreCase = true)

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    var showReleaseNotes by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) } // Local, Server, Notes
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showDbPasswordDialog by remember { mutableStateOf(false) }
    var buchoPlayerFilter by remember { mutableStateOf("Todos os Jogadores") }

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
            title = { Text("Notas da Versão") },
            text = {
                Column {
                    Text("Sua Versão: ${com.marcioarruda.clubedodomino.BuildConfig.VERSION_NAME} (${com.marcioarruda.clubedodomino.BuildConfig.VERSION_CODE})", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    if (releaseInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Última disponível: v${releaseInfo?.second}", color = DominoGold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("O que mudou:", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(releaseInfo?.third ?: "Nenhuma nota disponível.", color = Color.White)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseNotes = false }) { Text("Fechar") }
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
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") } },
            text = { Text(uiState.message!!) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administração") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, 
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black 
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
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                    shape = RoundedCornerShape(12.dp)
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Permite que você cadastre partidas fora do horário padrão (11h45 às 14h)",
                                color = Color.Gray,
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
                    HorizontalDivider(color = Color(0xFF444444), modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(
                        onClick = { showAddPlayerDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DominoGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Cadastrar Novo Jogador", color = DominoGreen, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Color(0xFF444444), modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(
                        onClick = { showDbPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = DominoGold)
                        Spacer(Modifier.width(8.dp))
                        Text("Alterar Senha do Banco de Dados", color = DominoGold, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = DominoGold) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                                            label = { Text("Filtrar por Jogador", color = DominoGold) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = Color(0xFF2C2C2C),
                                                unfocusedContainerColor = Color(0xFF2C2C2C),
                                                focusedBorderColor = DominoGold,
                                                unfocusedBorderColor = Color.Gray
                                            )
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(Color(0xFF2C2C2C))
                                        ) {
                                            playerNames.forEach { name ->
                                                DropdownMenuItem(
                                                    text = { Text(name, color = Color.White) },
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
                        2 -> PlayersList(
                            players = uiState.players,
                            onToggleActive = { u, a -> viewModel.togglePlayerActive(u, a) },
                            onToggleVacation = { u, v -> viewModel.togglePlayerVacation(u, v) },
                            canEdit = canEdit
                        )
                        3 -> DebtorsList(debtors = uiState.debtors)
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
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dateFormat.format(match.date), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text("${match.team1Player1.displayName}/${match.team1Player2.displayName} vs ${match.team2Player1.displayName}/${match.team2Player2.displayName}", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Placar: ${match.score1} x ${match.score2}", color = DominoGold)
                            if (match.wasBuchoRe) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 BUCHO DE RÉ", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text("Cadastrado por: ${match.registeredBy.name}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
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
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bucho.data ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text(bucho.jogador ?: "", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Valor: R$ ${bucho.valor}", color = DominoGold)
                            if (bucho.buchore == true) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 BUCHO DE RÉ", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (!bucho.cadastrado_por.isNullOrBlank()) {
                            Text("Cadastrado por: ${bucho.cadastrado_por}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
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
fun DebtorsList(debtors: List<DebtorItem>) {
    val dateFormat = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val totalGeral = debtors.sumOf { it.totalDue }

    if (debtors.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✅", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Nenhum inadimplente!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Todos os jogadores estão em dia.", color = Color.Gray, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total em Aberto", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            "${debtors.size} inadimplente${if (debtors.size > 1) "s" else ""}",
                            color = Color.White, fontSize = 13.sp
                        )
                    }
                    Text(
                        "R$ ${"%.2f".format(totalGeral)}",
                        color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 20.sp
                    )
                }
            }
        }

        items(debtors, key = { it.user.id }) { debtor ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
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
                        AvatarImage(
                            url = debtor.user.photoUrl,
                            size = 44.dp,
                            borderColor = Color(0xFFFF6B6B),
                            borderWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                debtor.user.displayName,
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                            )
                            Text(
                                "${debtor.debts.size} débito${if (debtor.debts.size > 1) "s" else ""} em aberto",
                                color = Color.Gray, fontSize = 12.sp
                            )
                        }
                        Text(
                            "R$ ${"%.2f".format(debtor.totalDue)}",
                            color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color.Gray
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
                                .background(Color(0xFF222222))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFF444444), modifier = Modifier.padding(bottom = 8.dp))
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
                                            Text(typeLabel, color = Color.White, fontSize = 13.sp)
                                            Text(
                                                when (entry.type) {
                                                    FinancialEntryType.MONTHLY_FEE, FinancialEntryType.EXTRA_TAX ->
                                                        dateFormat.format(entry.dueDate)
                                                    else ->
                                                        fullDateFormat.format(entry.dueDate)
                                                },
                                                color = Color.Gray, fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        "R$ ${"%.2f".format(entry.amount)}",
                                        color = Color(0xFFFF9B9B), fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFF444444), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("Total: ", color = Color.Gray, fontSize = 13.sp)
                                Text(
                                    "R$ ${"%.2f".format(debtor.totalDue)}",
                                    color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 13.sp
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
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                            focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                            focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                            focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                            focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                            focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                            focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                                    borderColor = if (selectedAvatar == avatarId) DominoGreen else Color.Gray
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
                colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Cadastrar", color = Color.Black, fontWeight = FontWeight.Bold)
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.user.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ativo", color = Color.LightGray)
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { if(canEdit) onToggleActive(item.user, it) },
                            enabled = canEdit
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Modo Férias", color = Color.LightGray)
                        Switch(
                            checked = item.isOnVacation,
                            onCheckedChange = { if(canEdit) onToggleVacation(item.user, it) },
                            enabled = canEdit
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
        title = { Text("Alterar Senha do Banco de Dados", color = DominoGold) },
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
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
                    )
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF444444))
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
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
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
                        focusedBorderColor = DominoGreen, unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF1A3A2A), unfocusedContainerColor = Color(0xFF1A3A2A)
                    )
                )
                if (senhasNaoConferem) {
                    Text("As senhas não conferem.", color = Color(0xFFE57373), fontSize = 11.sp)
                } else if (senhaMuitoCurta) {
                    Text("A senha deve ter pelo menos 4 caracteres.", color = Color(0xFFE57373), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(senhaLogin, novaSenha) },
                enabled = podeConfirmar && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Atualizar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = DominoMuted) }
        }
    )
}
