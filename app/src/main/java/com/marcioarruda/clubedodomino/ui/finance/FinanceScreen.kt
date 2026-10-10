package com.marcioarruda.clubedodomino.ui.finance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.FinancialEntry
import com.marcioarruda.clubedodomino.data.FinancialEntryStatus
import com.marcioarruda.clubedodomino.data.FinancialEntryType
import com.marcioarruda.clubedodomino.ui.ViewModelFactory
import com.marcioarruda.clubedodomino.ui.theme.DominoAmber
import com.marcioarruda.clubedodomino.ui.theme.DominoBg
import com.marcioarruda.clubedodomino.ui.theme.DominoCyan
import com.marcioarruda.clubedodomino.ui.theme.DominoError
import com.marcioarruda.clubedodomino.ui.theme.DominoGreen
import com.marcioarruda.clubedodomino.ui.theme.DominoLight
import com.marcioarruda.clubedodomino.ui.theme.DominoMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOnDark
import com.marcioarruda.clubedodomino.ui.theme.DominoOnDarkMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOrange
import com.marcioarruda.clubedodomino.ui.theme.DominoSurface
import com.marcioarruda.clubedodomino.ui.theme.DominoYellow
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private enum class FinanceFilter(val label: String) {
    ALL("TODAS"),
    MONTHLY("MENSALIDADES"),
    BUCHO("BUCHOS")
}

// Cor oficial da marca Pix, usada no badge/ícone que indica o clique no saldo vencido.
private val PixTeal = Color(0xFF32BCAD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    navController: NavController,
    userId: String,
    viewModel: FinanceViewModel = viewModel(factory = ViewModelFactory(ClubRepository()))
) {
    // Carrega dados apenas uma vez quando a tela é exibida
    LaunchedEffect(key1 = userId) {
        viewModel.loadFinancialData(userId)
        viewModel.loadBancosPix()
    }

    val uiState by viewModel.uiState.collectAsState()
    var selectedEntry by remember { mutableStateOf<FinancialEntry?>(null) }
    var selectedFilter by remember { mutableStateOf(FinanceFilter.ALL) }
    var showBancoPixSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val bancoPixSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    uiState.pixKeyCopiedMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearPixKeyCopiedMessage()
        }
    }

    // Launcher para selecionar imagens ou PDFs
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadComprovante(userId, it, context)
        }
    }

    if (uiState.uploadStatus == UploadStatus.SUCCESS) {
        val baixaAutomatica = uiState.uploadBaixaAutomatica == true
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss without clicking OK */ },
            title = { Text(if (baixaAutomatica) "Pagamento confirmado! ✅" else "Enviado!") },
            text = {
                Text(
                    if (baixaAutomatica) {
                        "Seu comprovante foi analisado e aprovado automaticamente. Suas pendências já foram baixadas!"
                    } else {
                        "Recebemos o seu comprovante. Ele será analisado manualmente e em breve AMILTON dará baixa em suas pendências!"
                    }
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissUploadStatus() }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            val encodedId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString())
            navController.navigate("dashboard/$encodedId") {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
                launchSingleTop = true
            }
            viewModel.onNavigateToHomeComplete()
        }
    }

    fun showDetails(entry: FinancialEntry) {
        selectedEntry = entry
        scope.launch { sheetState.show() }
    }

    fun dismissDetails() {
        scope.launch {
            sheetState.hide()
            selectedEntry = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Minhas finanças",
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
        floatingActionButton = {
            // Show FAB if there is ANY pending debt that has a remote identifier (payable)
            val hasPayableDebt = uiState.debts.any {
                it.status == FinancialEntryStatus.PENDING && (it.originalRemoteId != null || it.originalReference != null)
            }

            if (hasPayableDebt && uiState.uploadStatus != UploadStatus.UPLOADING) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                    containerColor = DominoYellow
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Enviar Comprovante", tint = DominoGreen)
                }
            } else if (uiState.uploadStatus == UploadStatus.UPLOADING) {
                FloatingActionButton(
                    onClick = { },
                    containerColor = DominoMuted
                ) {
                    CircularProgressIndicator(color = DominoOnDark, modifier = Modifier.size(24.dp))
                }
            }
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
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DominoGreen)
                    }
                }
                uiState.error != null -> {
                    ErrorView(
                        message = uiState.error ?: "Erro desconhecido",
                        onRetry = { viewModel.loadFinancialData(userId) }
                    )
                }
                uiState.uploadStatus == UploadStatus.ERROR -> {
                     AlertDialog(
                        onDismissRequest = { viewModel.dismissUploadStatus() },
                        title = { Text("Erro no Envio") },
                        text = { Text(uiState.uploadError ?: "Erro desconhecido") },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissUploadStatus() }) {
                                Text("Tentar Novamente")
                            }
                        }
                    )
                }
                else -> {
                    val filteredDebts = when (selectedFilter) {
                        FinanceFilter.ALL -> uiState.debts
                        FinanceFilter.MONTHLY -> uiState.debts.filter { it.type == FinancialEntryType.MONTHLY_FEE }
                        FinanceFilter.BUCHO -> uiState.debts.filter {
                            it.type == FinancialEntryType.BUCHO || it.type == FinancialEntryType.BUCHO_RE
                        }
                    }
                    val pendingItems = filteredDebts.filter { it.status != FinancialEntryStatus.PAID }
                    val paidItems = filteredDebts.filter { it.status == FinancialEntryStatus.PAID }
                    val pendingCount = uiState.debts.count { it.status == FinancialEntryStatus.PENDING }

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.loadFinancialData(userId, isRefreshing = true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            item {
                                TotalDueCard(
                                    totalVencido = uiState.totalDue,
                                    totalAVencer = uiState.totalUpcoming,
                                    pendingCount = pendingCount,
                                    onVencidoClick = {
                                        copiarChavePixParaAreaDeTransferencia(context) { message ->
                                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        showBancoPixSheet = true
                                    }
                                )
                            }

                            item {
                                FilterPills(
                                    selected = selectedFilter,
                                    onSelect = { selectedFilter = it }
                                )
                            }

                            if (filteredDebts.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            "Nenhum débito pendente! 🎉",
                                            color = DominoCyan,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            } else {
                                if (pendingItems.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "PENDENTES",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DominoMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                    items(
                                        items = pendingItems,
                                        key = { entry -> entry.id }
                                    ) { entry ->
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                            FinancialEntryItem(entry, onClick = { showDetails(entry) })
                                        }
                                    }
                                }

                                if (paidItems.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "PAGAS",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DominoMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                    items(
                                        items = paidItems,
                                        key = { entry -> entry.id }
                                    ) { entry ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .alpha(0.65f)
                                        ) {
                                            FinancialEntryItem(entry, onClick = { showDetails(entry) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedEntry != null) {
            ModalBottomSheet(
                onDismissRequest = { dismissDetails() },
                sheetState = sheetState,
                containerColor = DominoSurface
            ) {
                MatchDetailsBottomSheet(entry = selectedEntry!!, onDismiss = { dismissDetails() })
            }
        }

        if (showBancoPixSheet) {
            val bancosInstalados = remember(uiState.bancosPix) {
                uiState.bancosPix.filter { isAppInstalado(context, it.packageName) }
            }
            ModalBottomSheet(
                onDismissRequest = { showBancoPixSheet = false },
                sheetState = bancoPixSheetState,
                containerColor = DominoSurface
            ) {
                BancoPixSheet(
                    bancos = bancosInstalados,
                    onBancoClick = { banco ->
                        abrirAppDoBanco(context, banco.packageName)
                        showBancoPixSheet = false
                    },
                    onOutroAppClick = {
                        abrirSeletorGenerico(context)
                        showBancoPixSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterPills(selected: FinanceFilter, onSelect: (FinanceFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FinanceFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) DominoGreen else DominoSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) DominoGreen else Color(0xFFE7DEC9),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = filter.label,
                    color = if (isSelected) DominoYellow else DominoMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun FinancialEntryItem(entry: FinancialEntry, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = try { dateFormat.format(entry.dueDate) } catch (e: Exception) { "N/A" }

    val isMonthly = entry.type == FinancialEntryType.MONTHLY_FEE
    val icon = if (isMonthly) Icons.Default.CalendarToday else Icons.Default.MoneyOff
    val dateLabel = if (isMonthly) "Vencimento" else "Data da partida"
    val typeLabel = if (isMonthly) "Mensalidade" else "Bucho"

    // Check if entry is from current month (or future) to display "A Vencer"
    val currentCal = java.util.Calendar.getInstance()
    val entryCal = java.util.Calendar.getInstance().apply { time = entry.dueDate }

    val isCurrentMonthOrFuture = (entryCal.get(java.util.Calendar.YEAR) > currentCal.get(java.util.Calendar.YEAR)) ||
            (entryCal.get(java.util.Calendar.YEAR) == currentCal.get(java.util.Calendar.YEAR) &&
             entryCal.get(java.util.Calendar.MONTH) >= currentCal.get(java.util.Calendar.MONTH))

    val isPaid = entry.status == FinancialEntryStatus.PAID
    val borderColor = if (isPaid) Color.Transparent else DominoOrange.copy(alpha = 0.35f)
    val amountColor = if (isPaid) DominoCyan else DominoOrange

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DominoGreen.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DominoGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$typeLabel · ${entry.description}",
                    fontWeight = FontWeight.Bold,
                    color = DominoLight,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateLabel: $dateStr",
                    fontSize = 12.sp,
                    color = DominoMuted
                )
            }

            when (entry.status) {
                FinancialEntryStatus.PENDING -> {
                    if (isCurrentMonthOrFuture) {
                         Column(horizontalAlignment = Alignment.End) {
                             Text(
                                text = "R$ ${String.format("%.2f", entry.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = DominoYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "A Vencer",
                                style = MaterialTheme.typography.bodySmall,
                                color = DominoMuted
                            )
                         }
                    } else {
                        Text(
                            text = "R$ ${String.format("%.2f", entry.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = amountColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                FinancialEntryStatus.UNDER_REVIEW -> {
                    Text(
                        text = "Em Análise",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DominoYellow,
                        fontWeight = FontWeight.Bold
                    )
                }
                FinancialEntryStatus.PAID -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "R$ ${String.format("%.2f", entry.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = amountColor,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Pago",
                            tint = DominoCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MatchDetailsBottomSheet(entry: FinancialEntry, onDismiss: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = try { dateFormat.format(entry.dueDate) } catch (e: Exception) { "Não informado" }
    val isMonthly = entry.type == FinancialEntryType.MONTHLY_FEE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMonthly) "Detalhes da Mensalidade" else "Detalhes da Partida",
                    style = MaterialTheme.typography.titleLarge,
                    color = DominoLight
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = DominoLight)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (isMonthly) {
                DetailRow(label = "Referência", value = entry.originalReference ?: "N/A")
                DetailRow(label = "Descrição", value = entry.description)
            } else {
                DetailRow(label = "ID da Dívida", value = entry.originalRemoteId?.toString() ?: "N/A (Local)")
                DetailRow(label = "Dupla Vencedora", value = entry.winningPair ?: "Não informado")
                DetailRow(label = "Dupla Perdedora", value = entry.losingPair ?: "Não informado")
                DetailRow(label = "Placar", value = entry.description)
            }

            DetailRow(label = if(isMonthly) "Vencimento" else "Data", value = dateStr)
            DetailRow(label = "Valor", value = "R$ ${String.format("%.2f", entry.amount)}", isHighlight = true)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = DominoMuted, fontSize = 14.sp)
        Text(
            value,
            color = if (isHighlight) DominoGreen else DominoLight,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Erro",
            tint = DominoOrange,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = DominoMuted,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)
        ) {
            Text("Tentar Novamente", color = DominoOnDark)
        }
    }
}

@Composable
fun TotalDueCard(
    totalVencido: Double,
    totalAVencer: Double,
    pendingCount: Int,
    onVencidoClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoGreen),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MEU SALDO",
                color = DominoYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SaldoColumn(
                    label = "VENCIDO",
                    value = totalVencido,
                    color = DominoError,
                    onClick = if (totalVencido > 0.0) onVencidoClick else null,
                    showPixBadge = totalVencido > 0.0
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(DominoOnDark.copy(alpha = 0.15f))
                )
                SaldoColumn(label = "A VENCER", value = totalAVencer, color = DominoAmber)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (pendingCount == 1) "1 cobrança pendente" else "$pendingCount cobranças pendentes",
                color = DominoOnDarkMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SaldoColumn(
    label: String,
    value: Double,
    color: Color,
    onClick: (() -> Unit)? = null,
    showPixBadge: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = "R$ ${String.format("%.2f", value)}",
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = DominoOnDarkMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
            if (showPixBadge) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PixTeal)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.marcioarruda.clubedodomino.R.drawable.ic_pix),
                            contentDescription = null,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("PIX", color = Color.White, fontWeight = FontWeight.Black, fontSize = 8.5.sp, letterSpacing = 0.3.sp)
                    }
                }
            }
        }
    }
}

// Chave Pix do clube — copiada automaticamente ao clicar no saldo vencido, para o jogador só
// colar no app do banco que escolher.
private const val CHAVE_PIX_CLUBE = "clubedominoemprel@gmail.com"

private fun copiarChavePixParaAreaDeTransferencia(context: android.content.Context, onCopiado: (String) -> Unit) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Chave Pix", CHAVE_PIX_CLUBE))
    onCopiado("Chave Pix copiada: $CHAVE_PIX_CLUBE")
}

// true se o app estiver instalado E visível para este app — requer que o package esteja
// declarado em <queries> no AndroidManifest (ver comentário lá); os pacotes vindos do Admin fora
// dessa lista sempre retornam false aqui até o manifest ser atualizado numa próxima versão.
private fun isAppInstalado(context: android.content.Context, packageName: String): Boolean =
    context.packageManager.getLaunchIntentForPackage(packageName) != null

// Abre o app do banco direto pelo package name. Só é chamado para bancos já filtrados como
// instalados (ver isAppInstalado), mas ainda cai no seletor genérico em caso de falha inesperada.
private fun abrirAppDoBanco(context: android.content.Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // cai no fallback abaixo
        }
    }
    android.widget.Toast.makeText(context, "Não foi possível abrir o app. A chave Pix já está copiada — cole no app do seu banco.", android.widget.Toast.LENGTH_LONG).show()
}

// Fallback manual — só acionado quando o jogador escolhe "Outro app" na lista (nenhum banco
// cadastrado foi detectado como instalado, ou o banco dele não está na lista). Abre o seletor
// padrão do Android para "compartilhar"; a chave já está copiada, então o jogador consegue colar
// mesmo sem escolher nada aqui.
private fun abrirSeletorGenerico(context: android.content.Context) {
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, CHAVE_PIX_CLUBE)
    }
    val chooser = android.content.Intent.createChooser(sendIntent, "Pagar com...").apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (_: android.content.ActivityNotFoundException) {
        // Nenhum app disponível — a chave já foi copiada, o jogador ainda consegue colar
        // manualmente no app do banco que preferir.
    }
}

@Composable
private fun BancoPixSheet(
    bancos: List<com.marcioarruda.clubedodomino.data.network.BancoPixDto>,
    onBancoClick: (com.marcioarruda.clubedodomino.data.network.BancoPixDto) -> Unit,
    onOutroAppClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Chave Pix copiada!",
            color = DominoLight,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Text(
            if (bancos.isEmpty()) "Não encontramos seu banco instalado. Toque em \"Outro app\" para escolher." else "Escolha o app do seu banco para colar e pagar.",
            color = DominoMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(bancos, key = { it.id }) { banco ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBancoClick(banco) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BankAppIcon(packageName = banco.packageName, size = 28.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(banco.nomeExibicao, color = DominoLight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOutroAppClick() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("➕", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Outro app", color = DominoMuted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// Ícone real do app do banco (o mesmo que aparece na gaveta de apps do aparelho), lido via
// PackageManager. Cai no emoji de prédio se o app não puder ser consultado (não instalado, ou
// pacote fora da lista declarada em <queries> no manifest).
@Composable
private fun BankAppIcon(packageName: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Text("🏦", fontSize = (size.value * 0.7).sp)
    }
}
