package com.dmwnezes.painel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

val Noite = Color(0xFF0A0E1A)
val TextoSuave = Color.White.copy(alpha = 0.74f)

/** Tamanho da tela e desfoque do fundo: cada bloco precisa deles para desenhar o vidro. */
data class InfoFundo(val tela: Size, val desfoque: Int)

val LocalFundo = compositionLocalOf { InfoFundo(Size.Zero, 80) }

/** As três manchas de cor do fundo, desenhadas nítidas; o desfoque vem de fora. */
fun DrawScope.manchas(tela: Size) {
    val w = tela.width
    val h = tela.height
    if (w == 0f) return
    drawCircle(Color(0xFF3B5BDB).copy(alpha = 0.6f), radius = w * 0.42f, center = Offset(w * 0.08f, h * 0.06f))
    drawCircle(Color(0xFFC2255C).copy(alpha = 0.5f), radius = w * 0.40f, center = Offset(w * 1.02f, h * 0.50f))
    drawCircle(Color(0xFF0C8599).copy(alpha = 0.5f), radius = w * 0.38f, center = Offset(w * 0.18f, h * 0.96f))
}

@Composable
fun Fundo(modifier: Modifier = Modifier) {
    val f = LocalFundo.current
    Box(modifier.background(Noite)) {
        Canvas(Modifier.matchParentSize().blur(f.desfoque.dp, BlurredEdgeTreatment.Unbounded)) { manchas(f.tela) }
    }
}

/**
 * Bloco de vidro: por trás do conteúdo desenha a mesma parte do fundo que ele cobre, com o desfoque do
 * fundo somado ao do bloco. A posição é lida só na hora de desenhar, então rolar a tela não recompõe nada.
 */
@Composable
fun Vidro(tom: Tom, desfoque: Int, modifier: Modifier = Modifier, conteudo: @Composable BoxScope.() -> Unit) {
    val f = LocalFundo.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val forma = RoundedCornerShape(24.dp)
    // Desfoques em sequência somam como raiz da soma dos quadrados.
    val total = Math.sqrt((f.desfoque * f.desfoque + desfoque * desfoque).toDouble()).toFloat()
    Box(modifier.onGloballyPositioned { pos = it.positionInRoot() }.clip(forma)) {
        Canvas(Modifier.matchParentSize().background(Noite).blur(total.dp, BlurredEdgeTreatment.Rectangle)) {
            translate(-pos.x, -pos.y) { manchas(f.tela) }
        }
        Box(
            Modifier.matchParentSize()
                .background(tom.forte.copy(alpha = if (tom.forte == Color.White) 0.08f else 0.18f))
                .border(1.dp, tom.forte.copy(alpha = if (tom.forte == Color.White) 0.16f else 0.40f), forma),
        )
        conteudo()
    }
}
