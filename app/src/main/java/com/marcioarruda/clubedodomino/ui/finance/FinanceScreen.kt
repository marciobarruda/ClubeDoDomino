package com.marcioarruda.clubedodomino.ui.finance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.marcioarruda.clubedodomino.ui.theme.GlassyColor
import com.marcioarruda.clubedodomino.ui.theme.RoyalGold
import com.marcioarruda.clubedodomino.ui.util.LifecycleEffect
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

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
                title = { Text("Financeiro", color = RoyalGold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = RoyalGold)
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
                    containerColor = RoyalGold
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Enviar Comprovante", tint = Color.Black)
                }
            } else if (uiState.uploadStatus == UploadStatus.UPLOADING) {
                FloatingActionButton(
                    onClick = { },
                    containerColor = Color.Gray
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                }
            }
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
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RoyalGold)
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
                                TotalDueCard(uiState.totalDue, uiState.totalUpcoming)
                            }
                            


                            if (uiState.debts.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            "Nenhum débito pendente! \uD83C\uDF89",
                                            color = Color.Green,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Detalhamento de Débitos",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                
                                items(
                                    items = uiState.debts,
                                    key = { entry -> entry.id }
                                ) { entry ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        FinancialEntryItem(entry, onClick = { showDetails(entry) })
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
                containerColor = Color(0xFF1E1E1E)
            ) {
                MatchDetailsBottomSheet(entry = selectedEntry!!, onDismiss = { dismissDetails() })
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
    val iconBgColor = if (isMonthly) Color(0xFF2196F3) else Color(0xFFFF9800)
    val dateLabel = if (isMonthly) "Vencimento" else "Data da partida"
    
    // Check if entry is from current month (or future) to display "A Vencer"
    val currentCal = java.util.Calendar.getInstance()
    val entryCal = java.util.Calendar.getInstance().apply { time = entry.dueDate }
    
    val isCurrentMonthOrFuture = (entryCal.get(java.util.Calendar.YEAR) > currentCal.get(java.util.Calendar.YEAR)) ||
            (entryCal.get(java.util.Calendar.YEAR) == currentCal.get(java.util.Calendar.YEAR) && 
             entryCal.get(java.util.Calendar.MONTH) >= currentCal.get(java.util.Calendar.MONTH))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassyColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMonthly) "Mensalidade" else "Bucho",
                    fontWeight = FontWeight.Bold,
                    color = iconBgColor,
                    fontSize = 12.sp
                )
                Text(
                    text = entry.description,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateLabel: $dateStr",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            when (entry.status) {
                FinancialEntryStatus.PENDING -> {
                    if (isCurrentMonthOrFuture) {
                         Column(horizontalAlignment = Alignment.End) {
                             Text(
                                text = "R$ ${String.format("%.2f", entry.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFA000), // Amber for "To Mature"
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "A Vencer",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFA000)
                            )
                         }
                    } else {
                        Text(
                            text = "R$ ${String.format("%.2f", entry.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                FinancialEntryStatus.UNDER_REVIEW -> {
                    Text(
                        text = "Em Análise",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                }
                FinancialEntryStatus.PAID -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Pago",
                        tint = Color.Green
                    )
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
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
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
        Text(label, color = Color.LightGray, fontSize = 14.sp)
        Text(
            value,
            color = if (isHighlight) RoyalGold else Color.White,
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
            tint = Color.Red,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = Color.LightGray,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
        ) {
            Text("Tentar Novamente", color = Color.Black)
        }
    }
}

@Composable
fun TotalDueCard(total: Double, upcoming: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassyColor),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pendente", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "R$ ${String.format("%.2f", total)}",
                    color = if (total > 0) Color(0xFFFF5252) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
            
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.1f)))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("A Vencer", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "R$ ${String.format("%.2f", upcoming)}",
                    color = if (upcoming > 0) Color(0xFFFFA000) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }
    }
}
