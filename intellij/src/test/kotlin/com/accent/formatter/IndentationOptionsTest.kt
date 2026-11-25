package com.accent.formatter

import org.junit.Assert.assertEquals
import org.junit.Test

class IndentationOptionsTest {
    @Test
    fun returnConcatenationContinuationWidths() {
        for (continuationIndent in listOf(0, 2, 6)) {
            for (insertSpaces in listOf(true, false)) {
                val formatter = AccentJavaFormatter(AccentFormatterConfig(continuationIndent = continuationIndent, insertSpaces = insertSpaces))
                val source = "class Sample {\nString url() {\nreturn normalizedBaseUrl\n+ \"/transform/\"\n+ projectName.trim();\n}\n}\n"
                fun indent(columns: Int) = if (insertSpaces) " ".repeat(columns) else "\t".repeat(columns / 4) + " ".repeat(columns % 4)
                val expected = "class Sample {\n${indent(4)}String url() {\n${indent(8)}return normalizedBaseUrl\n${indent(8 + continuationIndent)}+ \"/transform/\"\n${indent(8 + continuationIndent)}+ projectName.trim();\n${indent(4)}}\n}\n"
                assertEquals(expected, formatter.formatDocument(source))
                assertEquals(expected, formatter.formatDocument(expected))
            }
        }
    }

    private val source = "class Sample {\nvoid run() {\nprint(convert(resolve(value)));\n}\n}\n"

    @Test
    fun independentBlockWidth() {
        val formatter = AccentJavaFormatter(AccentFormatterConfig(tabSize = 2))
        val expected = "class Sample {\n  void run() {\n    print(\n      convert(\n        resolve(value)));\n  }\n}\n"
        assertEquals(expected, formatter.formatDocument(source))
        assertEquals(expected, formatter.formatDocument(expected))
    }

    @Test
    fun tabsAndPartialTabContinuations() {
        val formatter = AccentJavaFormatter(AccentFormatterConfig(insertSpaces = false))
        val expected = "class Sample {\n\tvoid run() {\n\t\tprint(\n\t\t  convert(\n\t\t\tresolve(value)));\n\t}\n}\n"
        assertEquals(expected, formatter.formatDocument(source))
        assertEquals(expected, formatter.formatDocument(expected))
    }
}
