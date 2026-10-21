package com.dmwnezes.painel

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class Tamanho(val nome: String, val colunas: Int, val itens: Int) {
    P("Pequeno", 1, 2),
    M("Médio", 2, 3),
    G("Grande", 2, 6),
}

/** Tons de vidro. `forte` é o tom do título e do vidro; a borda e o preenchimento saem dele com transparência. */
data class Tom(val nome: String, val forte: Color, val claro: Color)

val TONS = listOf(
    Tom("Vidro", Color(0xFFFFFFFF), Color(0xFFDDE3F0)),
    Tom("Âmbar", Color(0xFFFFD43B), Color(0xFFFFE27A)),
    Tom("Azul", Color(0xFF4C6EF5), Color(0xFFA5B8FF)),
    Tom("Vermelho", Color(0xFFF03E3E), Color(0xFFFFA8A8)),
    Tom("Roxo", Color(0xFF845EF7), Color(0xFFC6B4FF)),
    Tom("Verde", Color(0xFF12B886), Color(0xFF8CE9CB)),
    Tom("Rosa", Color(0xFFE64980), Color(0xFFFFADD2)),
    Tom("Ciano", Color(0xFF15AABF), Color(0xFF99E9F2)),
)

const val DESFOQUE_MAX = 40
const val DESFOQUE_FUNDO_MAX = 120

data class Ajuste(val tamanho: Tamanho, val tom: Int, val detalhado: Boolean, val desfoque: Int) {
    fun codificar() = "${tamanho.name}|$tom|${if (detalhado) 1 else 0}|$desfoque"

    companion object {
        /** Lê o formato salvo; qualquer coisa estranha volta para o padrão da seção. */
        fun decodificar(s: String?, padrao: Ajuste): Ajuste {
            val p = s?.split('|') ?: return padrao
            if (p.size != 4) return padrao
            return Ajuste(
                tamanho = Tamanho.entries.firstOrNull { it.name == p[0] } ?: padrao.tamanho,
                tom = p[1].toIntOrNull()?.takeIf { it in TONS.indices } ?: padrao.tom,
                detalhado = p[2] == "1",
                desfoque = p[3].toIntOrNull()?.coerceIn(0, DESFOQUE_MAX) ?: padrao.desfoque,
            )
        }
    }
}

enum class Secao(val titulo: String, val padrao: Ajuste) {
    CLIMA("Clima", Ajuste(Tamanho.P, 1, false, 18)),
    EMAILS("E-mails", Ajuste(Tamanho.P, 2, false, 18)),
    NOTICIAS("Importante hoje", Ajuste(Tamanho.M, 0, false, 18)),
    FORTALEZA("Fortaleza", Ajuste(Tamanho.P, 3, false, 18)),
    GAMES("Games", Ajuste(Tamanho.P, 4, false, 18)),
    AGENDA("Agenda", Ajuste(Tamanho.P, 5, false, 18)),
    ATALHOS("Atalhos", Ajuste(Tamanho.P, 0, false, 18)),
}

class Preferencias(ctx: Context) {
    private val sp = ctx.getSharedPreferences("painel", Context.MODE_PRIVATE)

    fun ajuste(s: Secao) = Ajuste.decodificar(sp.getString("secao.${s.name}", null), s.padrao)
    fun salvar(s: Secao, a: Ajuste) = sp.edit().putString("secao.${s.name}", a.codificar()).apply()

    var desfoqueFundo: Int
        get() = sp.getInt("fundo.desfoque", 80)
        set(v) = sp.edit().putInt("fundo.desfoque", v).apply()

    var gmailConectado: Boolean
        get() = sp.getBoolean("gmail.conectado", false)
        set(v) = sp.edit().putBoolean("gmail.conectado", v).apply()
}
