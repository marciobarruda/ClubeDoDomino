package com.marcioarruda.clubedodomino.ui.torneio

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.UserSession
import com.marcioarruda.clubedodomino.data.network.TorneioDto
import com.marcioarruda.clubedodomino.data.network.TorneioIndicadoDto
import com.marcioarruda.clubedodomino.data.network.TorneioPartidaDto
import com.marcioarruda.clubedodomino.data.network.TorneioRankingDto
import com.marcioarruda.clubedodomino.data.network.TorneioTituloDto
import com.marcioarruda.clubedodomino.ui.theme.*
import com.marcioarruda.clubedodomino.ui.util.AvatarImage
import com.marcioarruda.clubedodomino.ui.util.LifecycleEffect
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val BorderBeige = Color(0xFFE6DAB8)

// Emoji usado quando a imagem do troféu (res/drawable/trofeu_<tipo>) ainda não foi adicionada.
private val TROFEU_EMOJI = mapOf(
    "campeao_torneio" to "🏆",
    "chibata_man" to "🥇",
    "mais_viril" to "💪",
    "gravida_taubate" to "🤰",
    "bimba_ouro" to "🎯"
)

// As imagens dos troféus são opcionais: procuradas pelo nome (trofeu_<tipo>) em res/drawable,
// assim o app compila e funciona (com emoji) antes delas existirem.
@SuppressLint("DiscouragedApi")
private fun trofeuResId(context: Context, tipo: String): Int =
    context.resources.getIdentifier("trofeu_$tipo", "drawable", context.packageName)

private fun statusLabel(status: String) = when (status) {
    "agendado" -> "Agendado"
    "em_andamento" -> "Em andamento"
    "aguardando_encerramento" -> "Aguardando encerramento"
    "encerrado" -> "Encerrado"
    else -> status
}

private fun statusColor(status: String) = when (status) {
    "em_andamento" -> DominoCyan
    "aguardando_encerramento" -> DominoAmber
    "encerrado" -> DominoGreen
    else -> DominoMuted
}

private fun formatarData(ymd: String, pattern: String = "EEE, dd/MM/yyyy"): String = try {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(ymd)
    SimpleDateFormat(pattern, Locale("pt", "BR")).format(d!!).replaceFirstChar { it.uppercase() }
} catch (_: Exception) { ymd }

private fun List<User>.avatarDe(nome: String): String =
    find { it.name.equals(nome.trim(), ignoreCase = true) || it.displayName.equals(nome.trim(), ignoreCase = true) }?.photoUrl ?: ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorneioScreen(
    navController: NavController,
    viewModel: TorneioViewModel,
    session: UserSession?
) {
    val state by viewModel.uiState.collectAsState()
    val userName = session?.userName?.trim() ?: ""
    // Mesma regra de edição do Admin.
    val isAdmin = userName.equals("MÁRCIO", ignoreCase = true) || userName.equals("CALÁBRIA", ignoreCase = true)

    var aba by remember { mutableStateOf(0) } // 0 = Torneios, 1 = Galeria
    var formTorneio by remember { mutableStateOf<TorneioDto?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var confirmacao by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LifecycleEffect { viewModel.load() }

    BackHandler(enabled = state.detalhe != null) { viewModel.fecharDetalhe() }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            containerColor = DominoSurface,
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK", color = DominoGreen) } },
            text = { Text(msg, color = DominoLight) }
        )
    }

    confirmacao?.let { (texto, onConfirm) ->
        AlertDialog(
            onDismissRequest = { confirmacao = null },
            containerColor = DominoSurface,
            text = { Text(texto, color = DominoLight) },
            confirmButton = {
                TextButton(onClick = { confirmacao = null; onConfirm() }) { Text("Confirmar", color = DominoGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmacao = null }) { Text("Cancelar", color = DominoMuted) } }
        )
    }

    if (showForm) {
        TorneioFormDialog(
            inicial = formTorneio,
            isSaving = state.isSaving,
            onDismiss = { showForm = false },
            onConfirm = { nome, data, ini, fim, corte ->
                viewModel.salvar(formTorneio?.id, nome, data, ini, fim, corte, session?.userName) { showForm = false }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.detalhe != null) "Torneio" else "Torneios",
                        color = DominoGreen,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.detalhe != null) viewModel.fecharDetalhe() else navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = DominoGreen)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DominoBg)
            )
        },
        containerColor = DominoBg
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val detalhe = state.detalhe
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DominoGreen)
                state.error != null -> Text(state.error!!, color = DominoOrange, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                detalhe != null -> TorneioDetalhe(
                    torneio = detalhe,
                    players = state.players,
                    isAdmin = isAdmin,
                    isSaving = state.isSaving,
                    onEditar = { formTorneio = detalhe; showForm = true },
                    onExcluir = { confirmacao = "Excluir o torneio \"${detalhe.nome}\" com todas as partidas e títulos?" to { viewModel.excluir(detalhe.id) } },
                    onEncerrar = { confirmacao = "Encerrar o torneio e definir os títulos agora?" to { viewModel.encerrar(detalhe.id) } },
                    onRecalcular = { confirmacao = "Recalcular os títulos deste torneio?" to { viewModel.encerrar(detalhe.id) } },
                    onReabrir = { confirmacao = "Reabrir o torneio? Os títulos gravados serão apagados até ele ser encerrado de novo." to { viewModel.reabrir(detalhe.id) } },
                    onExcluirPartida = { p -> confirmacao = "Excluir esta partida do torneio?" to { viewModel.excluirPartida(detalhe.id, p.id) } }
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Torneios", "🏛️ Galeria de Troféus").forEachIndexed { i, titulo ->
                            val selected = aba == i
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) DominoGreen else DominoSurface)
                                    .border(1.dp, if (selected) DominoGreen else BorderBeige, RoundedCornerShape(50))
                                    .clickable { aba = i }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(titulo, color = if (selected) DominoGold else DominoMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                            }
                        }
                    }
                    if (aba == 0) {
                        TorneiosLista(
                            torneios = state.torneios,
                            players = state.players,
                            isAdmin = isAdmin,
                            isLoadingDetalhe = state.isLoadingDetalhe,
                            onNovo = { formTorneio = null; showForm = true },
                            onAbrir = { viewModel.abrirDetalhe(it.id) }
                        )
                    } else {
                        GaleriaDeTrofeus(torneios = state.torneios, players = state.players)
                    }
                }
            }
        }
    }
}

@Composable
private fun TorneiosLista(
    torneios: List<TorneioDto>,
    players: List<User>,
    isAdmin: Boolean,
    isLoadingDetalhe: Boolean,
    onNovo: () -> Unit,
    onAbrir: (TorneioDto) -> Unit
) {
    // Em andamento/aguardando primeiro, depois os agendados (mais próximo primeiro), depois o histórico.
    val ativos = torneios.filter { it.status == "em_andamento" || it.status == "aguardando_encerramento" }
    val agendados = torneios.filter { it.status == "agendado" }.sortedWith(compareBy({ it.data }, { it.horaInicio }))
    val encerrados = torneios.filter { it.status == "encerrado" }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isAdmin) {
            item {
                Button(
                    onClick = onNovo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DominoYellow, contentColor = DominoGreen)
                ) { Text("+ Novo torneio", fontWeight = FontWeight.Bold) }
            }
        }
        if (isLoadingDetalhe) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DominoGreen) }
        }
        if (torneios.isEmpty()) {
            item {
                Text(
                    "Nenhum torneio cadastrado ainda.",
                    color = DominoMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        }
        secao("ACONTECENDO", ativos, players, onAbrir)
        secao("PRÓXIMOS", agendados, players, onAbrir)
        secao("ENCERRADOS", encerrados, players, onAbrir)
    }
}

private fun LazyListScope.secao(titulo: String, lista: List<TorneioDto>, players: List<User>, onAbrir: (TorneioDto) -> Unit) {
    if (lista.isEmpty()) return
    item { SecaoTitulo(titulo) }
    items(lista, key = { "t${it.id}" }) { TorneioCard(it, players) { onAbrir(it) } }
}

@Composable
private fun SecaoTitulo(texto: String) {
    Text(
        texto,
        color = DominoMuted,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun StatusBadge(status: String) {
    val cor = statusColor(status)
    Text(
        statusLabel(status),
        color = cor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(cor.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun TorneioCard(t: TorneioDto, players: List<User>, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.nome,
                    color = DominoLight,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(t.status)
            }
            Spacer(Modifier.height(4.dp))
            Text("📅 ${formatarData(t.data)} · ${t.horaInicio}–${t.horaFim}", color = DominoMuted, fontSize = 12.sp)
            Text("🎲 ${t.totalPartidas} partidas", color = DominoMuted, fontSize = 12.sp)
            if (!t.revelado) {
                Text("🎖️ Indicados aos troféus do ano — toque para ver", color = DominoGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            val campeoes = t.titulos.filter { it.tipo == "campeao_torneio" }
            if (campeoes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    campeoes.forEach { c ->
                        AvatarImage(url = players.avatarDe(c.jogador), size = 24.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(c.jogador, color = DominoGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TorneioDetalhe(
    torneio: TorneioDto,
    players: List<User>,
    isAdmin: Boolean,
    isSaving: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit,
    onEncerrar: () -> Unit,
    onRecalcular: () -> Unit,
    onReabrir: () -> Unit,
    onExcluirPartida: (TorneioPartidaDto) -> Unit
) {
    val encerrado = torneio.status == "encerrado"
    val titulos = if (encerrado) torneio.titulos else torneio.titulosParciais.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DominoGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(torneio.nome, color = DominoYellow, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${formatarData(torneio.data, "EEEE, dd/MM/yyyy")} · ${torneio.horaInicio}–${torneio.horaFim}", color = DominoOnDark, fontSize = 13.sp)
                    torneio.corteTitulos?.let {
                        Text("Títulos do ano: partidas até ${formatarData(it, "dd/MM/yyyy")}", color = DominoOnDarkMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            statusLabel(torneio.status),
                            color = DominoGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.background(DominoYellow, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${torneio.totalPartidas} partidas", color = DominoOnDarkMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Partidas do torneio não geram cobrança e não contam no ranking do clube.",
                        color = DominoOnDarkMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (isAdmin) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!encerrado) {
                        if (torneio.status != "agendado") {
                            AcaoBotao("Encerrar", DominoYellow, DominoGreen, isSaving, Modifier.weight(1f), onEncerrar)
                        }
                        AcaoBotao("Editar", DominoSurface, DominoGreen, isSaving, Modifier.weight(1f), onEditar)
                    } else {
                        AcaoBotao("Recalcular", DominoYellow, DominoGreen, isSaving, Modifier.weight(1f), onRecalcular)
                        AcaoBotao("Reabrir", DominoSurface, DominoGreen, isSaving, Modifier.weight(1f), onReabrir)
                    }
                    AcaoBotao("Excluir", DominoSurface, DominoError, isSaving, Modifier.weight(1f), onExcluir)
                }
            }
        }

        val indicados = torneio.indicados.orEmpty()
        if (!torneio.revelado && indicados.isNotEmpty()) {
            item { SecaoTitulo("INDICADOS AOS TROFÉUS DO ANO") }
            item {
                Text(
                    "Vencedores e pontuações serão revelados em ${revelacaoTexto(torneio)}. Os indicados estão em ordem alfabética.",
                    color = DominoMuted,
                    fontSize = 11.sp
                )
            }
            items(indicados, key = { "ind-${it.tipo}" }) { IndicadosCard(it, players) }
        }

        item { SecaoTitulo(if (encerrado) "TÍTULOS" else "TÍTULOS PARCIAIS") }
        if (!encerrado) {
            item {
                Text(
                    if (torneio.revelado) "Prévia ao vivo — os títulos são gravados ao encerrar o torneio. Os títulos do ano consideram só as partidas normais do clube."
                    else "Prévia ao vivo do Campeão do Torneio. Os títulos do ano aparecem aqui após o término do torneio.",
                    color = DominoMuted,
                    fontSize = 11.sp
                )
            }
        }
        if (titulos.isEmpty()) {
            item { Text("Nenhum título definido ainda.", color = DominoMuted, fontSize = 13.sp) }
        } else {
            items(titulos) { TrofeuCard(it, players) }
        }

        item { SecaoTitulo("RANKING DO TORNEIO") }
        val ranking = torneio.ranking.orEmpty()
        if (ranking.isEmpty()) {
            item { Text("Nenhuma partida registrada no torneio.", color = DominoMuted, fontSize = 13.sp) }
        } else {
            items(ranking.size) { i -> RankingTorneioRow(i + 1, ranking[i], players) }
        }

        val partidas = torneio.partidas.orEmpty()
        if (partidas.isNotEmpty()) {
            item { SecaoTitulo("PARTIDAS") }
            items(partidas, key = { "p${it.id}" }) { p ->
                PartidaTorneioRow(p, podeExcluir = isAdmin && !encerrado, onExcluir = { onExcluirPartida(p) })
            }
        }
    }
}

@Composable
private fun AcaoBotao(texto: String, fundo: Color, cor: Color, isSaving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isSaving,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fundo, contentColor = cor)
    ) { Text(texto, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

@Composable
private fun RankingTorneioRow(posicao: Int, r: TorneioRankingDto, players: List<User>) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (posicao == 1) "👑" else "$posicao",
                color = DominoMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(26.dp)
            )
            AvatarImage(url = players.avatarDe(r.jogador), size = 36.dp, borderWidth = 1.dp, borderColor = DominoMuted.copy(alpha = 0.3f))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(r.jogador, color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.partidas} partidas · ${r.vitorias}V ${r.derrotas}D · buchos ${r.buchosAplicados}/${r.buchosSofridos}",
                    color = DominoMuted,
                    fontSize = 11.sp
                )
            }
            Text("${r.pontos} pts", color = DominoCyan, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PartidaTorneioRow(p: TorneioPartidaDto, podeExcluir: Boolean, onExcluir: () -> Unit) {
    val time1Venceu = p.scored1 > p.scored2
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${p.jogador1} / ${p.jogador2}",
                    color = if (time1Venceu) DominoGreen else DominoLight,
                    fontWeight = if (time1Venceu) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
                Text(
                    "${p.jogador3} / ${p.jogador4}",
                    color = if (!time1Venceu) DominoGreen else DominoLight,
                    fontWeight = if (!time1Venceu) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
                val hora = p.data?.drop(11)?.take(5).orEmpty()
                Text(
                    listOfNotNull(hora.ifBlank { null }, "${p.pts} pts", if (p.buchore) "bucho de ré" else null, p.cadastrado_por?.let { "por $it" })
                        .joinToString(" · "),
                    color = DominoMuted,
                    fontSize = 11.sp
                )
            }
            Text("${p.scored1} x ${p.scored2}", color = DominoLight, fontWeight = FontWeight.Black, fontSize = 16.sp)
            if (podeExcluir) {
                IconButton(onClick = onExcluir) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir partida", tint = DominoError)
                }
            }
        }
    }
}

@Composable
private fun TrofeuImagem(tipo: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val resId = remember(tipo) { trofeuResId(context, tipo) }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(DominoYellow.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Image(painter = painterResource(resId), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text(TROFEU_EMOJI[tipo] ?: "🏆", fontSize = (size.value * 0.5f).sp)
        }
    }
}

private fun revelacaoTexto(t: TorneioDto): String {
    val quando = t.revelacaoEm ?: "${t.data} ${t.horaFim}"
    return "${formatarData(quando.take(10), "dd/MM")} às ${quando.drop(11).take(5)}"
}

@Composable
private fun IndicadosCard(ind: TorneioIndicadoDto, players: List<User>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        modifier = Modifier.fillMaxWidth().border(1.dp, DominoYellow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrofeuImagem(ind.tipo, 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ind.titulo.uppercase(), color = DominoMuted, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(6.dp))
                if (ind.jogadores.isEmpty()) {
                    Text("Sem indicados ainda.", color = DominoMuted, fontSize = 12.sp)
                }
                ind.jogadores.forEach { nome ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        AvatarImage(url = players.avatarDe(nome), size = 24.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(nome, color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrofeuCard(t: TorneioTituloDto, players: List<User>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DominoSurface),
        modifier = Modifier.fillMaxWidth().border(1.dp, DominoYellow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrofeuImagem(t.tipo, 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(t.titulo.uppercase(), color = DominoMuted, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(url = players.avatarDe(t.jogador), size = 28.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(t.jogador, color = DominoGreen, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!t.valor.isNullOrBlank()) {
                    Text(t.valor, color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (!t.detalhe.isNullOrBlank()) {
                    Text(t.detalhe, color = DominoMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// Galeria de troféus: histórico de todos os torneios encerrados, agrupado por ano.
@Composable
private fun GaleriaDeTrofeus(torneios: List<TorneioDto>, players: List<User>) {
    val porAno = torneios
        .filter { it.titulos.isNotEmpty() }
        .groupBy { it.data.take(4) }
        .toSortedMap(compareByDescending { it })

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (porAno.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏛️", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("A galeria ainda está vazia.", color = DominoMuted, fontWeight = FontWeight.Bold)
                    Text("Os troféus aparecem aqui quando um torneio é encerrado.", color = DominoMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
        porAno.forEach { (ano, lista) ->
            item(key = "ano$ano") {
                Text(ano, color = DominoGreen, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 26.sp, modifier = Modifier.padding(top = 8.dp))
            }
            lista.forEach { t ->
                item(key = "g${t.id}") {
                    Text("🏆 ${t.nome} · ${formatarData(t.data, "dd/MM/yyyy")}", color = DominoMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                items(t.titulos, key = { "g${t.id}-${it.tipo}-${it.jogador}" }) { TrofeuCard(it, players) }
            }
        }
    }
}

@Composable
private fun TorneioFormDialog(
    inicial: TorneioDto?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (nome: String, data: String, horaInicio: String, horaFim: String, corteTitulos: String) -> Unit
) {
    val context = LocalContext.current
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var data by remember { mutableStateOf(inicial?.data ?: "") }
    var horaInicio by remember { mutableStateOf(inicial?.horaInicio ?: "") }
    var horaFim by remember { mutableStateOf(inicial?.horaFim ?: "") }
    // Data de corte dos títulos anuais: vazia = dia anterior ao torneio (padrão do servidor).
    var corte by remember { mutableStateOf(inicial?.corteTitulos ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }

    fun escolherData(atual: String, onSet: (String) -> Unit) {
        val cal = Calendar.getInstance()
        if (atual.length == 10) {
            runCatching { cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(atual)!! }
        }
        DatePickerDialog(context, { _, y, m, d ->
            onSet("%04d-%02d-%02d".format(y, m + 1, d))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun escolherHora(atual: String, onSet: (String) -> Unit) {
        val h = atual.take(2).toIntOrNull() ?: 14
        val m = atual.drop(3).toIntOrNull() ?: 0
        TimePickerDialog(context, { _, hh, mm -> onSet("%02d:%02d".format(hh, mm)) }, h, m, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DominoSurface,
        title = { Text(if (inicial == null) "Novo torneio" else "Editar torneio", color = DominoLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do torneio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoSelecao("Data", if (data.isBlank()) "Escolher" else formatarData(data)) { escolherData(data) { data = it } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoSelecao("Início", horaInicio.ifBlank { "--:--" }, Modifier.weight(1f)) { escolherHora(horaInicio) { horaInicio = it } }
                    CampoSelecao("Fim", horaFim.ifBlank { "--:--" }, Modifier.weight(1f)) { escolherHora(horaFim) { horaFim = it } }
                }
                CampoSelecao(
                    "Títulos do ano consideram partidas até",
                    if (corte.isBlank()) "Dia anterior ao torneio" else formatarData(corte)
                ) { escolherData(corte.ifBlank { data }) { corte = it } }
                Text(
                    "Durante esse horário o cadastro de partidas fica liberado e todas as partidas vão para o torneio (sem cobrança e fora do ranking do clube).",
                    color = DominoMuted,
                    fontSize = 11.sp
                )
                erro?.let { Text(it, color = DominoError, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    erro = when {
                        nome.isBlank() -> "Informe o nome."
                        data.isBlank() -> "Escolha a data."
                        horaInicio.isBlank() || horaFim.isBlank() -> "Escolha o horário de início e fim."
                        horaFim <= horaInicio -> "O fim deve ser depois do início."
                        corte.isNotBlank() && corte > data -> "A data de corte não pode ser depois do torneio."
                        else -> null
                    }
                    if (erro == null) onConfirm(nome, data, horaInicio, horaFim, corte)
                }
            ) { Text(if (isSaving) "Salvando..." else "Salvar", color = DominoGreen, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = DominoMuted) } }
    )
}

@Composable
private fun CampoSelecao(label: String, valor: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderBeige, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = DominoMuted, fontSize = 11.sp)
        Text(valor, color = DominoLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
