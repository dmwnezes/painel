package com.dmwnezes.painel

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

private val http = OkHttpClient()

fun baixar(url: String, token: String? = null): String {
    val req = Request.Builder().url(url).header("User-Agent", "Painel/1.0")
    if (token != null) req.header("Authorization", "Bearer $token")
    return http.newCall(req.build()).execute().use {
        check(it.isSuccessful) { "HTTP ${it.code}" }
        it.body!!.string()
    }
}

// ---------- Notícias (RSS) ----------

data class Noticia(
    val titulo: String,
    val resumo: String?,
    val fonte: String,
    val link: String,
    val imagem: String?,
    val data: Instant?,
)

private val IMPORTANTES = listOf(
    "g1" to "https://g1.globo.com/rss/g1/",
    "BBC Brasil" to "https://feeds.bbci.co.uk/portuguese/rss.xml",
)
// shortcut: busca do Google News (sem foto); trocar por um feed de site de games se ele quiser imagens.
private const val GAMES = "https://news.google.com/rss/search?q=videogames+OR+games+lan%C3%A7amento&hl=pt-BR&gl=BR&ceid=BR:pt-419"
private val FORTALEZA = listOf(
    "ge" to "https://ge.globo.com/rss/ge/ce/",
    "O Povo" to "https://www.opovo.com.br/esportes/rss.xml",
)

fun carregarImportantes() = juntarFontes(IMPORTANTES)
fun carregarGames() = lerRss(baixar(GAMES), "Google Notícias").take(20)
fun carregarNoticiasFortaleza() = juntarFontes(FORTALEZA) { ehDoFortaleza(it) }

private fun juntarFontes(fontes: List<Pair<String, String>>, filtro: (Noticia) -> Boolean = { true }): List<Noticia> {
    val porFonte = fontes.map { (nome, url) -> runCatching { lerRss(baixar(url), nome) } }
    if (porFonte.all { it.isFailure }) throw porFonte.first().exceptionOrNull()!!
    return porFonte.mapNotNull { it.getOrNull() }.flatten().filter(filtro)
        .distinctBy { it.link }.sortedByDescending { it.data }.take(20)
}

fun ehDoFortaleza(n: Noticia) =
    "/times/fortaleza/" in n.link || (Regex("\\bFortaleza\\b").containsMatchIn(n.titulo) && "/times/" !in n.link)

fun lerRss(xml: String, fonte: String): List<Noticia> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    val itens = doc.getElementsByTagName("item")
    return (0 until itens.length).map { i ->
        val it = itens.item(i) as Element
        // Google News manda a fonte em <source> e repete no fim do título ("Manchete - Site").
        val origem = it.texto("source")
        val titulo = it.texto("title").orEmpty().let { t -> if (origem != null) t.removeSuffix(" - $origem") else t }
        val descricao = it.texto("description").orEmpty()
        val imagem = it.filho("enclosure")?.getAttribute("url")?.ifBlank { null }
            ?: it.filho("content")?.getAttribute("url")?.ifBlank { null }
            ?: Regex("<img[^>]+src=\"([^\"]+)\"").find(descricao + it.texto("encoded").orEmpty())?.groupValues?.get(1)
        val resumo = (it.texto("subtitle") ?: limparHtml(descricao)).take(200).ifBlank { null }
            ?.takeIf { r -> r != titulo && !r.startsWith(titulo) }
        Noticia(
            titulo = titulo,
            resumo = resumo,
            fonte = origem ?: fonte,
            link = it.texto("link").orEmpty(),
            imagem = imagem,
            data = it.texto("pubDate")?.let { d -> runCatching { ZonedDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() },
        )
    }
}

private fun limparHtml(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

// Busca o filho pelo nome sem prefixo ("media:content" -> "content"), sem depender de namespace.
private fun Element.filho(nome: String): Element? {
    val nos = childNodes
    for (i in 0 until nos.length) {
        val n = nos.item(i)
        if (n is Element && n.tagName.substringAfter(':') == nome) return n
    }
    return null
}

private fun Element.texto(nome: String) = filho(nome)?.textContent?.trim()?.ifBlank { null }

// ---------- Clima (Open-Meteo, sem chave) ----------

data class Dia(val data: LocalDate, val max: Int, val min: Int, val codigo: Int)
data class Clima(val agora: Int, val codigo: Int, val dias: List<Dia>)

private const val CLIMA = "https://api.open-meteo.com/v1/forecast?latitude=-4.10&longitude=-38.49" +
    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code" +
    "&timezone=America%2FFortaleza&forecast_days=6"

fun carregarClima() = lerClima(baixar(CLIMA))

fun lerClima(json: String): Clima {
    val raiz = JSONObject(json)
    val atual = raiz.getJSONObject("current")
    val d = raiz.getJSONObject("daily")
    val datas = d.getJSONArray("time")
    return Clima(
        agora = Math.round(atual.getDouble("temperature_2m")).toInt(),
        codigo = atual.getInt("weather_code"),
        dias = (0 until datas.length()).map { i ->
            Dia(
                LocalDate.parse(datas.getString(i)),
                Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
                Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)).toInt(),
                d.getJSONArray("weather_code").getInt(i),
            )
        },
    )
}

// Códigos WMO do Open-Meteo.
fun descreverTempo(codigo: Int) = when (codigo) {
    0 -> "Céu limpo"
    1, 2 -> "Poucas nuvens"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    in 51..57 -> "Garoa"
    in 61..67, in 80..82 -> "Chuva"
    in 95..99 -> "Trovoadas"
    else -> "Tempo variável"
}
