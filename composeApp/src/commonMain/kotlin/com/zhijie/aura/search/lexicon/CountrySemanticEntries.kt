package com.zhijie.aura.search.lexicon

import com.zhijie.aura.search.SemanticAliasEntry

internal interface CountrySemanticEntries {
    val countryCode: String
    val entriesByLanguage: Map<String, List<SemanticAliasEntry>>
}

