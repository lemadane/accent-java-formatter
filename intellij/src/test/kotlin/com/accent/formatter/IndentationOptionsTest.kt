package com.accent.formatter

import org.junit.Assert.assertEquals
import org.junit.Test

class IndentationOptionsTest {
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
