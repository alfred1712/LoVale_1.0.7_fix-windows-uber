package com.example.lovale2.domain

import java.text.Normalizer
import java.util.Locale

object ZoneAutocomplete {
    fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
        .replace(Regex("\\s+"), " ")

    fun search(query: String, catalog: List<String>, excluded: List<String>): List<String> {
        val term = key(query)
        if (term.length < 3) return emptyList()
        val blocked = excluded.map(::key).toSet()
        return catalog.asSequence().filter { key(it) !in blocked }
            .filter { label ->
                val name = key(label.substringBefore("("))
                name.startsWith(term) || name.split(" ").indices.any { i ->
                    name.split(" ").drop(i).joinToString(" ").startsWith(term)
                }
            }
            .distinctBy(::key)
            .sortedWith(compareBy<String> { !key(it).startsWith(term) }.thenBy { key(it) })
            .take(20).toList()
    }
}
