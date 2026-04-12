package com.zhijie.aura.search.lexicon.generic

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.metroStationEntry

internal object GenericSemanticEntries {
    private val metroGenericEntry = metroStationEntry(
        aliases = setOf(
            "metro",
            "subway",
            "underground",
            "u bahn",
            "u-bahn",
            "地铁",
            "地下铁",
            "metró",
            "métro",
            "metropolitano",
        ),
    )

    val entriesByLanguage: Map<String, List<SemanticAliasEntry>> = mapOf(
        "default" to listOf(metroGenericEntry),
        "en" to listOf(metroGenericEntry),
        "es" to listOf(metroGenericEntry),
        "zh" to listOf(metroGenericEntry),
        "de" to listOf(metroGenericEntry),
        "fr" to listOf(metroGenericEntry),
    )
}

