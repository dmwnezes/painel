package com.dmwnezes.painel

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.White, background = Noite, surface = Color(0xFF151A2A),
                    onBackground = Color.White, onSurface = Color.White,
                ),
            ) {
                ProvideTextStyle(LocalTextStyle.current.copy(color = Color.White)) { App() }
            }
        }
    }
}

sealed interface Carga<out T> {
    data object Carregando : Carga<Nothing>
    data class Ok<T>(val v: T) : Carga<T>
    data class Erro(val msg: String) : Carga<Nothing>
    /** Falta o Daniel autorizar (login do Gmail, permissão da agenda). */
    data object Conectar : Carga<Nothing>
}

val ptBR: Locale = Locale.forLanguageTag("pt-BR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val prefs = remember { Preferencias(ctx) }
    val escopo = rememberCoroutineScope()

    val ajustes = remember { mutableStateMapOf<Secao, Ajuste>().apply { Secao.entries.forEach { put(it, prefs.ajuste(it)) } } }
    var desfoqueFundo by remember { mutableIntStateOf(prefs.desfoqueFundo) }
    var tela by remember { mutableStateOf(Size.Zero) }
    var editando by remember { mutableStateOf<Secao?>(null) }
    var editandoFundo by remember { mutableStateOf(false) }

    var clima by remember { mutableStateOf<Carga<Clima>>(Carga.Carregando) }
    var emails by remember { mutableStateOf<Carga<List<Email>>>(Carga.Carregando) }
    var noticias by remember { mutableStateOf<Carga<List<Noticia>>>(Carga.Carregando) }
    var jogos by remember { mutableStateOf<Carga<List<Jogo>>>(Carga.Carregando) }
    var noticiasLeao by remember { mutableStateOf<Carga<List<Noticia>>>(Carga.Carregando) }
    var games by remember { mutableStateOf<Carga<List<Noticia>>>(Carga.Carregando) }
    var agenda by remember { mutableStateOf<Carga<List<Compromisso>>>(Carga.Carregando) }
    var atualizando by remember { mutableStateOf(false) }
    var rodada by remember { mutableIntStateOf(0) }

    suspend fun <T> buscar(bloco: suspend () -> T): Carga<T> =
        runCatching { withContext(Dispatchers.IO) { bloco() } }
            .fold({ Carga.Ok(it) }, { Carga.Erro(it.message ?: "erro") })

    fun lerEmails(token: String) = escopo.launch {
        emails = Carga.Carregando
        emails = buscar { carregarEmails(token) }
    }

    val loginGmail = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = tokenDoRetorno(activity, r.data)
        if (token != null) {
            prefs.gmailConectado = true
            lerEmails(token)
        } else emails = Carga.Conectar
    }

    /** `interativo`: o Daniel tocou em conectar, então pode abrir a tela do Google. */
    fun conectarGmail(interativo: Boolean) = escopo.launch {
        if (!interativo && !prefs.gmailConectado) { emails = Carga.Conectar; return@launch }
        runCatching { pedirAcessoGmail(activity) }
            .onSuccess { a ->
                when (a) {
                    is Acesso.Token -> { prefs.gmailConectado = true; lerEmails(a.valor) }
                    is Acesso.PrecisaLogin ->
                        if (interativo) loginGmail.launch(IntentSenderRequest.Builder(a.tela.intentSender).build())
                        else emails = Carga.Conectar
                }
            }
            .onFailure { emails = Carga.Erro(it.message ?: "erro") }
    }

    fun temAgenda() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun lerAgenda() = escopo.launch { agenda = if (temAgenda()) buscar { carregarAgenda(ctx) } else Carga.Conectar }
    val pedirAgenda = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { lerAgenda() }

    LaunchedEffect(rodada) {
        atualizando = true
        val tarefas = listOf(
            launch { clima = buscar { carregarClima() } },
            launch { noticias = buscar { carregarImportantes() } },
            launch { jogos = buscar { carregarJogos() } },
            launch { noticiasLeao = buscar { carregarNoticiasFortaleza() } },
            launch { games = buscar { carregarGames() } },
            lerAgenda(),
            conectarGmail(interativo = false),
        )
        tarefas.forEach { it.join() }
        atualizando = false
    }

    CompositionLocalProvider(LocalFundo provides InfoFundo(tela, desfoqueFundo)) {
        Box(Modifier.fillMaxSize().onSizeChanged { tela = Size(it.width.toFloat(), it.height.toFloat()) }) {
            Fundo(Modifier.matchParentSize())
            PullToRefreshBox(isRefreshing = atualizando, onRefresh = { rodada++ }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = WindowInsets.systemBars.asPaddingValues().let {
                        PaddingValues(start = 16.dp, end = 16.dp, top = it.calculateTopPadding() + 16.dp, bottom = it.calculateBottomPadding() + 24.dp)
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { GridItemSpan(2) }) { Topo { editandoFundo = true } }
                    items(Secao.entries, key = { it.name }, span = { GridItemSpan(ajustes.getValue(it).tamanho.colunas) }) { s ->
                        val a = ajustes.getValue(s)
                        Bloco(s, a, onAjustar = { editando = s }) {
                            when (s) {
                                Secao.CLIMA -> BlocoClima(clima, a)
                                Secao.EMAILS -> BlocoEmails(emails, a) { conectarGmail(interativo = true) }
                                Secao.NOTICIAS -> BlocoNoticias(noticias, a)
                                Secao.FORTALEZA -> BlocoFortaleza(jogos, noticiasLeao, a)
                                Secao.GAMES -> BlocoNoticias(games, a)
                                Secao.AGENDA -> BlocoAgenda(agenda, a) { pedirAgenda.launch(Manifest.permission.READ_CALENDAR) }
                                Secao.ATALHOS -> BlocoAtalhos(a)
                            }
                        }
                    }
                }
            }
        }

        editando?.let { s ->
            ModalBottomSheet(onDismissRequest = { editando = null }, containerColor = Color(0xFF151A2A)) {
                Personalizar(s, ajustes.getValue(s)) { novo -> ajustes[s] = novo; prefs.salvar(s, novo) }
            }
        }
        if (editandoFundo) {
            ModalBottomSheet(onDismissRequest = { editandoFundo = false }, containerColor = Color(0xFF151A2A)) {
                AjustesFundo(
                    desfoque = desfoqueFundo,
                    mudarDesfoque = { desfoqueFundo = it; prefs.desfoqueFundo = it },
                    gmailConectado = emails !is Carga.Conectar && prefs.gmailConectado,
                    conectarGmail = { conectarGmail(interativo = true) },
                )
            }
        }
    }
}

@Composable
private fun Topo(abrirAjustes: () -> Unit) {
    val hora = LocalTime.now().hour
    val saudacao = when {
        hora < 12 -> "Bom dia"
        hora < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
    val data = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR))
    Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(data, color = TextoSuave, fontSize = 13.sp)
            Text("$saudacao, Daniel", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        IconButton(onClick = abrirAjustes) { Icon(Icons.Rounded.Settings, "Fundo e contas", tint = Color.White) }
    }
}

@Composable
private fun Personalizar(s: Secao, a: Ajuste, mudar: (Ajuste) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Personalizar · ${s.titulo}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

        Rotulo("Tamanho")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tamanho.entries.forEach { t ->
                FilterChip(selected = a.tamanho == t, onClick = { mudar(a.copy(tamanho = t)) }, label = { Text(t.nome) })
            }
        }

        Rotulo("Cor")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TONS.forEachIndexed { i, tom ->
                val escolhido = a.tom == i
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(tom.forte.copy(alpha = if (i == 0) 0.25f else 0.85f))
                        .border(if (escolhido) 3.dp else 1.dp, if (escolhido) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { mudar(a.copy(tom = i)) }
                        .semantics { contentDescription = tom.nome + if (escolhido) ", escolhida" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (escolhido) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Rotulo("Exibição")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !a.detalhado, onClick = { mudar(a.copy(detalhado = false)) }, label = { Text("Simples") })
            FilterChip(selected = a.detalhado, onClick = { mudar(a.copy(detalhado = true)) }, label = { Text("Detalhada") })
        }

        Rotulo("Desfoque do bloco · ${a.desfoque}")
        Slider(
            value = a.desfoque.toFloat(), onValueChange = { mudar(a.copy(desfoque = it.toInt())) },
            valueRange = 0f..DESFOQUE_MAX.toFloat(),
        )
    }
}

@Composable
private fun AjustesFundo(desfoque: Int, mudarDesfoque: (Int) -> Unit, gmailConectado: Boolean, conectarGmail: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Fundo e contas", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Rotulo("Desfoque do fundo · $desfoque")
        Slider(value = desfoque.toFloat(), onValueChange = { mudarDesfoque(it.toInt()) }, valueRange = 0f..DESFOQUE_FUNDO_MAX.toFloat())
        Rotulo("Gmail")
        if (gmailConectado) Text("Conectado", color = TextoSuave)
        else OutlinedButton(onClick = conectarGmail) { Text("Conectar Gmail") }
        Text("Toque no ícone de ajuste de cada bloco para mudar tamanho, cor, exibição e desfoque.", color = TextoSuave, fontSize = 13.sp)
    }
}

@Composable
private fun Rotulo(texto: String) = Text(texto, color = TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
