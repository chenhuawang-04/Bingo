package com.xty.englishhelper.ui.screen.addword

import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.SynonymInfo

data class AddWordUiState(
    val isEditing: Boolean = false,
    val spelling: String = "",
    val phonetic: String = "",
    val meanings: List<Meaning> = listOf(Meaning("", "")),
    val rootExplanation: String = "",
    val decomposition: List<DecompositionPart> = emptyList(),
    val synonyms: List<SynonymInfo> = emptyList(),
    val similarWords: List<SimilarWordInfo> = emptyList(),
    val cognates: List<CognateInfo> = emptyList(),
    val inflections: List<Inflection> = emptyList(),
    val isAiLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSuccessfully: Boolean = false,
    val availableUnits: List<StudyUnit> = emptyList(),
    val selectedUnitIds: Set<Long> = emptySet()
)
