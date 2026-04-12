package com.zhijie.aura.search.lexicon

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.SemanticTag

internal fun metroStationEntry(aliases: Set<String>): SemanticAliasEntry {
    return SemanticAliasEntry(
        id = "transport.metro_station",
        aliases = aliases,
        tags = setOf(SemanticTag(key = "railway", value = "station")),
    )
}

