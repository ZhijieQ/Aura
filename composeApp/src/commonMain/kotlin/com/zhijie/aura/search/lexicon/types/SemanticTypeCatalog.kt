package com.zhijie.aura.search.lexicon.types

import com.zhijie.aura.search.SemanticAliasEntry

/**
 * One semantic type (for one key-value concept), with language and country layers.
 */
internal interface SemanticTypeCatalog {
    val typeId: String

    /** Generic aliases not tied to a specific country. */
    val genericEntriesByLanguage: Map<String, List<SemanticAliasEntry>>

    /** Country-specific aliases: countryCode -> languageCode -> entries. */
    val entriesByCountryAndLanguage: Map<String, Map<String, List<SemanticAliasEntry>>>
}

