package com.accent.formatter

import java.io.File
import com.github.javaparser.StaticJavaParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class IndentationTest(private val input: File) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<Array<Any>> = File("../formatting-parity/indentation")
            .listFiles()!!.filter { it.name.endsWith(".input.java") }.sortedBy { it.name }
            .map { arrayOf<Any>(it) }
    }

    @Test
    fun formatsSafelyAndIdempotently() {
        val source = input.readText()
        val expected = File(input.path.replace(".input.", ".expected.")).readText()
        val formatter = AccentJavaFormatter(AccentFormatterConfig())
        val actual = formatter.formatDocument(source)
        assertEquals(expected, actual)
        assertEquals("Formatting must be idempotent", actual, formatter.formatDocument(actual))
        // Independent Java parser verifies syntax and semantic structure, including literals.
        StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17)
        val before = StaticJavaParser.parse(source).tokenRange.get().map { it.text }.filter { it.isNotBlank() }
        val after = StaticJavaParser.parse(actual).tokenRange.get().map { it.text }.filter { it.isNotBlank() }
        assertEquals(before, after)
    }
}
