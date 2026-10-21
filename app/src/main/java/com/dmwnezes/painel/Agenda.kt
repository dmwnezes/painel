package com.dmwnezes.painel

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.temporal.ChronoUnit

data class Compromisso(val titulo: String, val inicio: Instant, val diaInteiro: Boolean, val local: String?)

/** Compromissos das agendas do celular (inclui o Google Agenda sincronizado) de agora até daqui a 3 dias. */
fun carregarAgenda(ctx: Context): List<Compromisso> {
    val agora = Instant.now()
    val uri = Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, agora.minus(1, ChronoUnit.HOURS).toEpochMilli())
        ContentUris.appendId(it, agora.plus(3, ChronoUnit.DAYS).toEpochMilli())
    }.build()
    val colunas = arrayOf(Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY, Instances.EVENT_LOCATION)
    val lista = mutableListOf<Compromisso>()
    ctx.contentResolver.query(uri, colunas, null, null, "${Instances.BEGIN} ASC")?.use { c ->
        while (c.moveToNext()) {
            lista += Compromisso(
                titulo = c.getString(0)?.ifBlank { null } ?: "(sem título)",
                inicio = Instant.ofEpochMilli(c.getLong(1)),
                diaInteiro = c.getInt(2) == 1,
                local = c.getString(3)?.ifBlank { null },
            )
        }
    }
    return lista
}
