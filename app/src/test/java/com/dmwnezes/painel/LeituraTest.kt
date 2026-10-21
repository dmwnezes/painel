package com.dmwnezes.painel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LeituraTest {
    private fun arquivo(n: String) = javaClass.classLoader!!.getResource(n).readText()

    @Test fun ajusteIdaEVolta() {
        val a = Ajuste(Tamanho.G, 4, true, 25)
        assertEquals(a, Ajuste.decodificar(a.codificar(), Secao.CLIMA.padrao))
    }

    @Test fun ajusteEstranhoVoltaAoPadrao() {
        val p = Secao.EMAILS.padrao
        assertEquals(p, Ajuste.decodificar(null, p))
        assertEquals(p, Ajuste.decodificar("lixo", p))
        // Cor fora da lista e desfoque acima do máximo não passam.
        val a = Ajuste.decodificar("M|99|1|500", p)
        assertEquals(Tamanho.M, a.tamanho)
        assertEquals(p.tom, a.tom)
        assertEquals(DESFOQUE_MAX, a.desfoque)
    }

    @Test fun agendaDoFortaleza() {
        val jogos = lerAgenda(arquivo("schedule.json"))
        assertEquals(2, jogos.size)
        val crb = jogos.last()
        assertEquals("Série B", crb.competicao)
        assertEquals("post", crb.estado)
        assertEquals("Fortaleza", crb.casa)
        assertEquals("2", crb.golsCasa)
    }

    @Test fun rssDoGeSoComFortaleza() {
        val n = lerRss(arquivo("ge.xml"), "ge")
        assertTrue(n.isNotEmpty())
        assertTrue(n.all { it.titulo.isNotBlank() && it.link.startsWith("http") })
        assertTrue(n.filter(::ehDoFortaleza).none { "/times/ceara/" in it.link })
    }

    @Test fun rssDoGoogleNoticiasTiraAFonteDoTitulo() {
        val xml = """<rss><channel><item><title>Novo jogo anunciado - Site X</title><link>https://news.google.com/a</link>
            <pubDate>Tue, 20 Oct 2026 12:00:00 GMT</pubDate><description>&lt;a href="x"&gt;Novo jogo anunciado&lt;/a&gt;</description>
            <source url="https://x.com">Site X</source></item></channel></rss>"""
        val n = lerRss(xml, "Google Notícias").single()
        assertEquals("Novo jogo anunciado", n.titulo)
        assertEquals("Site X", n.fonte)
        assertNull(n.resumo)
        assertEquals(Instant.parse("2026-10-20T12:00:00Z"), n.data)
    }

    @Test fun clima() {
        val json = """{"current":{"temperature_2m":26.6,"weather_code":0},
            "daily":{"time":["2026-10-21","2026-10-22"],"temperature_2m_max":[32.2,31.0],
            "temperature_2m_min":[23.4,22.9],"weather_code":[1,61]}}"""
        val c = lerClima(json)
        assertEquals(27, c.agora)
        assertEquals("Céu limpo", descreverTempo(c.codigo))
        assertEquals(Dia(LocalDate.parse("2026-10-22"), 31, 23, 61), c.dias[1])
        assertEquals("Chuva", descreverTempo(61))
    }

    @Test fun email() {
        val json = """{"id":"1","labelIds":["UNREAD","INBOX"],"snippet":"Ol&#39;a, sua prova &amp; trabalho",
            "payload":{"headers":[{"name":"From","value":"\"Faculdade X\" <aviso@fac.br>"},{"name":"Subject","value":"Aviso"}]}}"""
        val e = lerEmail(json)
        assertEquals("Faculdade X", e.remetente)
        assertEquals("Aviso", e.assunto)
        assertEquals("Ol'a, sua prova & trabalho", e.resumo)
        assertTrue(e.naoLido)
        assertEquals("fulano", nomeRemetente("fulano@x.com"))
        assertEquals("(sem assunto)", lerEmail("""{"payload":{"headers":[]}}""").assunto)
        assertFalse(lerEmail("""{"payload":{}}""").naoLido)
    }

    @Test fun tempos() {
        val agora = Instant.parse("2026-10-21T12:00:00Z")
        assertEquals("há 5 min", tempoRelativo(Instant.parse("2026-10-21T11:55:00Z"), agora))
        assertEquals("há 3 h", tempoRelativo(Instant.parse("2026-10-21T09:00:00Z"), agora))
        assertEquals("há 2 d", tempoRelativo(Instant.parse("2026-10-19T09:00:00Z"), agora))
        val hoje = LocalDate.parse("2026-10-21")
        assertEquals("amanhã", rotuloDia(hoje.plusDays(1), hoje))
    }
}
