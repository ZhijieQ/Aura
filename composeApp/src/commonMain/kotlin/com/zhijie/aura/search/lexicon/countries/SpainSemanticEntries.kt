package com.zhijie.aura.search.lexicon.countries

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.CountrySemanticEntries
import com.zhijie.aura.search.lexicon.metroStationEntry

internal object SpainSemanticEntries : CountrySemanticEntries {
    override val countryCode: String = "ES"

    override val entriesByLanguage: Map<String, List<SemanticAliasEntry>> = mapOf(
        "es" to listOf(
            metroStationEntry(
                aliases = setOf(
                    "metro madrid",
                    "metro de madrid",
                    "metropolitano",
                ),
            ),
        ),
    )
}

