package com.dmwnezes.painel

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Mesma fonte do app Leão. shortcut: API pública não oficial da ESPN, sem chave; se mudar o formato, ajustar aqui.
private const val FORTALEZA_ID = "6272"
private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/all"

data class Jogo(
    val competicao: String,
    val inicio: Instant,
    val estado: String, // pre, in, post
    val casa: String,
    val fora: String,
    val golsCasa: String?,
    val golsFora: String?,
)

/** Resultados + próximos jogos do Fortaleza, em ordem de data. */
fun carregarJogos(): List<Jogo> {
    val passados = lerAgenda(baixar("$BASE/teams/$FORTALEZA_ID/schedule"))
    val futuros = runCatching { lerAgenda(baixar("$BASE/teams/$FORTALEZA_ID/schedule?fixture=true")) }.getOrDefault(emptyList())
    return (passados + futuros).distinctBy { it.inicio to it.casa }.sortedBy { it.inicio }
}

fun lerAgenda(json: String): List<Jogo> {
    val evs = JSONObject(json).optJSONArray("events") ?: return emptyList()
    return (0 until evs.length()).map { i ->
        val e = evs.getJSONObject(i)
        val liga = e.optJSONObject("league")
        val c = e.getJSONArray("competitions").getJSONObject(0)
        val cs = c.getJSONArray("competitors")
        val lados = (0 until cs.length()).map { cs.getJSONObject(it) }
        val casa = lados.first { it.optString("homeAway") == "home" }
        val fora = lados.first { it.optString("homeAway") == "away" }
        val estado = c.getJSONObject("status").getJSONObject("type").optString("state", "pre")
        Jogo(
            competicao = nomeCompeticao(liga?.optString("slug").orEmpty(), liga?.optString("name").orEmpty()),
            inicio = lerData(c.getString("date")),
            estado = estado,
            casa = nomeTime(casa),
            fora = nomeTime(fora),
            golsCasa = if (estado == "pre") null else gols(casa),
            golsFora = if (estado == "pre") null else gols(fora),
        )
    }
}

private fun nomeTime(x: JSONObject): String {
    val t = x.getJSONObject("team")
    return t.optString("shortDisplayName").ifBlank { t.optString("displayName") }
}

private fun gols(x: JSONObject): String? =
    x.optJSONObject("score")?.optString("displayValue") ?: x.optString("score").ifBlank { null }

// ESPN manda "2026-10-16T23:30Z" (sem segundos); Instant.parse não aceita.
fun lerData(s: String): Instant = OffsetDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]X")).toInstant()

fun nomeCompeticao(slug: String, nome: String) = when (slug) {
    "bra.1" -> "Série A"
    "bra.2" -> "Série B"
    "bra.copa_do_brazil" -> "Copa do Brasil"
    "bra.copa_do_nordeste" -> "Copa do Nordeste"
    "conmebol.sudamericana" -> "Sul-Americana"
    "conmebol.libertadores" -> "Libertadores"
    else -> nome
}
