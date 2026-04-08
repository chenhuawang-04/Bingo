package com.xty.englishhelper.data.remote.cambridge

import com.xty.englishhelper.domain.model.CambridgeEntry
import com.xty.englishhelper.domain.model.CambridgeSense
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

class CambridgeHtmlParser {

    fun parseEntry(html: String, fallbackWord: String, sourceUrl: String): CambridgeEntry {
        val doc = Jsoup.parse(html)
        val entryRoot = doc.selectFirst("div.entry-body__el, div.pr.entry-body__el")
        val headword = entryRoot
            ?.selectFirst("span.hw, span.headword, .hw.dhw")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("span.hw, span.headword, .hw.dhw")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: fallbackWord

        val partOfSpeech = entryRoot
            ?.selectFirst("span.pos, .pos.dpos, .posgram")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("span.pos, .pos.dpos, .posgram")
                ?.text()
                ?.trim()
                .orEmpty()

        val ipaTexts = (entryRoot ?: doc)
            .select("span.ipa, .dipa")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val pronunciation = ipaTexts.joinToString(" ").ifBlank { null }
        val senses = extractSenses(entryRoot ?: doc)

        return CambridgeEntry(
            headword = headword,
            partOfSpeech = partOfSpeech,
            pronunciation = pronunciation,
            senses = senses,
            sourceUrl = sourceUrl
        )
    }

    private fun extractSenses(root: Element): List<CambridgeSense> {
        val senses = mutableListOf<CambridgeSense>()
        val blocks = root.select("div.def-block, div.def-block.ddef_block")
        for (block in blocks) {
            val definition = block.selectFirst(".def, .ddef_d")?.text()?.trim()
            val translation = block.selectFirst(".trans, .dtrans")?.text()?.trim()
            if (!definition.isNullOrBlank()) {
                senses.add(CambridgeSense(definition, translation))
            }
            if (senses.size >= 12) break
        }

        if (senses.isEmpty()) {
            val defs = root.select(".def, .ddef_d")
            for (def in defs) {
                val definition = def.text().trim()
                if (definition.isBlank()) continue
                val translation = def.parent()?.selectFirst(".trans, .dtrans")?.text()?.trim()
                senses.add(CambridgeSense(definition, translation))
                if (senses.size >= 8) break
            }
        }

        return senses
    }

    fun parseExamples(html: String, limit: Int = 8): List<String> {
        val doc = Jsoup.parse(html)
        val entryRoot = doc.selectFirst(".entry-body, .entry-body__el, .pr.entry-body__el") ?: doc
        val candidates = entryRoot.select(
            ".def-block .examp, .def-block .dexamp, .examp.dexamp, .examp, .dexamp, .eg, .x-h .x, .examples li"
        )

        val dedup = linkedMapOf<String, String>()
        for (node in candidates) {
            val sentence = extractEnglishExample(node) ?: continue
            val key = normalizeForDedup(sentence)
            if (key.isBlank()) continue
            dedup.putIfAbsent(key, sentence)
            if (dedup.size >= limit) break
        }
        return dedup.values.toList()
    }

    private fun extractEnglishExample(node: Element): String? {
        val clone = node.clone()
        clone.select(
            ".trans, .dtrans, .trans.dtrans, .trans-dtrans, .x-h .trans, .x-h .dtrans, .lab, .dlab"
        ).remove()
        val raw = normalizeWhitespace(clone.text())
        if (!looksLikeSentence(raw)) return null
        return raw
    }

    private fun looksLikeSentence(text: String): Boolean {
        if (text.length < 8 || text.length > 240) return false
        val asciiLetters = text.count { it.isLetter() && it.code < 128 }
        if (asciiLetters < 6) return false
        val zhChars = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        if (zhChars > 4) return false
        return true
    }

    private fun normalizeForDedup(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("[\\p{Punct}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeWhitespace(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
