package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.data.local.dao.EntryTypeClassificationInput
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.util.Constants

/**
 * Pure functions for building AI prompts related to word-edge generation,
 * edge review, and entry-type classification.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 */
internal object EdgePromptBuilder {

    /**
     * Multi-lane edge prompt: builds a sense-first, POS-aware prompt that
     * instructs the AI to evaluate candidates across 5 relationship clusters.
     */
    fun buildEdgePrompt(target: WordDetails, window: List<WordDetails>): String {
        val targetPOS = target.meanings.map { it.pos }.distinct().joinToString("/")
        val targetSenses = target.meanings.take(4).mapIndexed { idx, m ->
            "${idx + 1}. [${m.pos}] ${m.definition}"
        }.joinToString("\n")

        return buildString {
            appendLine("你是英语词池整理 Agent。你的任务是围绕目标词构建对学习者真正有价值的高质量词池。")
            appendLine()
            appendLine("目标词：${target.spelling}")
            appendLine("词性：$targetPOS")
            appendLine("义项：")
            appendLine(targetSenses)
            appendLine()
            appendLine("候选词（index. spelling [词性]: 释义）：")
            window.forEachIndexed { idx, w ->
                val m = w.meanings.firstOrNull()
                val posTag = m?.pos ?: ""
                val def = m?.definition ?: ""
                appendLine("$idx. ${w.spelling} [$posTag]: $def")
            }
            appendLine()
            appendLine("【核心原则】")
            appendLine("- 必须先区分词性，再区分义项。")
            appendLine("- 词池以\"义项\"为单位，不得把不同义项混在一起。")
            appendLine("- 语义类边默认只收同词性；跨词性归入 family lane。")
            appendLine("- \"语言学相关\"不等于\"值得加入\"；对每个候选都判断学习价值。")
            appendLine("- 不得把罕见、古旧、不自然、牵强附会的词当作高优先级。")
            appendLine("- 不得把同根词、同前缀词自动视为应纳入。")
            appendLine()
            appendLine("【5条召回通道】对每个候选词，检查以下5个通道：")
            appendLine("1. SEMANTIC — 语义概念关系（同义/近义/反义/上下位/语义重叠）")
            appendLine("   纳入条件：必须绑定到目标词的某个具体义项，且同词性。")
            appendLine("2. FORM — 形式与语音关系（拼写形近/同音/发音相似/最小对立体）")
            appendLine("   纳入条件：混淆风险高或发音训练价值高。")
            appendLine("3. FAMILY — 词族与构词关系（屈折/派生/同根）")
            appendLine("   纳入条件：构词透明度高，学习收益明确。")
            appendLine("4. USAGE — 语用与搭配关系（搭配/短语/句型模式）")
            appendLine("   纳入条件：有真实用例或高频搭配证据。")
            appendLine("5. LEARNING — 学习与错误关系（易混淆/常见误用/对比学习对）")
            appendLine("   纳入条件：有 learner evidence 或显著教学收益。")
            appendLine()
            appendLine("【硬门槛】以下任一条不满足则必须排除：")
            appendLine("- sense_match: 关系必须绑定到目标词的某个义项（form/family/learning 类除外）")
            appendLine("- pos_rule: 语义类必须同词性；跨词性只能进 family/learning lane")
            appendLine("- evidence_rule: 至少有一个可信理由支撑纳入")
            appendLine("- naturalness_rule: 候选词必须是自然常用词，不收罕见/古旧词")
            appendLine("- incremental_value_rule: 不能是已有候选的近重复或低收益远亲")
            appendLine()
            appendLine("【允许的 edge_type 值（16种）】")
            appendLine("SEMANTIC_SYNONYM / SEMANTIC_ANTONYM / SEMANTIC_OVERLAP / SEMANTIC_HYPERNYM / SEMANTIC_HYPONYM")
            appendLine("FORM_SPELLING / FORM_HOMOPHONE / FORM_PRONUNCIATION / FORM_MINIMAL_PAIR")
            appendLine("FAMILY_INFLECTION / FAMILY_DERIVATION / FAMILY_SAME_ROOT")
            appendLine("USAGE_COLLOCATION / USAGE_PHRASE / USAGE_PATTERN")
            appendLine("LEARNING_CONFUSABLE / LEARNING_MISUSE_PAIR")
            appendLine()
            appendLine("【status 取值】")
            appendLine("core — 核心关联，必须掌握")
            appendLine("support — 辅助关联，有助于理解")
            appendLine("warning — 存在混淆风险，需特别注意")
            appendLine("optional — 可选了解")
            appendLine()
            appendLine("【返回JSON格式】")
            appendLine("""[{"i":1,"edge_type":"SEMANTIC_SYNONYM","relation_strength":4,"learning_value":5,"status":"core","register":"neutral","reason":"两词都表示'大的'，高频同义替换","warning_note":"","confidence":0.9,"evidence_source":"高频同义替换","example_sentence":"The big house is large.","difficulty_cefr":"A1"}]""")
            appendLine()
            appendLine("字段说明：")
            appendLine("- i: 候选词序号（从0开始）")
            appendLine("- edge_type: 上述16种之一")
            appendLine("- relation_strength: 1-5，关系紧密程度")
            appendLine("- learning_value: 1-5，学习价值")
            appendLine("- status: core/support/warning/optional")
            appendLine("- register: 语域（neutral/formal/informal/academic/technical，无法判断填 neutral）")
            appendLine("- reason: 一句话说明为什么有关系（中文）")
            appendLine("- warning_note: 如有混淆风险给出警示（中文，无则空字符串）")
            appendLine("- confidence: 0.0-1.0，你对这个判断的自信程度")
            appendLine("- evidence_source: 证据来源简述（如\"拼写相似+高频词\"、\"常见搭配\"、\"词典标注易混\"，不超过10字）")
            appendLine("- example_sentence: 展示该关联的例句（英文，无则空字符串）")
            appendLine("- difficulty_cefr: 该边的难度等级（A1/A2/B1/B2/C1/C2，无法判断留空）")
            appendLine()
            appendLine("【自检要求】")
            appendLine("输出结果前请自查：")
            appendLine("1. 是否有边只因拼写/词根相近就纳入，但实际学习价值很低？")
            appendLine("2. 是否有边的关系类型标注错误？（如把易混淆词标为同义词）")
            appendLine("3. status=core 的边是否确实应为核心？")
            appendLine("4. 是否有重复或近重复的边？")
            appendLine("5. 是否混义项或混词性？")
            appendLine("6. 是否有低频噪声或不自然搭配？")
            appendLine("如发现问题请自行修正后再输出。")
            appendLine()
            appendLine("只返回确实存在关联的词对。无关联则返回 []。")
            appendLine(Constants.JSON_STRICT_RULES)
        }
    }

    /**
     * Build a review prompt for the REVIEWER AI model to audit low-confidence edges.
     */
    fun buildReviewPrompt(edges: List<WordEdgeEntity>, wordMap: Map<Long, WordDetails>): String {
        return buildString {
            appendLine("你是英语词池质量审核员。请逐条审核以下 ${edges.size} 条边：")
            appendLine()
            edges.forEachIndexed { idx, edge ->
                val wordA = wordMap[edge.wordIdA]?.spelling ?: "?"
                val wordB = wordMap[edge.wordIdB]?.spelling ?: "?"
                appendLine("$idx. $wordA ↔ $wordB | 类型:${edge.edgeType} | 状态:${edge.status} | 置信度:${edge.confidence} | 语域:${edge.register ?: "neutral"} | 难度:${edge.difficultyCefr ?: "未知"} | 理由:${edge.reason ?: "无"}")
            }
            appendLine()
            appendLine("审核标准：")
            appendLine("1. 关系类型(edge_type)是否准确？（如易混淆词不应标为同义词）")
            appendLine("2. status 是否合理？（core=核心/support=辅助/warning=易混/optional=可选）")
            appendLine("3. confidence 是否恰当？（低置信度边应降级或移除）")
            appendLine("4. 是否混入了仅\"相关\"但学习价值低的词？（如仅词根相关但现代意义差异大）")
            appendLine("5. 是否混入了罕见、古旧或证据薄弱的边？")
            appendLine("6. 是否遗漏了高价值的易混词或核心搭配？")
            appendLine("7. reason 是否充分解释了纳入原因？")
            appendLine("8. 是否存在重复或近重复的边？")
            appendLine("9. 难度与语域是否匹配目标学习者？（如低频学术词不应标为A1核心）")
            appendLine("10. 是否有本应排除但漏掉的候选？")
            appendLine()
            appendLine("返回JSON数组，每个元素：")
            appendLine("""[{"i":0,"verdict":"keep","new_status":"core","new_confidence":0.8,"note":"调整原因"}]""")
            appendLine("verdict: keep=保留原样 / adjust=调整status或confidence / remove=将该边置信度降为0（保留边记录）")
            appendLine("adjust 时必须提供 new_status 和 new_confidence；keep/remove 时可省略。")
            appendLine("note: 简要说明审核意见（中文）。")
            appendLine(Constants.JSON_STRICT_RULES)
        }
    }

    /**
     * Build entry-type classification prompt.
     * BUG 10 fix: includes meaningsJson and rootExplanation for each word.
     */
    fun buildEntryTypePrompt(words: List<EntryTypeClassificationInput>): String {
        return buildString {
            appendLine("你是英语构词学与词典学专家。下面有 ${words.size} 个词条，请根据你的语言学知识判断每个词条属于哪一类。")
            appendLine()
            appendLine("【三类词条的定义与特征】")
            appendLine()
            appendLine("1. word — 普通英语单词")
            appendLine("   特征：在现代英语中是独立使用的词汇单位，有明确的词性（名词/动词/形容词/副词等），")
            appendLine("   有完整的词义定义，可以独立出现在句子中。")
            appendLine("   例子：affect（动词，影响）、resilient（形容词，有韧性的）、make（动词，做）、happy、run、computer")
            appendLine()
            appendLine("2. root — 拉丁/希腊词根")
            appendLine("   特征：来自拉丁语或希腊语的构词成分，在现代英语中不能独立使用，")
            appendLine("   但作为核心语素派生出一组有规律的同族词。词根的含义需要通过它所派生的词来理解。")
            appendLine("   关键判断标准：这个形式本身是否是现代英语中可以独立使用的词？如果不是，而是作为构词成分存在于一串派生词中，它就是 root。")
            appendLine("   例子：")
            appendLine("   - spect（拉丁语\"看\"）→ spectator, inspect, spectacle, respect, suspect")
            appendLine("   - port（拉丁语\"携带\"）→ transport, export, import, portable")
            appendLine("   - duct（拉丁语\"引导\"）→ conduct, produce, reduce, deduct")
            appendLine("   - ceive/cept（拉丁语\"拿取\"）→ receive, accept, concept, deceive")
            appendLine("   - graph/gram（希腊语\"写\"）→ photograph, diagram, telegram")
            appendLine("   注意：如果一个形式既是词根又恰好是现代英语中的独立单词（如 port 作为\"港口\"），应归类为 word。只有当它纯粹作为构词成分存在时才是 root。")
            appendLine()
            appendLine("3. phrase — 多词表达 / 短语动词")
            appendLine("   特征：由两个或多个词组合而成的固定表达，整体的语义不等于各组成部分的字面意思之和。")
            appendLine("   通常包含介词或副词，与动词组合后产生全新的含义。")
            appendLine("   例子：make up（编造/化妆）、take care of（照顾）、give in（屈服）、look forward to（期待）、break down（崩溃/分解）")
            appendLine()
            appendLine("【返回格式】")
            appendLine("返回JSON数组，每个元素包含 id 和 entry_type：")
            appendLine("""[{"id":1,"entry_type":"word"},{"id":2,"entry_type":"root"},{"id":3,"entry_type":"phrase"}]""")
            appendLine()
            appendLine("entry_type 只能是 word、root、phrase 三者之一。")
            appendLine(Constants.JSON_STRICT_RULES)
            appendLine()
            appendLine("待分类词条：")
            words.forEach { w ->
                appendLine("${w.id}. ${w.spelling}")
                // BUG 10 fix: include meanings and root explanation for better classification
                if (w.meaningsJson.isNotBlank()) {
                    appendLine("   释义: ${w.meaningsJson}")
                }
                if (w.rootExplanation.isNotBlank()) {
                    appendLine("   词根: ${w.rootExplanation}")
                }
            }
        }
    }
}
