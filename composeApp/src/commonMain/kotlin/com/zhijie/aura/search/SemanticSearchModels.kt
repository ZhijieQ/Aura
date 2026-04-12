package com.zhijie.aura.search

/**
 * A searchable OSM semantic tag. Example: railway=station.
 */
data class SemanticTag(
    val key: String,
    val value: String,
)

data class SemanticAliasEntry(
    val id: String,
    val aliases: Set<String>,
    val tags: Set<SemanticTag>,
)

data class ParsedSearchQuery(
    val originalQuery: String,
    val cleanedQuery: String,
    val matchedEntryIds: Set<String>,
    val semanticTags: Set<SemanticTag>,
) {
    val effectiveQuery: String
        get() = cleanedQuery.ifBlank { originalQuery.trim() }
}

