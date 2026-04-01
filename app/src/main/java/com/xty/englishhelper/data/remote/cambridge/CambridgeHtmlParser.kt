package com.xty.englishhelper.data.remote.cambridge

import com.xty.englishhelper.domain.model.CambridgeEntry
import com.xty.englishhelper.domain.model.CambridgeSense
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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
        val senses = extractSenses(doc)

        return CambridgeEntry(
            headword = headword,
            partOfSpeech = partOfSpeech,
            pronunciation = pronunciation,
            senses = senses,
            sourceUrl = sourceUrl
        )
    }

    private fun extractSenses(doc: Document): List<CambridgeSense> {
        val senses = mutableListOf<CambridgeSense>()
        val blocks = doc.select("div.def-block, div.def-block.ddef_block")
        for (block in blocks) {
            val definition = block.selectFirst(".def, .ddef_d")?.text()?.trim()
            val translation = block.selectFirst(".trans, .dtrans")?.text()?.trim()
            if (!definition.isNullOrBlank()) {
                senses.add(CambridgeSense(definition, translation))
            }
            if (senses.size >= 12) break
        }

        if (senses.isEmpty()) {
            val defs = doc.select(".def, .ddef_d")
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
}
