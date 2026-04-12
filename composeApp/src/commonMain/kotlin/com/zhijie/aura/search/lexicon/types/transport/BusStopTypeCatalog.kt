package com.zhijie.aura.search.lexicon.types.transport

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.types.SemanticTypeCatalog

/**
 * Template type: transport.bus_stop
 * Fill country-specific aliases later.
 */
internal object BusStopTypeCatalog : SemanticTypeCatalog {
    override val typeId: String = "transport.bus_stop"

    override val genericEntriesByLanguage: Map<String, List<SemanticAliasEntry>> = emptyMap()

    override val entriesByCountryAndLanguage: Map<String, Map<String, List<SemanticAliasEntry>>> = emptyMap()
}

