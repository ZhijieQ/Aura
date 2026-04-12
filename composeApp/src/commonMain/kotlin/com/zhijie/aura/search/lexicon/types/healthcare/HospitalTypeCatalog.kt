package com.zhijie.aura.search.lexicon.types.healthcare

import com.zhijie.aura.search.SemanticAliasEntry
import com.zhijie.aura.search.lexicon.types.SemanticTypeCatalog

/**
 * Template type: healthcare.hospital
 * Fill country-specific aliases later.
 */
internal object HospitalTypeCatalog : SemanticTypeCatalog {
    override val typeId: String = "healthcare.hospital"

    override val genericEntriesByLanguage: Map<String, List<SemanticAliasEntry>> = emptyMap()

    override val entriesByCountryAndLanguage: Map<String, Map<String, List<SemanticAliasEntry>>> = emptyMap()
}

