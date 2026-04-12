package com.zhijie.aura.search.lexicon.types

import com.zhijie.aura.search.lexicon.types.healthcare.HospitalTypeCatalog
import com.zhijie.aura.search.lexicon.types.transport.BusStopTypeCatalog
import com.zhijie.aura.search.lexicon.types.transport.MetroStationTypeCatalog

internal object SemanticTypeRegistry {
    /**
     * Template-first registry.
     * Add new semantic type catalogs here.
     */
    val catalogs: List<SemanticTypeCatalog> = listOf(
        MetroStationTypeCatalog,
        BusStopTypeCatalog,
        HospitalTypeCatalog,
    )
}

