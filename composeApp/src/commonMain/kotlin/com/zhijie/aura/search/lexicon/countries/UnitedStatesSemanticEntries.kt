package com.zhijie.aura.search.lexicon.countries

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.CountrySemanticEntries
import com.zhijie.aura.search.lexicon.metroStationEntry

internal object UnitedStatesSemanticEntries : CountrySemanticEntries {
    override val countryCode: String = "US"

    override val entriesByLanguage: Map<String, List<SemanticAliasEntry>> = mapOf(
        "en" to listOf(
            metroStationEntry(
                aliases = setOf("subway"),
            ),
        ),
    )
}

