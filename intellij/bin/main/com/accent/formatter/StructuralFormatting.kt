package com.accent.formatter

/** Document-wide lexical/layout passes; kept in parity with structuralFormatting.ts. */
internal object StructuralFormatting {
    fun skipNonCode(source: String, index: Int): Int {
        if (source.startsWith("//", index)) return source.indexOf('\n', index).let { if (it < 0) source.length else it }
        if (source.startsWith("/*", index)) return source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
        val quote = source[index]
        if (quote != '"' && quote != '\'') return index
        val delimiter = if (source.startsWith("\"\"\"", index)) "\"\"\"" else quote.toString()
        var i = index + delimiter.length
        while (i < source.length) {
            if (source[i] == '\\') { i += 2; continue }
            if (source.startsWith(delimiter, i)) return i + delimiter.length
            i++
        }
        return source.length
    }

    class ProtectedCode(val source: String, private val replacements: List<Pair<String, String>>) {
        fun restore(text: String): String {
            var result = text
            for ((token, original) in replacements) result = result.replace(token, original)
            return result
        }
    }

    /** Opaque tokens protect even the legacy regex passes, retaining single-line widths. */
    fun protectNonCode(source: String): ProtectedCode {
        val replacements = mutableListOf<Pair<String, String>>()
        val result = StringBuilder()
        var codePoint = 0xE100
        var i = 0
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end == i) { result.append(source[i++]); continue }
            val original = source.substring(i, end)
            var marker: String
            do { marker = String(Character.toChars(codePoint++)) } while (source.contains(marker))
            val comment = original.startsWith('/')
            val prefix = if (comment) original.take(2) else original.take(1)
            val suffix = if (original.startsWith("/*")) "*/" else if (comment) "" else original.take(1)
            val width = original.lineSequence().first().length
            val token = prefix + marker.repeat(maxOf(1, width - prefix.length - suffix.length)) + suffix
            replacements.add(token to original)
            result.append(token)
            i = end
        }
        return ProtectedCode(result.toString(), replacements)
    }

    private fun closing(source: String, open: Int): Int {
        var depth = 1
        var i = open + 1
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end != i) { i = end; continue }
            if (source[i] == '(') depth++
            if (source[i] == ')') { depth--; if (depth == 0) return i }
            i++
        }
        return -1
    }

    private fun isCall(source: String, open: Int, close: Int): Boolean {
        val before = source.substring(0, open).trimEnd()
        val name = Regex("""[\w$]+$""").find(before)?.value ?: ""
        return Regex("""[\w$\]>]$""").containsMatchIn(before) &&
            name !in listOf("if", "for", "while", "switch", "catch", "synchronized", "try") &&
            !Regex("""^\s*->""").containsMatchIn(source.substring(close + 1))
    }

    fun splitJavaArguments(source: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var depth = 0
        var angles = 0
        var i = 0
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end != i) { i = end; continue }
            val ch = source[i]
            if (ch in "([{") depth++
            else if (ch in ")]}") depth--
            else if (ch == '<' && (angles > 0 || Regex("""(?:\b[A-Z_$][\w$]*\s*|\.\s*)$""").containsMatchIn(source.substring(0, i)) || Regex("""^[^;{}]*>\s*[.(]""").containsMatchIn(source.substring(i + 1))) &&
                Regex("""^\s*[\w?@]""").containsMatchIn(source.substring(i + 1)) && source.indexOf('>', i + 1) >= 0) angles++
            else if (ch == '>' && angles > 0) angles--
            else if (ch == ',' && depth == 0 && angles == 0) {
                result.add(source.substring(start, i).trim()); start = i + 1
            }
            i++
        }
        result.add(source.substring(start).trim())
        return result
    }

    private fun containsCall(source: String): Boolean {
        var i = 0
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end != i) { i = end; continue }
            if (source[i] == '(') {
                val close = closing(source, i)
                if (close >= 0 && isCall(source, i, close)) return true
            }
            i++
        }
        return false
    }

    private fun appendClose(source: String): String {
        var lastComment = false
        var i = 0
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end != i) { lastComment = source.startsWith("//", i) && end == source.length; i = end }
            else i++
        }
        return source + if (lastComment) "\n)" else ")"
    }

    fun formatCalls(source: String, maxLineLength: Int, tabSize: Int = 4, baseDepth: Int = 0, continuation: Int = 0, continuationSize: Int = 2): String {
        var depth = baseDepth
        val result = StringBuilder()
        var i = 0
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end != i) { result.append(source.substring(i, end)); i = end; continue }
            if (source[i] != '(') {
                if (source[i] == '{') depth++
                if (source[i] == '}') depth = maxOf(0, depth - 1)
                result.append(source[i++]); continue
            }
            val close = closing(source, i)
            if (close < 0) { result.append(source.substring(i)); break }
            val body = source.substring(i + 1, close)
            if (!isCall(source, i, close)) result.append("(" + formatCalls(body, maxLineLength, tabSize, depth, continuation, continuationSize) + ")")
            else {
                val args = splitJavaArguments(body)
                val prefix = result.substring(result.lastIndexOf("\n") + 1).trimStart()
                val extra = if (prefix.startsWith(".") || prefix.startsWith("ASSIGN_RHS:")) continuationSize else 0
                val width = depth * tabSize + continuation + extra + prefix.removePrefix("ASSIGN_RHS:").length + body.trim().length + 2
                val compactChain = prefix.startsWith(".") && args.size == 1 && !args[0].contains('\n') && !args[0].contains("//")
                val multiline = args.size > 1 || containsCall(body) || (body.contains('\n') && !compactChain) ||
                    (body.isNotBlank() && width >= maxLineLength)
                val formatted = args.map { formatCalls(it, maxLineLength, tabSize, depth, continuation + extra + (if (multiline) continuationSize else 0), continuationSize) }
                result.append(if (multiline && body.isNotBlank()) "(\n" + appendClose(formatted.joinToString(",\n")) else "(" + body.trim() + ")")
            }
            i = close + 1
            val chain = Regex("""^\s*\.\s*([\w$]+)\s*\(""").find(source.substring(i))
            if (chain != null) {
                result.append("\n." + chain.groupValues[1])
                i += chain.value.length - 1
            }
        }
        return result.toString()
    }

    fun expandBlocks(source: String): String {
        data class Frame(val kind: String, val block: Boolean)
        val stack = mutableListOf<Frame>()
        val result = StringBuilder()
        var previous = ""
        var i = 0
        while (i < source.length) {
            val end = skipNonCode(source, i)
            if (end != i) { result.append(source.substring(i, end)); i = end; continue }
            val token = Regex("""^[\w$]+""").find(source.substring(i))?.value ?: source[i].toString()
            val frame = stack.lastOrNull()
            if (token in listOf("(", "{", "[")) {
                val block = token == "{" && (previous in listOf(")", ">", "else", "try", "finally", "do"))
                stack.add(Frame(token, block))
                result.append(token)
                if (block && !Regex("""^[ \t]*(?:[\r\n}]|//)""").containsMatchIn(source.substring(i + 1))) result.append('\n')
            } else if (token in listOf(")", "}", "]")) {
                if (token == "}" && frame?.block == true && previous != "{") {
                    while (result.isNotEmpty() && result.last().isWhitespace()) result.setLength(result.length - 1)
                    result.append('\n')
                }
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                result.append(token)
            } else {
                result.append(token)
                if (token == ";" && frame?.block == true && !Regex("""^\s*(?:[\r\n}]|//|/\*)""").containsMatchIn(source.substring(i + 1))) result.append('\n')
            }
            if (token.isNotBlank()) previous = token
            i += token.length
        }
        return result.toString()
    }

    fun formatAnnotations(source: String): String {
        var result = ""
        var i = 0
        while (i < source.length) {
            val skipped = skipNonCode(source, i)
            if (skipped != i) { result += source.substring(i, skipped); i = skipped; continue }
            val name = Regex("""^@[\w$.]+""").find(source.substring(i))
            if (name == null || name.value == "@interface") { result += source[i++]; continue }
            var end = i + name.value.length
            val gap = Regex("""^\s*""").find(source.substring(end))!!.value.length
            if (source.getOrNull(end + gap) == '(') {
                val close = closing(source, end + gap)
                if (close < 0) { result += source.substring(i); break }
                end = close + 1
            }
            if (result.substring(result.lastIndexOf('\n') + 1).isNotBlank()) result = result.trimEnd() + "\n"
            result += source.substring(i, end)
            if (Regex("""^[ \t]*[^\s,;)\]>.]""").containsMatchIn(source.substring(end))) result += "\n"
            i = end
        }
        return result
    }
}
