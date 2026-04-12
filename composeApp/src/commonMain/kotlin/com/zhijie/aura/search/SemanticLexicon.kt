package com.zhijie.aura.search

import com.zhijie.aura.search.lexicon.types.SemanticTypeRegistry

/**
 * Locale-aware semantic dictionary backed by type catalogs.
 *
 * Generic mode order:
 * 1) current country + language
 * 2) generic language
 * 3) generic default
 *
 * Global mode:
 * - iterate all countries for the same language, then generic fallback.
 */
object SemanticLexicon {
    private const val DEFAULT_LANGUAGE = "default"

    fun entriesForLanguage(languageCode: String?): List<SemanticAliasEntry> {
        return entriesForLocale(languageCode = languageCode, countryCode = null)
    }

    fun entriesForLocale(
        languageCode: String?,
        countryCode: String?,
    ): List<SemanticAliasEntry> {
        val normalizedLanguage = normalizeLanguage(languageCode)
        val normalizedCountry = normalizeCountry(countryCode)
        val mergedById = linkedMapOf<String, SemanticAliasEntry>()

        SemanticTypeRegistry.catalogs.forEach { catalog ->
            val entries = buildList {
                if (normalizedCountry != null && normalizedLanguage != null) {
                    addAll(
                        catalog.entriesByCountryAndLanguage[normalizedCountry]
                            ?.get(normalizedLanguage)
                            .orEmpty(),
                    )
                }
                if (normalizedLanguage != null) {
                    addAll(catalog.genericEntriesByLanguage[normalizedLanguage].orEmpty())
                }
                addAll(catalog.genericEntriesByLanguage[DEFAULT_LANGUAGE].orEmpty())
            }
            mergeEntries(mergedById, entries)
        }

        return mergedById.values.toList()
    }

    fun entriesForAllCountries(languageCode: String?): List<SemanticAliasEntry> {
        val normalizedLanguage = normalizeLanguage(languageCode)
        val mergedById = linkedMapOf<String, SemanticAliasEntry>()

        SemanticTypeRegistry.catalogs.forEach { catalog ->
            val entries = buildList {
                if (normalizedLanguage != null) {
                    catalog.entriesByCountryAndLanguage.values.forEach { entriesByLanguage ->
                        addAll(entriesByLanguage[normalizedLanguage].orEmpty())
                    }
                    addAll(catalog.genericEntriesByLanguage[normalizedLanguage].orEmpty())
                }
                addAll(catalog.genericEntriesByLanguage[DEFAULT_LANGUAGE].orEmpty())
            }
            mergeEntries(mergedById, entries)
        }

        return mergedById.values.toList()
    }

    private fun mergeEntries(
        target: LinkedHashMap<String, SemanticAliasEntry>,
        entries: List<SemanticAliasEntry>,
    ) {
        entries.forEach { entry ->
            val existing = target[entry.id]
            target[entry.id] = if (existing == null) {
                entry
            } else {
                existing.copy(
                    aliases = existing.aliases + entry.aliases,
                    tags = existing.tags + entry.tags,
                )
            }
        }
    }

    private fun normalizeLanguage(languageCode: String?): String? {
        return languageCode
            ?.substringBefore('-')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeCountry(countryCode: String?): String? {
        return countryCode
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
    }
}
