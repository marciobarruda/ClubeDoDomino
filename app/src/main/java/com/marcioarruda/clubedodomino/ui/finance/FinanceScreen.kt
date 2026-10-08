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
import androidx.compose.ui.platform.LocalContext
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
    }

    val uiState by viewModel.uiState.collectAsState()
    var selectedEntry by remember { mutableStateOf<FinancialEntry?>(null) }
    var selectedFilter by remember { mutableStateOf(FinanceFilter.ALL) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Launcher para selecionar imagens ou PDFs
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadComprovante(userId, it, context)
        }
    }

    if (uiState.uploadStatus == UploadStatus.SUCCESS) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss without clicking OK */ },
            title = { Text("Enviado!") },
            text = { Text("Recebemos o seu comprovante. Aguarde que em breve AMILTON dará baixa em suas pendências!") },
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
                                TotalDueCard(uiState.totalDue, uiState.totalUpcoming, pendingCount)
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
fun TotalDueCard(totalVencido: Double, totalAVencer: Double, pendingCount: Int) {
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
                SaldoColumn(label = "VENCIDO", value = totalVencido, color = DominoError)
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
private fun SaldoColumn(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "R$ ${String.format("%.2f", value)}",
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            color = DominoOnDarkMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}
