package com.zhijie.aura

import com.zhijie.aura.search.SemanticQueryParser
import com.zhijie.aura.search.SemanticLexicon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun parse_metro_prefix_extracts_station_tag() {
        val parsed = SemanticQueryParser.parse("metro sol", "en")

        assertEquals("sol", parsed.cleanedQuery)
        assertTrue(parsed.semanticTags.any { it.key == "railway" && it.value == "station" })
    }

    @Test
    fun parse_metro_suffix_extracts_station_tag() {
        val parsed = SemanticQueryParser.parse("gran via metro", "es")

        assertEquals("gran via", parsed.cleanedQuery)
        assertTrue(parsed.semanticTags.any { it.key == "railway" && it.value == "station" })
    }

    @Test
    fun parse_country_specific_alias_for_china() {
        val parsed = SemanticQueryParser.parse(
            query = "天安门 轨道交通",
            languageCode = "zh",
            countryCode = "CN",
        )

        assertEquals("天安门", parsed.cleanedQuery)
        assertTrue(parsed.semanticTags.any { it.key == "railway" && it.value == "station" })
    }

    @Test
    fun lexicon_country_and_language_fallback_merges_aliases() {
        val entries = SemanticLexicon.entriesForLocale(languageCode = "es", countryCode = "ES")
        val metro = entries.first { it.id == "transport.metro_station" }

        assertTrue("metro de madrid" in metro.aliases)
        assertTrue("metro" in metro.aliases)
    }

    @Test
    fun parse_falls_back_to_all_countries_when_generic_misses() {
        val parsed = SemanticQueryParser.parse(
            query = "轨道交通 天安门",
            languageCode = "zh",
            countryCode = "ES",
            enableAllCountriesFallback = true,
        )

        assertEquals("天安门", parsed.cleanedQuery)
        assertTrue(parsed.semanticTags.any { it.key == "railway" && it.value == "station" })
    }
}