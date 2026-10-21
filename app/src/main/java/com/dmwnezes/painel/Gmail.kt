package com.dmwnezes.painel

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.URLEncoder

data class Email(val remetente: String, val assunto: String, val resumo: String, val naoLido: Boolean)

/** Ou já temos o token, ou o Google precisa mostrar a tela de login/permissão. */
sealed interface Acesso {
    data class Token(val valor: String) : Acesso
    data class PrecisaLogin(val tela: PendingIntent) : Acesso
}

// O Google casa o app com o cliente OAuth Android pelo nome do pacote + SHA-1 da chave; não há ID no código.
suspend fun pedirAcessoGmail(activity: Activity): Acesso {
    // Montado aqui, não no topo do arquivo: as classes do Google Play não existem nos testes de JVM.
    val pedido = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/gmail.readonly")))
        .build()
    val r = Identity.getAuthorizationClient(activity).authorize(pedido).await()
    val tela = r.pendingIntent
    return if (r.hasResolution() && tela != null) Acesso.PrecisaLogin(tela) else Acesso.Token(r.accessToken!!)
}

fun tokenDoRetorno(activity: Activity, dados: Intent?): String? =
    runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(dados).accessToken }.getOrNull()

private const val API = "https://gmail.googleapis.com/gmail/v1/users/me/messages"

/** E-mails da caixa de entrada nas últimas 24 h. */
fun carregarEmails(token: String): List<Email> {
    val q = URLEncoder.encode("in:inbox newer_than:1d", "UTF-8")
    val ids = JSONObject(baixar("$API?q=$q&maxResults=15", token)).optJSONArray("messages") ?: return emptyList()
    return (0 until ids.length()).map { i ->
        val id = ids.getJSONObject(i).getString("id")
        lerEmail(baixar("$API/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject", token))
    }
}

fun lerEmail(json: String): Email {
    val m = JSONObject(json)
    val cab = m.getJSONObject("payload").optJSONArray("headers")
    fun cabecalho(nome: String) = (0 until (cab?.length() ?: 0)).map { cab!!.getJSONObject(it) }
        .firstOrNull { it.optString("name").equals(nome, ignoreCase = true) }?.optString("value").orEmpty()
    val rotulos = m.optJSONArray("labelIds")
    return Email(
        remetente = nomeRemetente(cabecalho("From")),
        assunto = cabecalho("Subject").ifBlank { "(sem assunto)" },
        resumo = desfazerEntidades(m.optString("snippet")),
        naoLido = (0 until (rotulos?.length() ?: 0)).any { rotulos!!.getString(it) == "UNREAD" },
    )
}

// "Fulano de Tal <fulano@x.com>" -> "Fulano de Tal"; só o e-mail -> parte antes do @.
fun nomeRemetente(de: String): String {
    val nome = de.substringBefore('<').trim().trim('"')
    return nome.ifBlank { de.trim('<', '>', ' ').substringBefore('@') }
}

private fun desfazerEntidades(s: String) = s
    .replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
