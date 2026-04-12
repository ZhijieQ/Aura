package com.zhijie.aura.search

object SemanticQueryParser {
    private val separatorChars = charArrayOf(',', '.', ';', ':', '!', '?', '(', ')', '[', ']', '{', '}', '"', '\'', '/', '\\', '|', '_')

    fun parse(
        query: String,
        languageCode: String?,
        countryCode: String? = null,
        enableAllCountriesFallback: Boolean = true,
    ): ParsedSearchQuery {
        val genericEntries = SemanticLexicon.entriesForLocale(
            languageCode = languageCode,
            countryCode = countryCode,
        )
        val genericParsed = parseWithEntries(query = query, entries = genericEntries)

        if (genericParsed.semanticTags.isNotEmpty() || !enableAllCountriesFallback) {
            return genericParsed
        }

        val allCountryEntries = SemanticLexicon.entriesForAllCountries(languageCode)
        val globalParsed = parseWithEntries(query = query, entries = allCountryEntries)
        return if (globalParsed.semanticTags.isNotEmpty()) globalParsed else genericParsed
    }

    private fun parseWithEntries(
        query: String,
        entries: List<SemanticAliasEntry>,
    ): ParsedSearchQuery {
        val normalizedText = normalizeForMatch(query)
        val originalTokens = normalizedText
            .split(' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (originalTokens.isEmpty()) {
            return ParsedSearchQuery(
                originalQuery = query,
                cleanedQuery = "",
                matchedEntryIds = emptySet(),
                semanticTags = emptySet(),
            )
        }

        val tokens = originalTokens.toMutableList()
        val matchedIds = mutableSetOf<String>()
        val matchedTags = linkedSetOf<SemanticTag>()

        entries.forEach { entry ->
            val aliasTokenLists = entry.aliases
                .map { normalizeForMatch(it) }
                .map { alias ->
                    alias.split(' ')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
                .filter { it.isNotEmpty() }
                .sortedByDescending { it.size }

            var consumed = false
            aliasTokenLists.forEach aliasLoop@{ aliasTokens ->
                var index = 0
                while (index <= tokens.size - aliasTokens.size) {
                    if (tokens.subList(index, index + aliasTokens.size) == aliasTokens) {
                        repeat(aliasTokens.size) { tokens.removeAt(index) }
                        consumed = true
                        return@aliasLoop
                    }
                    index += 1
                }
            }

            if (consumed) {
                matchedIds += entry.id
                matchedTags += entry.tags
            }
        }

        return ParsedSearchQuery(
            originalQuery = query,
            cleanedQuery = tokens.joinToString(" "),
            matchedEntryIds = matchedIds,
            semanticTags = matchedTags,
        )
    }

    private fun normalizeForMatch(text: String): String {
        return text
            .lowercase()
            .let { source ->
                buildString(source.length) {
                    source.forEach { ch ->
                        if (ch in separatorChars) {
                            append(' ')
                        } else {
                            append(ch)
                        }
                    }
                }
            }
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
