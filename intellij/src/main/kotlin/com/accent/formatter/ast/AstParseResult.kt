package com.accent.formatter.ast

data class AstParseResult(
    val parseSucceeded: Boolean,
    val parseErrors: List<String>
)
