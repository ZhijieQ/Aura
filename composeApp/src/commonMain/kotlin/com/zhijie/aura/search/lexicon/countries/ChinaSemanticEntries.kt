package com.zhijie.aura.search.lexicon.countries

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.CountrySemanticEntries
import com.zhijie.aura.search.lexicon.metroStationEntry

internal object ChinaSemanticEntries : CountrySemanticEntries {
    override val countryCode: String = "CN"

    override val entriesByLanguage: Map<String, List<SemanticAliasEntry>> = mapOf(
        "zh" to listOf(
            metroStationEntry(
                aliases = setOf(
                    "地铁",
                    "地铁站",
                    "轨道交通",
                ),
            ),
        ),
    )
}

