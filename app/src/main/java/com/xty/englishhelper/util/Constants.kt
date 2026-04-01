package com.xty.englishhelper.util

object Constants {
    const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/"
    const val ANTHROPIC_API_VERSION = "2023-06-01"
    const val ANTHROPIC_USER_AGENT = "claude-cli/2.1.50 (external, cli)"

    const val OPENAI_BASE_URL = "https://api.openai.com/"

    val ANTHROPIC_AVAILABLE_MODELS = listOf(
        "claude-haiku-4-5-20251001" to "Claude Haiku 4.5",
        "claude-sonnet-4-6" to "Claude Sonnet 4.6",
        "claude-opus-4-6" to "Claude Opus 4.6",
    )

    val OPENAI_AVAILABLE_MODELS = listOf(
        "gpt-5.2" to "GPT-5.2",
        "gpt-4.1" to "GPT-4.1",
        "gpt-4.1-mini" to "GPT-4.1 Mini",
        "gpt-4.1-nano" to "GPT-4.1 Nano",
        "o4-mini" to "o4-mini",
        "o3" to "o3",
    )

    const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    const val DEFAULT_OPENAI_MODEL = "gpt-4.1-nano"

    const val AI_WORD_PROMPT_RULES = """【最高优先级指令 / 必须严格执行】
你是专业的英语词汇分析助手。用户会提供一个英语单词，你必须输出“纯 JSON 文本”，不得包含任何额外内容。
这是硬性要求。若输出非 JSON 或无法被标准 JSON 解析器解析，将被视为错误结果并直接丢弃。

【严格 JSON 输出规则（必须逐条遵守）】
1) 仅输出 JSON 本体，不要任何解释、标题、注释、前后缀、礼貌语或空话。
2) 禁止任何 Markdown 代码块或围栏（``` / ```json / ''' / '''json）。
3) 输出必须以 { 开始并以 } 结束，只能是“单一 JSON 对象”。
4) 必须使用双引号，字符串内部出现引号必须转义为 \\"。
5) 禁止在字符串中出现未转义的双引号。
6) 禁止单引号、禁止尾逗号、禁止 NaN/Infinity。
7) 仅允许 schema 中定义的字段，不得新增字段，不得改字段名，不得省略必填字段。
8) 若无法确定某字段，按类型给空值："" / 0 / 0.0 / false / null / []。

【任务说明】
请分析以下方面：
1. 词性与中文释义 —— 使用详细词性标注：vt. / vi. / vt.&vi. / n.[C] / n.[U] / n.[集合] 等，无法细分则用 n. / v. / adj. / adv. / prep. / conj. / pron. / int. / art. / aux. / phr.
2. 音标（国际音标）
3. 词根拆解 —— 将单词拆分为前缀/词根/后缀等构词成分，每个成分标注类型（PREFIX/ROOT/SUFFIX/STEM/LINKING/OTHER）和含义
4. 词根解释（用文字说明构词逻辑）
5. 词形变化 —— 列出所有常见屈折变化：plural / past_tense / past_participle / present_participle / third_person / comparative / superlative（只列适用项）
6. 近义词（2-4个，附简要区分）
7. 形近词（1-3个，附含义与区分方法）
8. 同根词（2-4个，附含义与共同词根）

【仅允许的输出 JSON schema】
{
  "phonetic": "/音标/",
  "meanings": [
    {"pos": "详细词性", "definition": "中文释义"}
  ],
  "decomposition": [
    {"segment": "构词成分", "role": "PREFIX|ROOT|SUFFIX|STEM|LINKING|OTHER", "meaning": "含义"}
  ],
  "rootExplanation": "词根解释",
  "inflections": [
    {"form": "变形拼写", "formType": "plural|past_tense|past_participle|present_participle|third_person|comparative|superlative"}
  ],
  "synonyms": [
    {"word": "近义词", "explanation": "区分说明"}
  ],
  "similarWords": [
    {"word": "形近词", "meaning": "含义", "explanation": "区分方法"}
  ],
  "cognates": [
    {"word": "同根词", "meaning": "含义", "sharedRoot": "共同词根"}
  ]
}
"""

    val JSON_STRICT_RULES = """
【严格 JSON 输出规则（必须逐条遵守）】
1) 仅输出 JSON 本体，不要任何解释、标题、注释、前后缀、礼貌语或空话。
2) 禁止任何 Markdown 代码块或围栏（``` / ```json / ''' / '''json）。
3) 输出必须以 { 或 [ 开始，并以 } 或 ] 结束；不得夹杂任何非 JSON 文本。
4) 必须使用双引号，字符串内部出现引号必须转义为 \\"。
5) 禁止在字符串中出现未转义的双引号。
6) 禁止单引号、禁止尾逗号、禁止 NaN/Infinity。
7) 仅允许 schema 中定义的字段，不得新增字段，不得改字段名，不得省略必填字段。
8) 若无法确定某字段，按类型给空值："" / 0 / 0.0 / false / null / []。
9) 输出必须可被标准 JSON 解析器直接解析。
""".trimIndent()

    const val AI_USER_PROMPT_TEMPLATE = "请严格按规则分析单词：%s"

    const val AI_WORD_RESEARCH_PROMPT_RULES = """【最高优先级指令 / 必须严格执行】
你现在的任务不是直接生成单词词条，而是为“单词整理”收集“可选参考建议”。
这些建议仅用于提供线索，不得作为后续主模型的硬性约束。
请尽可能结合网络上常见且可靠的英语学习整理内容、词典差异说明、考研经验总结、易混词笔记、同根词整理、形近词辨析等资料，
提炼出可验证、可忽略、可替换的参考要点。

【任务重点】
1) 识别该词最常见的易混词、近义词、同根词、形近词。
2) 标出对考研更重要、更常考、更容易混淆的点。
3) 只保留高置信度、稳定、常见的区别结论。
4) 如果网络上没有足够可靠或有价值的整理结果，必须明确返回 hasUsefulReference=false。
5) 你输出的是“参考摘要”，不是最终词条，不要替主模型完成最终释义与字段填充。
6) 禁止使用“必须”“务必”等强制措辞去影响主模型行为，保持中性描述。

【严格 JSON 输出规则（必须逐条遵守）】
1) 仅输出 JSON 本体，不要任何解释、标题、注释、前后缀、Markdown、代码块或客套话。
2) 输出必须以 { 开始并以 } 结束，只允许单个 JSON 对象。
3) 必须使用双引号，字符串内部如有引号必须正确转义。
4) 若无法确定字段内容，请返回空字符串、空数组或 false，不得编造。

【唯一允许的 JSON schema】
{
  "hasUsefulReference": true,
  "examFocusSummary": "一句话概括该词在考研中的常见关注点（仅建议，不是结论）",
  "confusionWords": [
    {"word": "词", "note": "与目标词的关键区别", "examImportance": "高|中|低"}
  ],
  "synonymWords": [
    {"word": "词", "note": "近义差异", "examImportance": "高|中|低"}
  ],
  "similarWords": [
    {"word": "词", "note": "形近/拼写易混点", "examImportance": "高|中|低"}
  ],
  "cognateWords": [
    {"word": "词", "note": "同根关系与考研价值", "examImportance": "高|中|低"}
  ],
  "webFindings": [
    "来自网络整理的候选观察 1",
    "来自网络整理的候选观察 2"
  ],
  "confidence": 0.0
}

【补充要求】
- 每个数组最多给 4 项。
- webFindings 最多给 5 条，每条尽量简短。
- confidence 取值范围 0.0-1.0。
- 如果没有足够有价值的参考，请返回 hasUsefulReference=false，其他字段尽量留空。
"""
}
