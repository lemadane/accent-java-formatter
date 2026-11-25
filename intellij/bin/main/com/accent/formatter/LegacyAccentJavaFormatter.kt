package com.accent.formatter

class LegacyAccentJavaFormatter(val config: AccentFormatterConfig = AccentFormatterConfig()) {

    fun formatDocument(sourceCode: String): String {
        val protectedCode = StructuralFormatting.protectNonCode(sourceCode)
        var lines = protectedCode.source.split("\r\n", "\n")

        if (config.organizeImportsOnFormat) {
            val importResult = AccentImportOrganizer.organizeJavaImports(lines)
            if (importResult.hasImports) {
                val beforeImports = lines.subList(0, importResult.startLineIndex)
                val afterImports = lines.subList(importResult.endLineIndex + 1, lines.size)
                val organizedLines = importResult.importsText.split("\n")
                lines = beforeImports + organizedLines + listOf("") + afterImports
            }
        }

        lines = preprocessFormattingRules(lines)
        return protectedCode.restore(formatCodeLines(lines))
    }

    private fun preprocessFormattingRules(lines: List<String>): List<String> {
        var result = lines
        result = applyRule5Clauses(result)
        // Declarations and calls share the recursive formatter.
        result = StructuralFormatting.formatAnnotations(result.joinToString("\n")).split("\n")
        result = applyRuleTernaryOperators(result)
        result = applyRule7AssignmentRhs(result)
        // Nested calls are handled recursively.
        result = StructuralFormatting.formatCalls(StructuralFormatting.expandBlocks(result.joinToString("\n")), config.maxLineLength, config.tabSize, continuationSize = config.continuationIndent).split("\n")
        // Chain boundaries are handled together with calls.
        return result
    }

    private fun applyRule5Clauses(lines: List<String>): List<String> {
        val output = mutableListOf<String>()
        var angleDepth = 0
        val clauseRegex = Regex("""^(extends|implements|throws)\b""")

        for (line in lines) {
            var start = 0
            var i = 0
            while (i < line.length) {
                val skipped = StructuralFormatting.skipNonCode(line, i)
                if (skipped != i) { i = skipped; continue }
                val ch = line[i]
                // Bounds inside type parameters/arguments are not inheritance clauses.
                if (ch == '<') angleDepth++
                else if (ch == '>') angleDepth = maxOf(0, angleDepth - 1)
                else if (ch in ";{}") angleDepth = 0
                if (angleDepth == 0 && (i == 0 || !(line[i - 1].isLetterOrDigit() || line[i - 1] in "_$"))) {
                    val clause = clauseRegex.find(line.substring(i))
                    if (clause != null) {
                        val before = line.substring(start, i).trim()
                        if (before.isNotEmpty()) output.add(before)
                        start = i
                        i += clause.value.length
                        continue
                    }
                }
                i++
            }
            output.add(if (start == 0) line else line.substring(start).trim())
        }

        return output
    }

    private fun applyRule7AssignmentRhs(lines: List<String>): List<String> {
        val output = mutableListOf<String>()
        var pendingAssignRhs = false

        for (idx in lines.indices) {
            val line = lines[idx]
            var trimmed = line.trim()

            if (pendingAssignRhs) {
                pendingAssignRhs = false

                val nextNonEmpty = lines
                    .drop(idx + 1)
                    .asSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }

                val isTernaryConditionLine =
                    trimmed.startsWith("(") ||
                            trimmed.contains("!=") ||
                            trimmed.contains("==") ||
                            trimmed.contains("<") ||
                            trimmed.contains(">")

                if (isTernaryConditionLine && (nextNonEmpty?.startsWith("?") == true || nextNonEmpty?.startsWith(":") == true)) {
                    output.add(trimmed)
                } else {
                    output.add("ASSIGN_RHS:$trimmed")
                }
                continue
            }

            trimmed = normalizeAssignmentSpaces(trimmed)
            trimmed = normalizeParenSpaces(trimmed)

            val codeWithoutStrings = trimmed.replace(Regex("""("([^"\\]|\\.)*"|'([^'\\]|\\.)*')"""), "\"\"")
            val eqIdx = findAssignmentOperatorIndex(trimmed)

            val isAssignment =
                eqIdx != -1 &&
                !codeWithoutStrings.startsWith("for ") &&
                !codeWithoutStrings.startsWith("if ") &&
                !codeWithoutStrings.startsWith("while ") &&
                !codeWithoutStrings.endsWith("{")

            if (isAssignment) {
                val lhs = trimmed.substring(0, eqIdx + 2).trim()
                val rhs = trimmed.substring(eqIdx + 3).trim()

                if (line.length >= config.maxLineLength) {
                    val nextNonEmpty = lines
                        .drop(idx + 1)
                        .asSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                    val rhsLooksLikeTernaryCondition =
                        rhs.startsWith("(") ||
                                rhs.contains("!=") ||
                                rhs.contains("==") ||
                                rhs.contains("<") ||
                                rhs.contains(">")

                    output.add(lhs)
                    if (rhsLooksLikeTernaryCondition && (nextNonEmpty?.startsWith("?") == true || nextNonEmpty?.startsWith(":") == true)) {
                        output.add(rhs)
                    } else {
                        output.add("ASSIGN_RHS:$rhs")
                    }
                    continue
                }
            }

            val isLineEndingWithEquals =
                (codeWithoutStrings.endsWith(" =") || codeWithoutStrings.endsWith("=")) &&
                !codeWithoutStrings.startsWith("for ") &&
                !codeWithoutStrings.startsWith("if ") &&
                !codeWithoutStrings.startsWith("while ") &&
                !codeWithoutStrings.endsWith("{")

            if (isLineEndingWithEquals) {
                output.add(trimmed)
                pendingAssignRhs = true
                continue
            }

            output.add(line)
        }

        return output
    }

    private fun applyRuleTernaryOperators(lines: List<String>): List<String> {
        val output = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val normalizedLine = if (trimmed.startsWith("ASSIGN_RHS:")) {
                trimmed.substring("ASSIGN_RHS:".length).trim()
            } else {
                trimmed
            }

            if (normalizedLine.startsWith("//") || normalizedLine.startsWith("/*") || normalizedLine.startsWith("*") || normalizedLine.startsWith("import ") || normalizedLine.startsWith("package ")) {
                output.add(line)
                i++
                continue
            }

            var statementText = normalizedLine
            var nextIndex = i + 1

            val startsAsContinuation = normalizedLine.startsWith("=") || trimmed.startsWith("ASSIGN_RHS:")

            var nextNonEmptyIndex = i + 1
            while (nextNonEmptyIndex < lines.size && lines[nextNonEmptyIndex].trim().isEmpty()) {
                nextNonEmptyIndex++
            }
            val nextNonEmptyTrimmed = if (nextNonEmptyIndex < lines.size) lines[nextNonEmptyIndex].trim() else ""

            val nextIsTernary = nextNonEmptyTrimmed.startsWith("?") || nextNonEmptyTrimmed.startsWith(":")
            val hasTernaryIndicators = statementText.contains('?') || statementText.contains(':') || normalizedLine.endsWith("=") || normalizedLine.contains(" =") || nextIsTernary

            if (hasTernaryIndicators && !statementText.endsWith(";") && !statementText.endsWith("{")) {
                var accumulated = statementText
                var lookaheadIdx = i + 1
                var foundTernary = accumulated.contains('?') || accumulated.contains(':')

                while (lookaheadIdx < lines.size) {
                    val nextRawTrimmed = lines[lookaheadIdx].trim()
                    val nextTrimmed = if (nextRawTrimmed.startsWith("ASSIGN_RHS:")) {
                        nextRawTrimmed.substring("ASSIGN_RHS:".length).trim()
                    } else {
                        nextRawTrimmed
                    }
                    if (nextTrimmed.startsWith("//") || nextTrimmed.startsWith("/*") || nextTrimmed.startsWith("@") || nextTrimmed.startsWith("class ") || nextTrimmed.startsWith("public ")) {
                        break
                    }
                    accumulated += " " + nextTrimmed
                    if (nextTrimmed.contains('?') || nextTrimmed.contains(':')) {
                        foundTernary = true
                    }
                    lookaheadIdx++
                    if (nextTrimmed.endsWith(";") || nextTrimmed.endsWith("{")) {
                        break
                    }
                }

                if (foundTernary && hasTernaryOperator(accumulated)) {
                    statementText = accumulated
                    nextIndex = lookaheadIdx
                }
            }

            if (hasTernaryOperator(statementText)) {
                val ternaryParts = splitTernaryExpression(statementText)
                if (ternaryParts != null) {
                    val (condition, trueExpr, falseExpr) = ternaryParts
                    val normalizedCond = normalizeOperatorSpaces(condition).replace(Regex("""\s*\.\s*"""), ".")
                    val normalizedTrue = trueExpr.trim().replace(Regex("""\s*\.\s*"""), ".")
                    val normalizedFalse = falseExpr.trim().replace(Regex("""\s*\.\s*"""), ".")

                    val fullSingleLine = "$normalizedCond ? $normalizedTrue : $normalizedFalse"
                    val isAssignmentTernary =
                        Regex("""(^|\s)=($|\s|\()""").containsMatchIn(normalizedCond) &&
                                !normalizedCond.contains("==")
                    val shouldWrapMultiline = isAssignmentTernary || startsAsContinuation || fullSingleLine.length >= config.maxLineLength

                    if (shouldWrapMultiline) {
                        var conditionLine = normalizedCond
                        if (conditionLine.startsWith("=") && output.isNotEmpty()) {
                            val previous = output.last()
                            output[output.lastIndex] = if (previous.trim().endsWith("=")) {
                                previous
                            } else {
                                "$previous ="
                            }
                            conditionLine = conditionLine.removePrefix("=").trim()
                        }

                        output.add(conditionLine)
                        output.add("? $normalizedTrue")
                        output.add(": $normalizedFalse")
                        i = nextIndex
                        continue
                    } else {
                        output.add(fullSingleLine)
                        i = nextIndex
                        continue
                    }
                }
            }

            output.add(line)
            i++
        }

        return output
    }

    private fun hasTernaryOperator(line: String): Boolean {
        var inString = false
        var stringChar = ' '
        var hasQuestion = false
        var hasColon = false

        for (i in line.indices) {
            val char = line[i]
            if (inString) {
                if (char == stringChar && (i == 0 || line[i - 1] != '\\')) inString = false
                continue
            }
            if (char == '"' || char == '\'') {
                inString = true
                stringChar = char
                continue
            }
            if (char == '?') {
                hasQuestion = true
            } else if (char == ':' && hasQuestion) {
                hasColon = true
            }
        }
        return hasQuestion && hasColon
    }

    private data class TernaryParts(val condition: String, val trueExpr: String, val falseExpr: String)

    private fun splitTernaryExpression(line: String): TernaryParts? {
        var inString = false
        var stringChar = ' '
        var questionIdx = -1
        var colonIdx = -1
        var depth = 0

        for (i in line.indices) {
            val char = line[i]
            if (inString) {
                if (char == stringChar && (i == 0 || line[i - 1] != '\\')) inString = false
                continue
            }
            if (char == '"' || char == '\'') {
                inString = true
                stringChar = char
                continue
            }
            if (char == '(' || char == '<' || char == '[') {
                depth++
            } else if (char == ')' || char == '>' || char == ']') {
                depth = maxOf(0, depth - 1)
            } else if (depth == 0) {
                if (char == '?' && questionIdx == -1) {
                    questionIdx = i
                } else if (char == ':' && questionIdx != -1 && colonIdx == -1) {
                    colonIdx = i
                }
            }
        }

        if (questionIdx != -1 && colonIdx != -1 && colonIdx > questionIdx) {
            val condition = line.substring(0, questionIdx).trim()
            val trueExpr = line.substring(questionIdx + 1, colonIdx).trim()
            val falseExpr = line.substring(colonIdx + 1).trim()
            return TernaryParts(condition, trueExpr, falseExpr)
        }

        return null
    }

    private fun findAssignmentOperatorIndex(line: String): Int {
        var inString = false
        var stringChar = ' '
        var parenDepth = 0

        for (i in 0 until line.length - 2) {
            val char = line[i]
            if (inString) {
                if (char == stringChar && (i == 0 || line[i - 1] != '\\')) inString = false
                continue
            }
            if (char == '"' || char == '\'') {
                inString = true
                stringChar = char
                continue
            }
            if (char == '(') parenDepth++
            else if (char == ')') parenDepth = maxOf(0, parenDepth - 1)

            if (parenDepth == 0 && line.substring(i, i + 3) == " = ") {
                return i
            }
        }
        return -1
    }

    private fun formatCodeLines(lines: List<String>): String {
        val formatted = formatLinesWithIndent(lines, 0)
        return postProcessBlankLines(formatted).joinToString("\n")
    }

    private fun formatLinesWithIndent(lines: List<String>, initialIndentLevel: Int): List<String> {
        val continuationIndent = config.continuationIndent
        fun indentation(columns: Int): String = if (config.insertSpaces) " ".repeat(columns)
            else "\t".repeat(columns / config.tabSize) + " ".repeat(columns % config.tabSize)
        var blockColumns = initialIndentLevel * config.tabSize
        val parenColumnsStack = mutableListOf<Int>()
        val outputLines = mutableListOf<String>()

        var inJavadoc = false
        var inBlockComment = false
        var inTextBlock = false

        var consumedLine = -1
        for (i in lines.indices) {
            if (i == consumedLine) continue
            if (inTextBlock) {
                outputLines.add(lines[i])
                if (lines[i].contains("\"\"\"")) inTextBlock = false
                continue
            }

            var line = lines[i].trim()

            // Lay out assignment call headers using their final indentation.
            val callHeader = Regex("""^(?:new\s+)?[\w$.]+(?:<[^;{}]*>)?\s*\($""")
            if (line.endsWith(" =") && i + 1 < lines.size) {
                val call = lines[i + 1].trim().removePrefix("ASSIGN_RHS:").trim()
                if (callHeader.matches(call)) {
                    line = "$line $call"
                    consumedLine = i + 1
                }
            }
            val assignment = findAssignmentOperatorIndex(line)
            val columns = blockColumns + (parenColumnsStack.lastOrNull() ?: 0)
            if (assignment >= 0 && callHeader.matches(line.substring(assignment + 3).trim()) &&
                columns + line.length > config.maxLineLength) {
                outputLines.add(indentation(columns) + line.substring(0, assignment).trimEnd())
                line = line.substring(assignment + 1)
            }

            if (line.isEmpty()) {
                outputLines.add("")
                continue
            }

            if (line.contains("\"\"\"")) {
                val count = Regex("""\"\"\"""").findAll(line).count()
                if (count % 2 != 0) inTextBlock = true
            }

            var isAssignRhs = false
            if (line.startsWith("ASSIGN_RHS:")) {
                line = line.substring("ASSIGN_RHS:".length).trim()
                isAssignRhs = true
            }

            var isNestedCall = false
            if (line.startsWith("NESTED_CALL:")) {
                line = line.substring("NESTED_CALL:".length).trim()
                isNestedCall = true
            }

            val startsWithClosingBrace = line.startsWith("}")
            if (startsWithClosingBrace) {
                blockColumns = maxOf(0, blockColumns - config.tabSize)
            }
            val startsWithClosingParen = line.startsWith(")") || line.startsWith(");")
            if (startsWithClosingParen && parenColumnsStack.isNotEmpty()) {
                parenColumnsStack.removeAt(parenColumnsStack.size - 1)
            }

            val parenColumns = if (parenColumnsStack.isNotEmpty()) parenColumnsStack.last() else 0
            val previousNonEmptyLine = run {
                var j = i - 1
                var found = ""
                while (j >= 0) {
                    val t = lines[j].trim().removePrefix("ASSIGN_RHS:").trim()
                    if (t.isNotEmpty()) {
                        found = t
                        break
                    }
                    j--
                }
                found
            }
            val previousBeforeNonEmptyLine = run {
                var j = i - 1
                var seen = 0
                var found = ""
                while (j >= 0) {
                    val t = lines[j].trim().removePrefix("ASSIGN_RHS:").trim()
                    if (t.isNotEmpty()) {
                        seen++
                        if (seen == 2) {
                            found = t
                            break
                        }
                    }
                    j--
                }
                found
            }
            val previousThirdNonEmptyLine = run {
                var j = i - 1
                var seen = 0
                var found = ""
                while (j >= 0) {
                    val t = lines[j].trim().removePrefix("ASSIGN_RHS:").trim()
                    if (t.isNotEmpty()) {
                        seen++
                        if (seen == 3) {
                            found = t
                            break
                        }
                    }
                    j--
                }
                found
            }
            val isAssignmentTernaryBranchLine =
                (line.startsWith("?") &&
                        previousBeforeNonEmptyLine.endsWith("=") &&
                        !previousNonEmptyLine.startsWith("?") &&
                        !previousNonEmptyLine.startsWith(":")) ||
                        (line.startsWith(":") &&
                                previousNonEmptyLine.startsWith("?") &&
                                previousThirdNonEmptyLine.endsWith("="))

            val isContinuationLine = isAssignRhs || line.startsWith("=") || line.startsWith(".") || ((line.startsWith("?") || line.startsWith(":")) && !isAssignmentTernaryBranchLine) || line.startsWith("extends") || line.startsWith("implements") || line.startsWith("throws")
            val isOperatorContinuation = line.startsWith("+") && !line.startsWith("++")
            val extraIndent = if (isContinuationLine || isOperatorContinuation) continuationIndent else 0
            val indentColumns = maxOf(0, blockColumns + parenColumns + extraIndent)

            if (line.startsWith("/**")) {
                inJavadoc = true
                outputLines.add(indentation(indentColumns) + line)
                if (line.endsWith("*/")) inJavadoc = false
                continue
            }
            if (inJavadoc) {
                if (line.startsWith("*")) {
                    outputLines.add(indentation(indentColumns) + " " + line)
                } else {
                    outputLines.add(indentation(indentColumns) + line)
                }
                if (line.contains("*/")) inJavadoc = false
                continue
            }

            if (line.startsWith("/*")) {
                inBlockComment = true
                outputLines.add(indentation(indentColumns) + line)
                if (line.endsWith("*/")) inBlockComment = false
                continue
            }
            if (inBlockComment) {
                outputLines.add(indentation(indentColumns) + line)
                if (line.contains("*/")) inBlockComment = false
                continue
            }

            if (line.startsWith("//")) {
                outputLines.add(indentation(indentColumns) + line)
                continue
            }

            line = normalizeControlFlowSpaces(line)
            line = normalizeAssignmentSpaces(line)
            line = normalizeOperatorSpaces(line)
            line = normalizeParenSpaces(line)
            line = normalizeCommaSpaces(line)
            line = normalizeBraceSpaces(line)

            val currentLineIndent = indentation(indentColumns)
            val fullLine = currentLineIndent + line

            outputLines.add(fullLine)

            val codeWithoutStrings = line.replace(Regex("""("([^"\\]|\\.)*"|'([^'\\]|\\.)*')"""), "\"\"")

            for (chIdx in codeWithoutStrings.indices) {
                val char = codeWithoutStrings[chIdx]
                if (char == '{') {
                    blockColumns += config.tabSize
                } else if (char == '}') {
                    if (!startsWithClosingBrace) {
                        blockColumns = maxOf(0, blockColumns - config.tabSize)
                    }
                } else if (char == '(' || char == '[') {
                    val textAfterParen = codeWithoutStrings.substring(chIdx + 1).trim()
                    val hasCodeAfterParen = textAfterParen.isNotEmpty() && !textAfterParen.startsWith("//")
                    val addedParenIndent = if (hasCodeAfterParen) {
                        if (isNestedCall && parenColumnsStack.isNotEmpty()) {
                            parenColumnsStack.last() + continuationIndent
                        } else if (parenColumnsStack.isNotEmpty()) {
                            parenColumnsStack.last()
                        } else {
                            continuationIndent
                        }
                    } else {
                        indentColumns + continuationIndent - blockColumns
                    }
                    parenColumnsStack.add(addedParenIndent)
                } else if (char == ')' || char == ']') {
                    if (!startsWithClosingParen && parenColumnsStack.isNotEmpty()) {
                        parenColumnsStack.removeAt(parenColumnsStack.size - 1)
                    }
                }
            }
        }

        return outputLines
    }

    private fun normalizeControlFlowSpaces(line: String): String {
        return line
            .replace(Regex("""\bif\s*\("""), "if (")
            .replace(Regex("""\bfor\s*\("""), "for (")
            .replace(Regex("""\bwhile\s*\("""), "while (")
            .replace(Regex("""\bswitch\s*\("""), "switch (")
            .replace(Regex("""\bcatch\s*\("""), "catch (")
            .replace(Regex("""\}\s*else\s*if\b"""), "} else if")
            .replace(Regex("""\}\s*else\b"""), "} else")
            .replace(Regex("""\}\s*catch\b"""), "} catch")
            .replace(Regex("""\}\s*finally\b"""), "} finally")
    }

    private fun normalizeAssignmentSpaces(line: String): String {
        val parts = mutableListOf<String>()
        val regex = Regex("""("([^"\\]|\\.)*"|'([^'\\]|\\.)*')""")
        var lastIdx = 0

        for (m in regex.findAll(line)) {
            val codeBefore = line.substring(lastIdx, m.range.first)
            parts.add(fixAssignmentOperatorSpaces(codeBefore))
            parts.add(m.value)
            lastIdx = m.range.last + 1
        }

        val codeRest = line.substring(lastIdx)
        parts.add(fixAssignmentOperatorSpaces(codeRest))

        return parts.joinToString("")
    }

    private fun fixAssignmentOperatorSpaces(code: String): String {
        var res = code.replace(Regex("""([^!\+\-\*\/\%\|\&\^\=\<\>\s])=(?!=)"""), "$1 =")
        res = res.replace(Regex("""(?<![!\+\-\*\/\%\|\&\^\=\<\>])=([^=\s])"""), "= $1")
        return res
    }

    private fun normalizeParenSpaces(line: String): String {
        val parts = mutableListOf<String>()
        val regex = Regex("""("([^"\\]|\\.)*"|'([^'\\]|\\.)*')""")
        var lastIdx = 0

        for (m in regex.findAll(line)) {
            val codeBefore = line.substring(lastIdx, m.range.first)
            parts.add(codeBefore.replace(Regex("""\(\s+"""), "(").replace(Regex("""\s+\)"""), ")"))
            parts.add(m.value)
            lastIdx = m.range.last + 1
        }

        val codeRest = line.substring(lastIdx)
        parts.add(codeRest.replace(Regex("""\(\s+"""), "(").replace(Regex("""\s+\)"""), ")"))

        return parts.joinToString("")
    }

    private fun normalizeOperatorSpaces(line: String): String {
        val parts = mutableListOf<String>()
        val regex = Regex("""("([^"\\]|\\.)*"|'([^'\\]|\\.)*')""")
        var lastIdx = 0

        for (m in regex.findAll(line)) {
            val codeBefore = line.substring(lastIdx, m.range.first)
            parts.add(fixOperatorSpaces(codeBefore))
            parts.add(m.value)
            lastIdx = m.range.last + 1
        }

        val codeRest = line.substring(lastIdx)
        parts.add(fixOperatorSpaces(codeRest))

        return parts.joinToString("")
    }

    private fun fixOperatorSpaces(code: String): String {
        return code
            .replace(Regex("""\s*!=\s*"""), " != ")
            .replace(Regex("""(?<![!=><])\s*==\s*(?!=)"""), " == ")
            .replace(Regex("""(?<![<])\s*<=\s*(?!=)"""), " <= ")
            .replace(Regex("""(?<![>])\s*>=\s*(?!=)"""), " >= ")
    }

    private fun normalizeCommaSpaces(line: String): String {
        return line.replace(Regex(""",([^\s\/\*])"""), ", $1")
    }

    private fun normalizeBraceSpaces(line: String): String {
        return line.replace(Regex("""([a-zA-Z0-9_\>\]\)])\{"""), "$1 {")
    }

    private fun postProcessBlankLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var consecutiveEmpty = 0

        for (i in lines.indices) {
            val line = lines[i]
            val isEmpty = line.trim().isEmpty()
            if (isEmpty) {
                var nextNonEmpty = ""
                for (j in (i + 1) until lines.size) {
                    val t = lines[j].trim()
                    if (t.isNotEmpty()) {
                        nextNonEmpty = t
                        break
                    }
                }
                if (nextNonEmpty.startsWith("?") || nextNonEmpty.startsWith(":") || nextNonEmpty.startsWith(".")) {
                    continue
                }

                consecutiveEmpty++
                if (consecutiveEmpty <= 1) {
                    result.add(line)
                }
            } else {
                consecutiveEmpty = 0
                result.add(line)
            }
        }

        return result
    }

    companion object {
        fun format(sourceCode: String, config: AccentFormatterConfig = AccentFormatterConfig()): String {
            return LegacyAccentJavaFormatter(config).formatDocument(sourceCode)
        }
    }
}
