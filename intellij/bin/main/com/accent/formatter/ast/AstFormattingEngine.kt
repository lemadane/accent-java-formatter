package com.accent.formatter.ast

import com.accent.formatter.AccentFormatterConfig
import com.accent.formatter.LegacyAccentJavaFormatter

class AstFormattingEngine(private val config: AccentFormatterConfig) {
    private val legacyFormatter = LegacyAccentJavaFormatter(config)
    var lastParseResult: AstParseResult? = null
        private set

    fun formatDocument(sourceCode: String): String {
        lastParseResult = AstParser.parse(sourceCode)

        // Keep output parity while all formatting requests pass through AST parsing.
        return legacyFormatter.formatDocument(sourceCode)
    }
}
