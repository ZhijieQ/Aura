package com.zhijie.aura.search.lexicon.countries

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.CountrySemanticEntries
import com.zhijie.aura.search.lexicon.metroStationEntry

internal object UnitedKingdomSemanticEntries : CountrySemanticEntries {
    override val countryCode: String = "GB"

    override val entriesByLanguage: Map<String, List<SemanticAliasEntry>> = mapOf(
        "en" to listOf(
            metroStationEntry(
                aliases = setOf(
                    "tube",
                    "underground",
                ),
            ),
        ),
    )
}

