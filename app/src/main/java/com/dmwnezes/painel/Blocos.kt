package com.dmwnezes.painel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun Bloco(s: Secao, a: Ajuste, onAjustar: () -> Unit, conteudo: @Composable ColumnScope.() -> Unit) {
    val tom = TONS[a.tom]
    Vidro(tom, a.desfoque, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.titulo, color = tom.claro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAjustar, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Tune, "Personalizar ${s.titulo}", tint = TextoSuave, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = conteudo)
        }
    }
}

@Composable
private fun <T> Estado(c: Carga<T>, conectar: (@Composable () -> Unit)? = null, ok: @Composable (T) -> Unit) {
    when (c) {
        Carga.Carregando -> Text("Carregando…", color = TextoSuave, fontSize = 13.sp)
        is Carga.Erro -> Text("Não deu para carregar. Puxe a tela para tentar de novo.", color = TextoSuave, fontSize = 13.sp)
        Carga.Conectar -> conectar?.invoke()
        is Carga.Ok -> ok(c.v)
    }
}

// ---------- Clima ----------

@Composable
fun BlocoClima(c: Carga<Clima>, a: Ajuste) = Estado(c) { cl ->
    Text("${cl.agora}°", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp)
    Text(descreverTempo(cl.codigo), color = TextoSuave, fontSize = 13.sp)
    if (a.detalhado) {
        cl.dias.firstOrNull()?.let { Text("máx ${it.max}° · mín ${it.min}°", color = TextoSuave, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            cl.dias.drop(1).take(if (a.tamanho.colunas == 2) 5 else 3).forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.data.format(DateTimeFormatter.ofPattern("EEE", ptBR)).removeSuffix("."), color = TextoSuave, fontSize = 12.sp)
                    Text("${d.max}°", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${d.min}°", color = TextoSuave, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------- E-mails ----------

@Composable
fun BlocoEmails(c: Carga<List<Email>>, a: Ajuste, conectar: () -> Unit) {
    val ctx = LocalContext.current
    Estado(c, conectar = { OutlinedButton(onClick = conectar) { Text("Conectar Gmail") } }) { lista ->
        Column(Modifier.clickable { abrirGmail(ctx) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lista.isEmpty()) {
                Text("Nada nas últimas 24 h", color = TextoSuave, fontSize = 14.sp)
                return@Column
            }
            if (!a.detalhado) {
                Text("${lista.size}", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp)
                val nomes = lista.map { it.remetente }.distinct()
                Text(
                    nomes.take(a.tamanho.itens * 2).joinToString(", ") + if (nomes.size > a.tamanho.itens * 2) "…" else "",
                    color = TextoSuave, fontSize = 13.sp,
                )
            } else {
                lista.take(a.tamanho.itens).forEach { e ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e.naoLido) Box(Modifier.padding(end = 6.dp).size(7.dp).clip(CircleShape).background(Color.White))
                            Text(e.remetente, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(e.assunto, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.resumo, color = TextoSuave, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (lista.size > a.tamanho.itens) Text("+ ${lista.size - a.tamanho.itens} e-mails", color = TextoSuave, fontSize = 12.sp)
            }
        }
    }
}

// ---------- Notícias e games ----------

@Composable
fun BlocoNoticias(c: Carga<List<Noticia>>, a: Ajuste) {
    val ctx = LocalContext.current
    Estado(c) { lista ->
        if (lista.isEmpty()) Text("Sem notícias agora", color = TextoSuave, fontSize = 13.sp)
        lista.take(a.tamanho.itens).forEach { n ->
            Row(Modifier.fillMaxWidth().clickable { abrir(ctx, n.link) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (a.detalhado && n.imagem != null) {
                    val lado = if (a.tamanho.colunas == 2) 64.dp else 44.dp
                    AsyncImage(n.imagem, null, Modifier.size(lado).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f)) {
                    Text(n.titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (a.detalhado && n.resumo != null) {
                        Text(n.resumo, color = TextoSuave, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Text(listOfNotNull(n.fonte, tempoRelativo(n.data)).joinToString(" · "), color = TextoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- Fortaleza ----------

@Composable
fun BlocoFortaleza(jogos: Carga<List<Jogo>>, noticias: Carga<List<Noticia>>, a: Ajuste) {
    val ctx = LocalContext.current
    Estado(jogos) { lista ->
        val aoVivo = lista.firstOrNull { it.estado == "in" }
        val proximo = aoVivo ?: lista.firstOrNull { it.estado == "pre" }
        val ultimo = lista.lastOrNull { it.estado == "post" }
        Column(Modifier.clickable { abrirApp(ctx, ATALHOS.first { it.nome == "Leão" }) }) {
            if (proximo == null) Text("Sem jogo marcado", color = TextoSuave, fontSize = 13.sp)
            else {
                Text("${proximo.casa} x ${proximo.fora}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (aoVivo != null) "AO VIVO · ${aoVivo.golsCasa} x ${aoVivo.golsFora}"
                    else "${dataJogo(proximo.inicio)} · ${proximo.competicao}",
                    color = TextoSuave, fontSize = 13.sp,
                )
            }
            if (a.detalhado && ultimo != null) {
                Spacer(Modifier.height(6.dp))
                Text("Último: ${ultimo.casa} ${ultimo.golsCasa} x ${ultimo.golsFora} ${ultimo.fora}", fontSize = 13.sp)
            }
        }
        if (a.detalhado) {
            (noticias as? Carga.Ok)?.v?.take(a.tamanho.itens - 1)?.forEach { n ->
                Text(
                    n.titulo, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { abrir(ctx, n.link) },
                )
            }
        }
    }
}

// ---------- Agenda ----------

@Composable
fun BlocoAgenda(c: Carga<List<Compromisso>>, a: Ajuste, permitir: () -> Unit) {
    val ctx = LocalContext.current
    Estado(c, conectar = { OutlinedButton(onClick = permitir) { Text("Permitir agenda") } }) { lista ->
        Column(Modifier.clickable { abrirAgenda(ctx) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (lista.isEmpty()) Text("Nada nos próximos dias", color = TextoSuave, fontSize = 13.sp)
            lista.take(a.tamanho.itens).forEach { e ->
                val quando = e.inicio.atZone(ZoneId.systemDefault())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (e.diaInteiro) "dia" else quando.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Column {
                        Text(e.titulo, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val dia = rotuloDia(quando.toLocalDate())
                        if (a.detalhado) {
                            Text(listOfNotNull(dia, e.local).joinToString(" · "), color = TextoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else if (dia != "hoje") Text(dia, color = TextoSuave, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------- Atalhos ----------

data class Atalho(val nome: String, val descricao: String, val pacote: String, val site: String)

val ATALHOS = listOf(
    Atalho("Palavreiro", "Jogo de palavras", "com.dmwnezes.palavreiro", "https://dmwnezes.com/termo/"),
    Atalho("Matinê", "Filmes juntos", "com.dmwnezes.matine", "https://dmwnezes.com/matine/"),
    Atalho("Leão", "Jogos do Fortaleza", "com.dmwnezes.leao", "https://github.com/dmwnezes/leao/releases/latest"),
    Atalho("Estante", "Vídeos do Drive", "com.dmwnezes.estante", "https://github.com/dmwnezes/estante/releases/latest"),
    Atalho("Sintonia", "Letras do Spotify", "com.dmwnezes.sintonia", "https://github.com/dmwnezes/spotify-letras/releases/latest"),
    Atalho("Soberano", "Governe um país", "com.dmwnezes.soberano", "https://github.com/dmwnezes/soberano/releases/latest"),
)

@Composable
fun BlocoAtalhos(a: Ajuste) {
    val ctx = LocalContext.current
    val porLinha = if (a.tamanho.colunas == 1) 2 else 3
    ATALHOS.chunked(porLinha).forEach { linha ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            linha.forEach { at ->
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                        .clickable { abrirApp(ctx, at) }.padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(at.nome, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (a.detalhado) Text(at.descricao, color = TextoSuave, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(porLinha - linha.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ---------- Ajudantes ----------

fun abrir(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Abre o app se estiver instalado; senão, o site ou a página de download. */
fun abrirApp(ctx: Context, at: Atalho) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(at.pacote)
    if (intent != null) ctx.startActivity(intent) else abrir(ctx, at.site)
}

private fun abrirGmail(ctx: Context) {
    val intent = ctx.packageManager.getLaunchIntentForPackage("com.google.android.gm")
    if (intent != null) ctx.startActivity(intent) else abrir(ctx, "https://mail.google.com/")
}

private fun abrirAgenda(ctx: Context) {
    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

fun tempoRelativo(i: Instant?, agora: Instant = Instant.now()): String? {
    if (i == null) return null
    val min = Duration.between(i, agora).toMinutes().coerceAtLeast(0)
    return when {
        min < 60 -> "há $min min"
        min < 60 * 24 -> "há ${min / 60} h"
        else -> "há ${min / (60 * 24)} d"
    }
}

fun rotuloDia(d: LocalDate, hoje: LocalDate = LocalDate.now()) = when (d) {
    hoje -> "hoje"
    hoje.plusDays(1) -> "amanhã"
    else -> d.format(DateTimeFormatter.ofPattern("EEEE", ptBR))
}

private fun dataJogo(i: Instant): String {
    val z = i.atZone(ZoneId.systemDefault())
    return rotuloDia(z.toLocalDate()) + " · " + z.format(DateTimeFormatter.ofPattern("HH'h'mm")).removeSuffix("00")
}
