package com.accent.formatter

import com.accent.formatter.ast.AstFormattingEngine

class AccentJavaFormatter(private val config: AccentFormatterConfig = AccentFormatterConfig()) {
    private val astFormattingEngine = AstFormattingEngine(config)

    fun formatDocument(sourceCode: String): String {
        return astFormattingEngine.formatDocument(sourceCode)
    }

    companion object {
        fun format(sourceCode: String, config: AccentFormatterConfig = AccentFormatterConfig()): String {
            return AccentJavaFormatter(config).formatDocument(sourceCode)
        }
    }
}
