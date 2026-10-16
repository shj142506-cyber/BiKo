package com.biko.client.data

import com.biko.client.data.model.ArticleBlock
import com.biko.client.data.model.ArticleSpan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 专栏正文解析：把三种来源（结构化段落 / Quill ops / HTML）解析为统一的块，
 * 再按「信息量」挑最完整的一份。移植自 BiliPai 的实现思路。
 */
internal fun parseArticleBlocks(
    structuredParagraphs: List<JsonObject>,
    htmlContent: String?,
    ops: List<JsonObject> = emptyList(),
): List<ArticleBlock> {
    val structuredBlocks = structuredParagraphs
        .flatMap(::parseStructuredParagraph)
        .mergeAdjacentListBlocks()
    val contentOps = if (ops.isNotEmpty()) ops else parseOpsFromContentJson(htmlContent)
    val opsBlocks = parseOpsBlocks(contentOps)
    val htmlBlocks = if (contentOps.isNotEmpty()) {
        emptyList()
    } else {
        parseHtmlBlocks(htmlContent).mergeAdjacentListBlocks()
    }
    return selectRicherArticleBlocks(structuredBlocks, opsBlocks, htmlBlocks)
}

internal fun scoreArticleBlocks(blocks: List<ArticleBlock>): Int {
    if (blocks.isEmpty()) return 0
    val textLength = blocks.sumOf { block ->
        when (block) {
            is ArticleBlock.Heading -> block.text.length
            is ArticleBlock.Paragraph -> block.text.length
            is ArticleBlock.Quote -> block.text.length
            is ArticleBlock.ListBlock -> block.items.sumOf { it.length }
            is ArticleBlock.Code -> block.content.length
            is ArticleBlock.Image -> 0
        }
    }
    val imageCount = blocks.count { it is ArticleBlock.Image }
    val semanticScore = blocks.sumOf { block ->
        when (block) {
            is ArticleBlock.Heading -> 60
            is ArticleBlock.Quote -> 40
            is ArticleBlock.ListBlock -> 60 + block.items.size * 10
            is ArticleBlock.Code -> 80
            is ArticleBlock.Paragraph, is ArticleBlock.Image -> 0
        }
    }
    return textLength + imageCount * 200 + blocks.size * 8 + semanticScore
}

internal fun selectRicherArticleBlocks(vararg candidates: List<ArticleBlock>): List<ArticleBlock> =
    candidates.maxByOrNull(::scoreArticleBlocks).orEmpty()

// ---------------------------------------------------------------------------
// 行内富文本：把 (文本, 样式) 片段合成 纯文本 + 带偏移的 span
// ---------------------------------------------------------------------------

private data class SpanStyle(
    val fontSizeSp: Int = 0,
    val colorArgb: Long = 0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
) {
    val isDefault: Boolean
        get() = fontSizeSp == 0 && colorArgb == 0L && !bold && !italic && !strikethrough
}

private fun buildRich(segments: List<Pair<String, SpanStyle>>): Pair<String, List<ArticleSpan>> {
    val sb = StringBuilder()
    val spans = mutableListOf<ArticleSpan>()
    segments.forEach { (text, style) ->
        if (text.isEmpty()) return@forEach
        val start = sb.length
        sb.append(text)
        if (!style.isDefault) {
            spans += ArticleSpan(
                start = start,
                end = sb.length,
                fontSizeSp = style.fontSizeSp,
                colorArgb = style.colorArgb,
                bold = style.bold,
                italic = style.italic,
                strikethrough = style.strikethrough,
            )
        }
    }
    return sb.toString() to spans
}

private fun extractRich(element: JsonElement?): Pair<String, List<ArticleSpan>> {
    val nodes = runCatching { element?.jsonObject?.get("nodes")?.jsonArray }.getOrNull()
        ?: return "" to emptyList()
    val segments = mutableListOf<Pair<String, SpanStyle>>()
    nodes.forEach { node ->
        val nodeObject = runCatching { node.jsonObject }.getOrNull() ?: return@forEach
        val richObject = runCatching { nodeObject["rich"]?.jsonObject }.getOrNull()
        val wordObject = runCatching { nodeObject["word"]?.jsonObject }.getOrNull()
        val text = wordObject?.get("words")?.jsonPrimitive?.contentOrNull
            ?: richObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: richObject?.get("orig_text")?.jsonPrimitive?.contentOrNull
            ?: richObject?.get("emoji")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: nodeObject["formula"]?.jsonObject?.get("latex_content")?.jsonPrimitive?.contentOrNull
            ?: return@forEach
        if (text.isEmpty()) return@forEach
        segments += text to spanStyleOf(wordObject)
    }
    return buildRich(segments)
}

private fun spanStyleOf(wordObject: JsonObject?): SpanStyle {
    if (wordObject == null) return SpanStyle()
    val fontSize = wordObject["font_size"]?.jsonPrimitive?.intOrNull?.takeIf { it in 8..64 } ?: 0
    val color = wordObject["color"]?.jsonPrimitive?.contentOrNull?.let(::parseArticleColor) ?: 0L
    val style = runCatching { wordObject["style"]?.jsonObject }.getOrNull()
    fun flag(key: String): Boolean =
        style?.get(key)?.jsonPrimitive?.contentOrNull == "true" ||
            style?.get(key)?.jsonPrimitive?.intOrNull == 1 ||
            style?.get(key)?.jsonPrimitive?.booleanOrNull == true
    return SpanStyle(
        fontSizeSp = fontSize,
        colorArgb = color,
        bold = flag("bold"),
        italic = flag("italic"),
        strikethrough = flag("strikethrough") || flag("strike"),
    )
}

/** "#RRGGBB" / "#AARRGGBB" / rgb(r,g,b) → ARGB Long；无法解析返回 0。 */
internal fun parseArticleColor(raw: String?): Long {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return 0
    val hex = when {
        value.startsWith("#") -> value.substring(1)
        value.startsWith("rgb(", ignoreCase = true) -> value.substringAfter("(")
            .substringBefore(")")
            .split(",", ";")
            .mapNotNull { it.trim().toIntOrNull() }
            .takeIf { it.size >= 3 }
            ?.joinToString("") { it.coerceIn(0, 255).toString(16).padStart(2, '0') }
            ?: return 0
        else -> return 0
    }
    return when (hex.length) {
        6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it } ?: 0
        8 -> hex.toLongOrNull(16) ?: 0
        else -> 0
    }
}

// ---------------------------------------------------------------------------
// 结构化段落（opus/新版 article content）
// ---------------------------------------------------------------------------

private val articleContentJson = Json { ignoreUnknownKeys = true }

private fun parseOpsFromContentJson(content: String?): List<JsonObject> {
    val raw = content?.trim().orEmpty()
    if (!raw.startsWith("{") && !raw.startsWith("[")) return emptyList()
    return runCatching {
        when (val root = articleContentJson.parseToJsonElement(raw)) {
            is JsonArray -> root
            is JsonObject -> root["ops"]?.jsonArray
            else -> null
        }?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun parseStructuredParagraph(paragraph: JsonObject): List<ArticleBlock> =
    when (paragraph["para_type"]?.jsonPrimitive?.intOrNull) {
        2 -> extractImages(paragraph)
        3 -> extractLineImage(paragraph)
        4 -> extractQuote(paragraph)
        5 -> extractList(paragraph)
        6 -> extractLinkCardText(paragraph)
            .ifEmpty { extractLegacyFormattedList(paragraph) }
            .ifEmpty { extractTextBlocks(paragraph) }
        7 -> extractCode(paragraph)
        else -> extractTextBlocks(paragraph)
    }

private fun extractTextBlocks(paragraph: JsonObject): List<ArticleBlock> {
    val blocks = mutableListOf<ArticleBlock>()
    val heading = extractRich(paragraph["heading"])
    if (heading.first.isNotBlank()) blocks += ArticleBlock.Heading(heading.first, heading.second)
    val text = extractRich(paragraph["text"])
    if (text.first.isNotBlank()) {
        blocks += if (maxFontSize(paragraph["text"]) >= ARTICLE_HEADING_FONT_SIZE) {
            ArticleBlock.Heading(text.first, text.second)
        } else {
            ArticleBlock.Paragraph(text.first, text.second)
        }
    }
    blocks += extractImages(paragraph)
    blocks += extractLineImage(paragraph)
    return blocks
}

private fun extractQuote(paragraph: JsonObject): List<ArticleBlock> {
    val rich = extractRich(paragraph["text"])
    if (rich.first.isBlank()) return emptyList()
    return listOf(ArticleBlock.Quote(rich.first, rich.second))
}

private fun extractList(paragraph: JsonObject): List<ArticleBlock> {
    val listObject = runCatching { paragraph["list"]?.jsonObject }.getOrNull() ?: return emptyList()
    val ordered = listObject["style"]?.jsonPrimitive?.intOrNull == 1
    val items = listObject["items"]?.let { runCatching { it.jsonArray }.getOrNull() }
        .orEmpty()
        .mapNotNull { item ->
            val itemObject = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            extractNodesText(itemObject["nodes"]).takeIf { it.isNotBlank() }
        }
    if (items.isEmpty()) return emptyList()
    return listOf(ArticleBlock.ListBlock(ordered = ordered, items = items))
}

private fun extractLegacyFormattedList(paragraph: JsonObject): List<ArticleBlock> {
    val format = runCatching { paragraph["format"]?.jsonObject }.getOrNull() ?: return emptyList()
    val listFormat = runCatching { format["list_format"]?.jsonObject }.getOrNull() ?: return emptyList()
    val text = extractNodesText(runCatching { paragraph["text"]?.jsonObject?.get("nodes") }.getOrNull())
    if (text.isBlank()) return emptyList()
    val style = listFormat["style"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val ordered = style == "1" || style.equals("ordered", ignoreCase = true)
    return listOf(ArticleBlock.ListBlock(ordered = ordered, items = listOf(text)))
}

private fun extractCode(paragraph: JsonObject): List<ArticleBlock> {
    val codeObject = runCatching { paragraph["code"]?.jsonObject }.getOrNull() ?: return emptyList()
    val content = decodeHtmlEntities(codeObject["content"]?.jsonPrimitive?.contentOrNull.orEmpty()).trim()
    if (content.isBlank()) return emptyList()
    val language = codeObject["lang"]?.jsonPrimitive?.contentOrNull
        .orEmpty().removePrefix("language-").trim()
    return listOf(ArticleBlock.Code(language = language, content = content))
}

private fun extractLinkCardText(paragraph: JsonObject): List<ArticleBlock> {
    val card = runCatching { paragraph["link_card"]?.jsonObject }
        .getOrNull()
        ?.get("card")
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: return emptyList()
    val nested = listOf("ugc", "common", "opus", "live", "music", "goods", "vote")
        .firstNotNullOfOrNull { key -> runCatching { card[key]?.jsonObject }.getOrNull() }
    val title = nested?.get("title")?.jsonPrimitive?.contentOrNull
        ?: nested?.get("name")?.jsonPrimitive?.contentOrNull
        ?: card["oid"]?.jsonPrimitive?.contentOrNull
        ?: return emptyList()
    if (title.isBlank() || title == "undefined") return emptyList()
    return listOf(ArticleBlock.Paragraph(title.trim()))
}

private fun extractLineImage(paragraph: JsonObject): List<ArticleBlock> {
    val image = parseImageObject(
        runCatching { paragraph["line"]?.jsonObject?.get("pic")?.jsonObject }.getOrNull()
    ) ?: return emptyList()
    return listOf(image)
}

private fun extractNodesText(nodesElement: JsonElement?): String {
    val nodes = runCatching { nodesElement?.jsonArray }.getOrNull() ?: return ""
    return buildString {
        nodes.forEach { node ->
            val nodeObject = runCatching { node.jsonObject }.getOrNull() ?: return@forEach
            val word = nodeObject["word"]?.jsonObject?.get("words")?.jsonPrimitive?.contentOrNull
            val richText = runCatching {
                val richObj = nodeObject["rich"]?.jsonObject
                richObj?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: richObj?.get("orig_text")?.jsonPrimitive?.contentOrNull
                    ?: richObj?.get("emoji")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val formula = nodeObject["formula"]?.jsonObject?.get("latex_content")?.jsonPrimitive?.contentOrNull
            append(word ?: richText ?: formula.orEmpty())
        }
    }.trim()
}

private fun maxFontSize(element: JsonElement?): Int {
    val nodes = runCatching { element?.jsonObject?.get("nodes")?.jsonArray }.getOrNull() ?: return 0
    return nodes.maxOfOrNull { node ->
        runCatching {
            node.jsonObject["word"]?.jsonObject?.get("font_size")?.jsonPrimitive?.intOrNull
        }.getOrNull() ?: 0
    } ?: 0
}

private fun extractImages(paragraph: JsonObject): List<ArticleBlock> {
    val results = mutableListOf<ArticleBlock>()
    collectPicObjects(paragraph["pic"]).forEach { parseImageObject(it)?.let(results::add) }
    if (results.isNotEmpty()) return results
    collectPicObjects(paragraph["pics"]).forEach { parseImageObject(it)?.let(results::add) }
    return results
}

private fun collectPicObjects(element: JsonElement?): List<JsonObject> {
    if (element == null) return emptyList()
    runCatching { element.jsonArray }.getOrNull()?.let { array ->
        return array.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    }
    val obj = runCatching { element.jsonObject }.getOrNull() ?: return emptyList()
    val nested = obj["pics"]?.let { runCatching { it.jsonArray }.getOrNull() }
    if (nested != null) return nested.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    return listOf(obj)
}

private fun parseImageObject(image: JsonObject?): ArticleBlock.Image? {
    if (image == null) return null
    val rawUrl = image["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    if (rawUrl.isBlank()) return null
    return ArticleBlock.Image(
        url = normalizeImageUrl(rawUrl),
        width = image["width"]?.jsonPrimitive?.intOrNull ?: 0,
        height = image["height"]?.jsonPrimitive?.intOrNull ?: 0,
    )
}

// ---------------------------------------------------------------------------
// HTML 兜底解析
// ---------------------------------------------------------------------------

private enum class HtmlInlineKind { Paragraph, Quote, ListItem }

private fun parseHtmlBlocks(htmlContent: String?): List<ArticleBlock> {
    if (htmlContent.isNullOrBlank()) return emptyList()
    if (htmlContent.trimStart().startsWith("{")) return emptyList()

    val blocks = mutableListOf<ArticleBlock>()
    val blockRegex = Regex("""(?is)<(h[1-6]|p|pre|blockquote|li|figure)\b[^>]*>(.*?)</\1>|<img\b[^>]*>""")
    blockRegex.findAll(htmlContent).forEach { match ->
        val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
        val content = if (tag.isBlank()) match.value else match.groupValues[2]
        when {
            tag.startsWith("h") -> cleanupHtmlText(content).takeIf { it.isNotBlank() }?.let {
                blocks += ArticleBlock.Heading(it)
            }
            tag == "p" -> blocks += parseHtmlInlineBlocks(content, HtmlInlineKind.Paragraph)
            tag == "blockquote" -> blocks += parseHtmlInlineBlocks(content, HtmlInlineKind.Quote)
            tag == "pre" -> decodeHtmlEntities(cleanupHtmlText(content)).takeIf { it.isNotBlank() }?.let {
                blocks += ArticleBlock.Code(language = "", content = it)
            }
            tag == "li" -> blocks += parseHtmlInlineBlocks(content, HtmlInlineKind.ListItem)
            tag == "figure" -> blocks += parseHtmlInlineBlocks(content, HtmlInlineKind.Paragraph)
            match.value.startsWith("<img", ignoreCase = true) -> parseHtmlImage(match.value)?.let { blocks += it }
        }
    }
    return blocks
}

private fun parseHtmlInlineBlocks(content: String, kind: HtmlInlineKind): List<ArticleBlock> {
    val result = mutableListOf<ArticleBlock>()
    val imgRegex = Regex("""(?is)<img\b[^>]*>""")
    var lastIndex = 0
    imgRegex.findAll(content).forEach { match ->
        appendHtmlTextBlock(result, cleanupHtmlText(content.substring(lastIndex, match.range.first)), kind)
        parseHtmlImage(match.value)?.let(result::add)
        lastIndex = match.range.last + 1
    }
    appendHtmlTextBlock(result, cleanupHtmlText(content.substring(lastIndex)), kind)
    return result
}

private fun appendHtmlTextBlock(target: MutableList<ArticleBlock>, text: String, kind: HtmlInlineKind) {
    if (text.isBlank()) return
    val rich = buildRich(parseHtmlSpans(text))
    if (rich.first.isBlank()) return
    target += when (kind) {
        HtmlInlineKind.Paragraph -> ArticleBlock.Paragraph(rich.first, rich.second)
        HtmlInlineKind.Quote -> ArticleBlock.Quote(rich.first, rich.second)
        HtmlInlineKind.ListItem -> ArticleBlock.ListBlock(ordered = false, items = listOf(rich.first))
    }
}

private val htmlStyleTagRegex = Regex("""(?is)<(/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>|[^<]+""")

/** 从 HTML 片段提取 (文本, 样式) 片段；<img> 已在上游拆出。 */
private fun parseHtmlSpans(raw: String): List<Pair<String, SpanStyle>> {
    val segments = mutableListOf<Pair<String, SpanStyle>>()
    val styleStack = ArrayDeque<SpanStyle>().apply { addLast(SpanStyle()) }
    val currentText = StringBuilder()

    fun flush() {
        val text = currentText.toString()
        currentText.clear()
        if (text.isEmpty()) return
        segments += text to styleStack.last()
    }

    htmlStyleTagRegex.findAll(raw).forEach { match ->
        val token = match.value
        if (!token.startsWith("<")) {
            currentText.append(decodeHtmlEntities(token))
            return@forEach
        }
        val closing = match.groupValues[1] == "/"
        val tag = match.groupValues[2].lowercase()
        when (tag) {
            "b", "strong" -> {
                flush()
                if (closing) unwindHtmlStyle(styleStack) { it.bold }
                else styleStack.addLast(styleStack.last().copy(bold = true))
            }
            "i", "em" -> {
                flush()
                if (closing) unwindHtmlStyle(styleStack) { it.italic }
                else styleStack.addLast(styleStack.last().copy(italic = true))
            }
            "del", "s", "strike" -> {
                flush()
                if (closing) unwindHtmlStyle(styleStack) { it.strikethrough }
                else styleStack.addLast(styleStack.last().copy(strikethrough = true))
            }
            "span" -> {
                flush()
                if (closing) {
                    unwindHtmlStyle(styleStack) { it.fontSizeSp != 0 || it.colorArgb != 0L }
                } else {
                    val styleAttr = extractHtmlAttribute(token, "style").orEmpty()
                    val fontSize = Regex("""font-size\s*:\s*(\d+(?:\.\d+)?)\s*px""", RegexOption.IGNORE_CASE)
                        .find(styleAttr)?.groupValues?.getOrNull(1)
                        ?.toFloatOrNull()?.toInt()?.takeIf { it in 8..64 } ?: 0
                    val color = Regex("""(?:^|;)\s*color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
                        .find(styleAttr)?.groupValues?.getOrNull(1)?.trim()
                        ?.let(::parseArticleColor) ?: 0L
                    if (fontSize != 0 || color != 0L) {
                        styleStack.addLast(styleStack.last().copy(fontSizeSp = fontSize, colorArgb = color))
                    }
                }
            }
            "br" -> currentText.append('\n')
            else -> Unit
        }
    }
    flush()
    return segments
}

/** 关闭标签时只回退到引入对应样式的层级，容忍未闭合标签的混排。 */
private fun unwindHtmlStyle(stack: ArrayDeque<SpanStyle>, matches: (SpanStyle) -> Boolean) {
    for (index in stack.size - 1 downTo 1) {
        if (matches(stack[index])) {
            while (stack.size > index) stack.removeLast()
            return
        }
    }
}

// ---------------------------------------------------------------------------
// Quill ops 解析
// ---------------------------------------------------------------------------

private fun parseOpsBlocks(ops: List<JsonObject>): List<ArticleBlock> {
    if (ops.isEmpty()) return emptyList()
    return buildList {
        val pending = mutableListOf<Pair<String, SpanStyle>>()
        var pendingLineAttributes: JsonObject? = null

        fun blockquoteActive(): Boolean = pendingLineAttributes?.get("blockquote")
            ?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) == true

        fun headerLevel(): Int? = pendingLineAttributes?.get("header")
            ?.jsonPrimitive?.intOrNull?.takeIf { it in 1..6 }

        fun listStyle(): String? = pendingLineAttributes?.get("list")
            ?.jsonPrimitive?.contentOrNull

        fun flushText() {
            val rich = buildRich(pending)
            pending.clear()
            val header = headerLevel()
            val listStyleValue = listStyle()
            val isQuote = blockquoteActive()
            pendingLineAttributes = null
            if (rich.first.isBlank()) return
            add(
                when {
                    header != null -> ArticleBlock.Heading(rich.first, rich.second)
                    isQuote -> ArticleBlock.Quote(rich.first, rich.second)
                    !listStyleValue.isNullOrBlank() -> ArticleBlock.ListBlock(
                        ordered = listStyleValue.equals("ordered", ignoreCase = true),
                        items = listOf(rich.first),
                    )
                    else -> ArticleBlock.Paragraph(rich.first, rich.second)
                }
            )
        }

        ops.forEach { op ->
            val attributes = runCatching { op["attributes"]?.jsonObject }.getOrNull()
            when (val insert = op["insert"]) {
                is JsonPrimitive -> {
                    val segments = insert.contentOrNull.orEmpty().split('\n')
                    segments.forEachIndexed { index, segment ->
                        if (segment.isNotEmpty()) pending += segment to opsSpanStyle(attributes)
                        if (index < segments.lastIndex) {
                            flushText()
                            pendingLineAttributes = attributes
                        }
                    }
                }
                is JsonObject -> {
                    flushText()
                    parseOpsImage(insert)?.let(::add)
                }
                else -> Unit
            }
        }
        flushText()
    }.mergeAdjacentListBlocks()
}

private fun opsSpanStyle(attributes: JsonObject?): SpanStyle {
    attributes ?: return SpanStyle()
    fun flag(key: String): Boolean =
        attributes[key]?.jsonPrimitive?.booleanOrNull == true ||
            attributes[key]?.jsonPrimitive?.contentOrNull == "true"
    return SpanStyle(
        colorArgb = attributes["color"]?.jsonPrimitive?.contentOrNull?.let(::parseArticleColor) ?: 0L,
        bold = flag("bold"),
        italic = flag("italic"),
        strikethrough = flag("strike"),
    )
}

private fun parseOpsImage(insert: JsonObject): ArticleBlock.Image? {
    val directImage = insert["image"]
    if (directImage is JsonPrimitive) {
        val url = directImage.contentOrNull.orEmpty().trim()
        if (url.isNotBlank()) return ArticleBlock.Image(url = normalizeImageUrl(url))
    }
    val cardKeys = listOf(
        "native-image", "image-card", "cut-off", "article-card", "live-card",
        "goods-card", "video-card", "mall-card", "vote-card",
    )
    return cardKeys.firstNotNullOfOrNull { key ->
        parseImageObject(runCatching { insert[key]?.jsonObject }.getOrNull())
    }
}

// ---------------------------------------------------------------------------
// 公共小工具
// ---------------------------------------------------------------------------

private fun List<ArticleBlock>.mergeAdjacentListBlocks(): List<ArticleBlock> {
    if (size < 2) return this
    return buildList {
        this@mergeAdjacentListBlocks.forEach { block ->
            val previous = lastOrNull() as? ArticleBlock.ListBlock
            if (block is ArticleBlock.ListBlock && previous?.ordered == block.ordered) {
                removeAt(lastIndex)
                add(previous.copy(items = previous.items + block.items))
            } else {
                add(block)
            }
        }
    }
}

private fun parseHtmlImage(rawBlock: String): ArticleBlock.Image? {
    val imgTag = Regex("""(?is)<img\b[^>]*>""").find(rawBlock)?.value ?: rawBlock
    val url = extractHtmlAttribute(imgTag, "data-src") ?: extractHtmlAttribute(imgTag, "src") ?: return null
    return ArticleBlock.Image(
        url = normalizeImageUrl(url),
        width = extractHtmlAttribute(imgTag, "width")?.toIntOrNull() ?: 0,
        height = extractHtmlAttribute(imgTag, "height")?.toIntOrNull() ?: 0,
    )
}

private fun extractHtmlAttribute(tag: String, name: String): String? =
    Regex("""(?is)\b$name\s*=\s*["']([^"']+)["']""").find(tag)
        ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

private fun cleanupHtmlText(raw: String): String = raw
    .replace(Regex("""(?is)<br\s*/?>"""), "\n")
    .replace(Regex("""(?is)<[^>]+>"""), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()

private fun decodeHtmlEntities(raw: String): String = raw
    .replace("&quot;", "\"")
    .replace("&#34;", "\"")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&nbsp;", " ")
    .replace("&#39;", "'")
    .replace("&apos;", "'")

private fun normalizeImageUrl(rawUrl: String): String = when {
    rawUrl.startsWith("//") -> "https:$rawUrl"
    rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
    else -> rawUrl
}

private const val ARTICLE_HEADING_FONT_SIZE = 22
