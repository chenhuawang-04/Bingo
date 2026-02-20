package com.xty.englishhelper.domain.model

enum class PartOfSpeech(val label: String) {
    NOUN("n."),
    VERB("v."),
    ADJECTIVE("adj."),
    ADVERB("adv."),
    PREPOSITION("prep."),
    CONJUNCTION("conj."),
    PRONOUN("pron."),
    INTERJECTION("int."),
    ARTICLE("art."),
    AUXILIARY("aux."),
    PHRASE("phr."),
    OTHER("其他");

    companion object {
        fun fromLabel(label: String): PartOfSpeech {
            return entries.find {
                it.label.equals(label, ignoreCase = true) ||
                it.name.equals(label, ignoreCase = true)
            } ?: OTHER
        }
    }
}
