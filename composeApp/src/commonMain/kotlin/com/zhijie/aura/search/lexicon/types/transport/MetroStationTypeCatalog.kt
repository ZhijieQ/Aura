package com.zhijie.aura.search.lexicon.types.transport

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.metroStationEntry
import com.zhijie.aura.search.lexicon.types.SemanticTypeCatalog

internal object MetroStationTypeCatalog : SemanticTypeCatalog {
    override val typeId: String = "transport.metro_station"

    override val genericEntriesByLanguage: Map<String, List<SemanticAliasEntry>> = mapOf(
        "default" to listOf(
            metroStationEntry(
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
            ),
        ),
        "es" to listOf(
            metroStationEntry(aliases = setOf("metro", "metropolitano")),
        ),
        "zh" to listOf(
            metroStationEntry(aliases = setOf("地铁", "地下铁")),
        ),
    )

    override val entriesByCountryAndLanguage: Map<String, Map<String, List<SemanticAliasEntry>>> = mapOf(
        "ES" to mapOf(
            "es" to listOf(
                metroStationEntry(
                    aliases = setOf(
                        "metro madrid",
                        "metro de madrid",
                    ),
                ),
            ),
        ),
        "CN" to mapOf(
            "zh" to listOf(
                metroStationEntry(
                    aliases = setOf(
                        "地铁站",
                        "轨道交通",
                    ),
                ),
            ),
        ),
    )
}

