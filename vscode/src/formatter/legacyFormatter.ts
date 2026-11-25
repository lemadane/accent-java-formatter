import { protectNonCode, skipNonCode, formatCalls, expandBlocks, formatAnnotations } from './structuralFormatting';
import { FormatterConfig } from './config';
import { organizeJavaImports } from './organizeImports';

export class LegacyJavaFormatter {
    private config: FormatterConfig;

    constructor(config: FormatterConfig) {
        this.config = config;
    }

    public formatDocument(sourceCode: string): string {
        const protectedCode = protectNonCode(sourceCode);
        let lines = protectedCode.source.split(/\r?\n/);

        // 1. Organize imports if enabled
        if (this.config.organizeImportsOnFormat) {
            const importResult = organizeJavaImports(lines);
            if (importResult.hasImports) {
                const beforeImports = lines.slice(0, importResult.startLineIndex);
                const afterImports = lines.slice(importResult.endLineIndex + 1);
                const organizedLines = importResult.importsText.split('\n');
                lines = [...beforeImports, ...organizedLines, ...afterImports];
            }
        }

        // 2. Apply Java declaration rules and document-wide structural layout
        lines = this.preprocessFormattingRules(lines);

        // 3. Format structure, indentation, braces, annotations & line wrapping (Rule 9)
        return protectedCode.restore(this.formatCodeLines(lines));
    }

    public formatRange(sourceCode: string, startLine: number, endLine: number): string {
        const rawLines = sourceCode.split(/\r?\n/);
        const first = Math.max(0, startLine);
        const last = Math.min(rawLines.length - 1, endLine);
        if (first > last) return sourceCode;
        const normalized = rawLines.join('\n');
        const start = rawLines.slice(0, first).reduce((n, line) => n + line.length + 1, 0);
        const end = start + rawLines.slice(first, last + 1).join('\n').length;
        let baseIndentLevel = 0;
        for (let i = 0; i < normalized.length;) {
            const skipped = skipNonCode(normalized, i);
            if (skipped !== i) {
                // A partial selection inside a literal/comment must never rewrite its contents.
                if ((i < start && skipped > start) || (i < end && skipped > end)) return sourceCode;
                i = skipped;
                continue;
            }
            if (i < start) {
                if (normalized[i] === '{') baseIndentLevel++;
                if (normalized[i] === '}') baseIndentLevel = Math.max(0, baseIndentLevel - 1);
            }
            i++;
        }
        const protectedCode = protectNonCode(rawLines.slice(first, last + 1).join('\n'));
        const processedRange = this.preprocessFormattingRules(protectedCode.source.split('\n'), baseIndentLevel);
        const formatted = protectedCode.restore(this.formatLinesWithIndent(processedRange, baseIndentLevel).join('\n'));
        return [...rawLines.slice(0, first), formatted, ...rawLines.slice(last + 1)].join('\n');
    }

    private preprocessFormattingRules(lines: string[], baseIndentLevel = 0): string[] {
        let result = lines;

        // Keep Java-specific clause and assignment rules, then lay out complete expressions.
        result = this.applyRule5Clauses(result);
        result = formatAnnotations(result.join("\n")).split("\n");
        result = this.applyRuleTernaryOperators(result);
        result = this.applyRule7AssignmentRhs(result);
        result = formatCalls(expandBlocks(result.join("\n")), this.config.maxLineLength, this.config.tabSize, baseIndentLevel, 0, this.config.continuationIndent ?? 2).split("\n");

        return result;
    }

    /**
     * Rule 5: extends, implements, throws on the next line together with associated class/interface/exception
     */
    private applyRule5Clauses(lines: string[]): string[] {
        const output: string[] = [];
        let angleDepth = 0;

        for (const line of lines) {
            let start = 0;
            for (let i = 0; i < line.length;) {
                const skipped = skipNonCode(line, i);
                if (skipped !== i) { i = skipped; continue; }
                const ch = line[i];
                // Bounds inside type parameters/arguments are not inheritance clauses.
                if (ch === '<') angleDepth++;
                else if (ch === '>') angleDepth = Math.max(0, angleDepth - 1);
                else if (';{}'.includes(ch)) angleDepth = 0;
                if (angleDepth === 0 && (i === 0 || !/[\w$]/.test(line[i - 1]))) {
                    const clause = /^(extends|implements|throws)\b/.exec(line.slice(i));
                    if (clause) {
                        const before = line.slice(start, i).trim();
                        if (before) output.push(before);
                        start = i;
                        i += clause[0].length;
                        continue;
                    }
                }
                i++;
            }
            output.push(start === 0 ? line : line.slice(start).trim());
        }

        return output;
    }

    private applyRule7AssignmentRhs(lines: string[]): string[] {
        const output: string[] = [];
        let pendingAssignRhs = false;
        let inTextBlock = false;

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (inTextBlock) {
                output.push(line);
                if (line.includes('"""')) inTextBlock = false;
                continue;
            }
            if (line.includes('"""')) {
                const count = (line.match(/"""/g) || []).length;
                if (count % 2 !== 0) inTextBlock = true;
            }

            let trimmed = line.trim();

            if (pendingAssignRhs) {
                pendingAssignRhs = false;
                output.push(`ASSIGN_RHS:${trimmed}`);
                continue;
            }

            trimmed = this.normalizeAssignmentSpaces(trimmed);
            trimmed = this.normalizeParenSpaces(trimmed);

            const codeWithoutStrings = trimmed.replace(/"([^"\\]|\\.)*"/g, '""').replace(/'([^'\\]|\\.)*'/g, "''");
            const eqIdx = this.findAssignmentOperatorIndex(trimmed);

            const isAssignment =
                eqIdx !== -1 &&
                !codeWithoutStrings.startsWith('for ') &&
                !codeWithoutStrings.startsWith('if ') &&
                !codeWithoutStrings.startsWith('while ') &&
                !codeWithoutStrings.endsWith('{');

            if (isAssignment) {
                const lhs = trimmed.slice(0, eqIdx + 2).trim();
                const rhs = trimmed.slice(eqIdx + 3).trim();

                if (line.length >= this.config.maxLineLength) {
                    output.push(lhs);
                    output.push(`ASSIGN_RHS:${rhs}`);
                    continue;
                }
            }

            const isLineEndingWithEquals =
                (codeWithoutStrings.endsWith(' =') || codeWithoutStrings.endsWith('=')) &&
                !codeWithoutStrings.startsWith('for ') &&
                !codeWithoutStrings.startsWith('if ') &&
                !codeWithoutStrings.startsWith('while ') &&
                !codeWithoutStrings.endsWith('{');

            if (isLineEndingWithEquals) {
                output.push(trimmed);
                pendingAssignRhs = true;
                continue;
            }

            output.push(line);
        }

        return output;
    }

    private findAssignmentOperatorIndex(line: string): number {
        let inString = false;
        let stringChar = '';
        let parenDepth = 0;

        for (let i = 0; i < line.length - 2; i++) {
            const char = line[i];
            if (inString) {
                if (char === stringChar && line[i - 1] !== '\\') {
                    inString = false;
                }
                continue;
            }
            if (char === '"' || char === "'") {
                inString = true;
                stringChar = char;
                continue;
            }
            if (char === '(') {
                parenDepth++;
            } else if (char === ')') {
                parenDepth = Math.max(0, parenDepth - 1);
            }

            if (parenDepth === 0 && line.slice(i, i + 3) === ' = ') {
                return i;
            }
        }
        return -1;
    }

    private formatCodeLines(lines: string[]): string {
        const formatted = this.formatLinesWithIndent(lines, 0);
        return this.postProcessBlankLines(formatted).join('\n');
    }

    private formatLinesWithIndent(lines: string[], initialIndentLevel: number): string[] {
        const continuationIndent = this.config.continuationIndent ?? 2;
        const indentation = (columns: number) => this.config.insertSpaces
            ? ' '.repeat(columns)
            : '\t'.repeat(Math.floor(columns / this.config.tabSize)) + ' '.repeat(columns % this.config.tabSize);
        let blockColumns = initialIndentLevel * this.config.tabSize;
        const parenColumnsStack: number[] = [];
        const outputLines: string[] = [];

        let inJavadoc = false;
        let inBlockComment = false;
        let inTextBlock = false;

        for (let i = 0; i < lines.length; i++) {
            if (inTextBlock) {
                outputLines.push(lines[i]);
                if (lines[i].includes('"""')) {
                    inTextBlock = false;
                }
                continue;
            }

            let line = lines[i].trim();

            // Lay out assignment call headers using their final indentation.
            const callHeader = /^(?:new\s+)?[\w$.]+(?:<[^;{}]*>)?\s*\($/;
            if (line.endsWith(' =') && i + 1 < lines.length) {
                const call = lines[i + 1].trim().replace(/^ASSIGN_RHS:/, '').trim();
                if (callHeader.test(call)) {
                    line = `${line} ${call}`;
                    i++;
                }
            }
            const assignment = this.findAssignmentOperatorIndex(line);
            const columns = blockColumns + (parenColumnsStack.at(-1) ?? 0);
            if (assignment >= 0 && callHeader.test(line.slice(assignment + 3).trim()) &&
                columns + line.length > this.config.maxLineLength) {
                outputLines.push(indentation(columns) + line.slice(0, assignment).trimEnd());
                line = line.slice(assignment + 1);
            }

            if (line === '') {
                outputLines.push('');
                continue;
            }

            if (line.includes('"""')) {
                const tripleQuoteCount = (line.match(/"""/g) || []).length;
                if (tripleQuoteCount % 2 !== 0) {
                    inTextBlock = true;
                }
            }

            let isAssignRhs = false;
            if (line.startsWith('ASSIGN_RHS:')) {
                line = line.slice('ASSIGN_RHS:'.length).trim();
                isAssignRhs = true;
            }

            let isNestedCall = false;
            if (line.startsWith('NESTED_CALL:')) {
                line = line.slice('NESTED_CALL:'.length).trim();
                isNestedCall = true;
            }

            const startsWithClosingBrace = line.startsWith('}');
            if (startsWithClosingBrace) {
                blockColumns = Math.max(0, blockColumns - this.config.tabSize);
            }
            const startsWithClosingParen = line.startsWith(')') || line.startsWith(');');
            if (startsWithClosingParen && parenColumnsStack.length > 0) {
                parenColumnsStack.pop();
            }

            const parenColumns = parenColumnsStack.length > 0 ? parenColumnsStack[parenColumnsStack.length - 1] : 0;
            const isContinuationLine = isAssignRhs || line.startsWith('=') || line.startsWith('.') || line.startsWith('?') || line.startsWith(':') || line.startsWith('extends') || line.startsWith('implements') || line.startsWith('throws');
            const extraIndent = isContinuationLine ? continuationIndent : 0;
            const isOperatorContinuation = line.startsWith('+') && !line.startsWith('++');
            const indentColumns = isOperatorContinuation
                ? Math.max(0, blockColumns + parenColumns + continuationIndent)
                : Math.max(0, blockColumns + parenColumns + extraIndent);

            if (line.startsWith('/**')) {
                inJavadoc = true;
                outputLines.push(indentation(indentColumns) + line);
                if (line.endsWith('*/')) inJavadoc = false;
                continue;
            }
            if (inJavadoc) {
                if (line.startsWith('*')) {
                    outputLines.push(indentation(indentColumns) + ' ' + line);
                } else {
                    outputLines.push(indentation(indentColumns) + line);
                }
                if (line.includes('*/')) inJavadoc = false;
                continue;
            }

            if (line.startsWith('/*')) {
                inBlockComment = true;
                outputLines.push(indentation(indentColumns) + line);
                if (line.endsWith('*/')) inBlockComment = false;
                continue;
            }
            if (inBlockComment) {
                outputLines.push(indentation(indentColumns) + line);
                if (line.includes('*/')) inBlockComment = false;
                continue;
            }

            if (line.startsWith('//')) {
                outputLines.push(indentation(indentColumns) + line);
                continue;
            }

            if (this.config.braceStyle === 'nextLine' && line.endsWith('{') && line.length > 1 && !line.startsWith('class ') && !line.startsWith('interface ')) {
                const codeWithoutBrace = line.slice(0, -1).trim();
                if (codeWithoutBrace.length > 0) {
                    outputLines.push(indentation(indentColumns) + codeWithoutBrace);
                    outputLines.push(indentation(indentColumns) + '{');
                    blockColumns += this.config.tabSize;
                    continue;
                }
            }

            line = this.normalizeControlFlowSpaces(line);
            line = this.normalizeAssignmentSpaces(line);
            line = this.normalizeOperatorSpaces(line);
            line = this.normalizeParenSpaces(line);
            line = this.normalizeCommaSpaces(line);
            line = this.normalizeBraceSpaces(line);
            line = this.normalizeAnnotationSpaces(line);

            const currentLineIndent = indentation(indentColumns);
            const fullLine = currentLineIndent + line;

            outputLines.push(fullLine);

            const codeWithoutStrings = line.replace(/"([^"\\]|\\.)*"/g, '""').replace(/'([^'\\]|\\.)*'/g, "''");

            for (let chIdx = 0; chIdx < codeWithoutStrings.length; chIdx++) {
                const char = codeWithoutStrings[chIdx];
                if (char === '{') {
                    blockColumns += this.config.tabSize;
                } else if (char === '}') {
                    if (!startsWithClosingBrace) {
                        blockColumns = Math.max(0, blockColumns - this.config.tabSize);
                    }
                } else if (char === '(' || char === '[') {
                    const textAfterParen = codeWithoutStrings.slice(chIdx + 1).trim();
                    const hasCodeAfterParen = textAfterParen !== '' && !textAfterParen.startsWith('//');
                    const addedParenIndent = hasCodeAfterParen
                        ? (isNestedCall && parenColumnsStack.length > 0
                            ? parenColumnsStack[parenColumnsStack.length - 1] + continuationIndent
                            : (parenColumnsStack.length > 0 ? parenColumnsStack[parenColumnsStack.length - 1] : continuationIndent))
                        : (indentColumns + continuationIndent - blockColumns);
                    parenColumnsStack.push(addedParenIndent);
                } else if (char === ')' || char === ']') {

                    if (!startsWithClosingParen && parenColumnsStack.length > 0) {
                        parenColumnsStack.pop();
                    }
                }
            }

        }

        return outputLines;
    }


    private normalizeControlFlowSpaces(line: string): string {
        return line
            .replace(/\bif\s*\(/g, 'if (')
            .replace(/\bfor\s*\(/g, 'for (')
            .replace(/\bwhile\s*\(/g, 'while (')
            .replace(/\bswitch\s*\(/g, 'switch (')
            .replace(/\bcatch\s*\(/g, 'catch (')
            .replace(/\}\s*else\s*if\b/g, '} else if')
            .replace(/\}\s*else\b/g, '} else')
            .replace(/\}\s*catch\b/g, '} catch')
            .replace(/\}\s*finally\b/g, '} finally');
    }

    private normalizeAssignmentSpaces(line: string): string {
        const parts: string[] = [];
        const regex = /("([^"\\]|\\.)*"|'([^'\\]|\\.)*')/g;
        let lastIdx = 0;
        let match: RegExpExecArray | null;

        while ((match = regex.exec(line)) !== null) {
            const codeBefore = line.slice(lastIdx, match.index);
            parts.push(this.fixAssignmentOperatorSpaces(codeBefore));
            parts.push(match[0]);
            lastIdx = match.index + match[0].length;
        }

        const codeRest = line.slice(lastIdx);
        parts.push(this.fixAssignmentOperatorSpaces(codeRest));

        return parts.join('');
    }

    private fixAssignmentOperatorSpaces(code: string): string {
        let res = code.replace(/([^!\+\-\*\/\%\|\&\^\=\<\>\s])=(?!=)/g, '$1 =');
        res = res.replace(/(?<![!\+\-\*\/\%\|\&\^\=\<\>])=([^=\s])/g, '= $1');
        return res;
    }

    private normalizeParenSpaces(line: string): string {
        // Protect string literals while removing excess inner paren spaces
        const parts: string[] = [];
        const regex = /("([^"\\]|\\.)*"|'([^'\\]|\\.)*')/g;
        let lastIdx = 0;
        let match: RegExpExecArray | null;

        while ((match = regex.exec(line)) !== null) {
            const codeBefore = line.slice(lastIdx, match.index);
            parts.push(codeBefore.replace(/\(\s+/g, '(').replace(/\s+\)/g, ')'));
            parts.push(match[0]); // keep string literal unchanged
            lastIdx = match.index + match[0].length;
        }

        const codeRest = line.slice(lastIdx);
        parts.push(codeRest.replace(/\(\s+/g, '(').replace(/\s+\)/g, ')'));

        return parts.join('');
    }

    private normalizeAnnotationSpaces(line: string): string {
        return line.replace(/@([a-zA-Z0-9_]+)\s*\(\s*/g, '@$1(');
    }

    private normalizeOperatorSpaces(line: string): string {
        const parts: string[] = [];
        const regex = /("([^"\\]|\\.)*"|'([^'\\]|\\.)*')/g;
        let lastIdx = 0;
        let match: RegExpExecArray | null;

        while ((match = regex.exec(line)) !== null) {
            const codeBefore = line.slice(lastIdx, match.index);
            parts.push(this.fixOperatorSpaces(codeBefore));
            parts.push(match[0]);
            lastIdx = match.index + match[0].length;
        }

        const codeRest = line.slice(lastIdx);
        parts.push(this.fixOperatorSpaces(codeRest));

        return parts.join('');
    }

    private applyRuleTernaryOperators(lines: string[]): string[] {
        const output: string[] = [];
        let i = 0;
        let inTextBlock = false;

        while (i < lines.length) {
            const line = lines[i];
            if (inTextBlock) {
                output.push(line);
                if (line.includes('"""')) inTextBlock = false;
                i++;
                continue;
            }
            if (line.includes('"""')) {
                const count = (line.match(/"""/g) || []).length;
                if (count % 2 !== 0) inTextBlock = true;
            }

            const trimmed = line.trim();

            if (trimmed.startsWith('//') || trimmed.startsWith('/*') || trimmed.startsWith('*') || trimmed.startsWith('import ') || trimmed.startsWith('package ')) {
                output.push(line);
                i++;
                continue;
            }

            let statementText = trimmed;
            let nextIndex = i + 1;

            const hasTernaryIndicators = statementText.includes('?') || statementText.includes(':') || trimmed.endsWith('=') || trimmed.includes(' =');

            if (hasTernaryIndicators && !statementText.endsWith(';') && !statementText.endsWith('{')) {
                let accumulated = statementText;
                let lookaheadIdx = i + 1;
                let foundTernary = accumulated.includes('?') || accumulated.includes(':');

                while (lookaheadIdx < lines.length) {
                    const nextTrimmed = lines[lookaheadIdx].trim();
                    if (nextTrimmed.startsWith('//') || nextTrimmed.startsWith('/*') || nextTrimmed.startsWith('@') || nextTrimmed.startsWith('class ') || nextTrimmed.startsWith('public ')) {
                        break;
                    }
                    accumulated += ' ' + nextTrimmed;
                    if (nextTrimmed.includes('?') || nextTrimmed.includes(':')) {
                        foundTernary = true;
                    }
                    lookaheadIdx++;
                    if (nextTrimmed.endsWith(';') || nextTrimmed.endsWith('{')) {
                        break;
                    }
                }

                if (foundTernary && this.hasTernaryOperator(accumulated)) {
                    statementText = accumulated;
                    nextIndex = lookaheadIdx;
                }
            }

            if (this.hasTernaryOperator(statementText)) {
                const ternaryParts = this.splitTernaryExpression(statementText);
                if (ternaryParts) {
                    const { condition, trueExpr, falseExpr } = ternaryParts;
                    const normalizedCond = this.normalizeOperatorSpaces(condition).replace(/\s*\.\s*/g, '.');
                    const normalizedTrue = trueExpr.trim().replace(/\s*\.\s*/g, '.');
                    const normalizedFalse = falseExpr.trim().replace(/\s*\.\s*/g, '.');

                    const fullSingleLine = `${normalizedCond} ? ${normalizedTrue} : ${normalizedFalse}`;
                    if (fullSingleLine.length >= this.config.maxLineLength || statementText.includes('?') || statementText.includes(':')) {
                        output.push(normalizedCond);
                        output.push(`? ${normalizedTrue}`);
                        output.push(`: ${normalizedFalse}`);
                        i = nextIndex;
                        continue;
                    } else {
                        output.push(fullSingleLine);
                        i = nextIndex;
                        continue;
                    }
                }
            }

            output.push(line);
            i++;
        }

        return output;
    }

    private hasTernaryOperator(line: string): boolean {
        let inString = false;
        let stringChar = '';
        let hasQuestion = false;
        let hasColon = false;

        for (let i = 0; i < line.length; i++) {
            const char = line[i];
            if (inString) {
                if (char === stringChar && line[i - 1] !== '\\') {
                    inString = false;
                }
                continue;
            }
            if (char === '"' || char === "'") {
                inString = true;
                stringChar = char;
                continue;
            }
            if (char === '?') {
                hasQuestion = true;
            } else if (char === ':' && hasQuestion) {
                hasColon = true;
            }
        }
        return hasQuestion && hasColon;
    }

    private splitTernaryExpression(line: string): { condition: string; trueExpr: string; falseExpr: string } | null {
        let inString = false;
        let stringChar = '';
        let questionIdx = -1;
        let colonIdx = -1;
        let depth = 0;

        for (let i = 0; i < line.length; i++) {
            const char = line[i];
            if (inString) {
                if (char === stringChar && line[i - 1] !== '\\') {
                    inString = false;
                }
                continue;
            }
            if (char === '"' || char === "'") {
                inString = true;
                stringChar = char;
                continue;
            }
            if (char === '(' || char === '<' || char === '[') {
                depth++;
            } else if (char === ')' || char === '>' || char === ']') {
                depth = Math.max(0, depth - 1);
            } else if (depth === 0) {
                if (char === '?' && questionIdx === -1) {
                    questionIdx = i;
                } else if (char === ':' && questionIdx !== -1 && colonIdx === -1) {
                    colonIdx = i;
                }
            }
        }

        if (questionIdx !== -1 && colonIdx !== -1 && colonIdx > questionIdx) {
            const condition = line.slice(0, questionIdx).trim();
            const trueExpr = line.slice(questionIdx + 1, colonIdx).trim();
            const falseExpr = line.slice(colonIdx + 1).trim();
            return { condition, trueExpr, falseExpr };
        }

        return null;
    }

    private fixOperatorSpaces(code: string): string {
        return code
            .replace(/\s*!=\s*/g, ' != ')
            .replace(/(?<![!=><])\s*==\s*(?!=)/g, ' == ')
            .replace(/(?<![<])\s*<=\s*(?!=)/g, ' <= ')
            .replace(/(?<![>])\s*>=\s*(?!=)/g, ' >= ');
    }

    private normalizeCommaSpaces(line: string): string {
        return line.replace(/,([^\s\/\*])/g, ', $1');
    }

    private normalizeBraceSpaces(line: string): string {
        return line.replace(/([a-zA-Z0-9_\>\]\)])\{/g, '$1 {');
    }

    private postProcessBlankLines(lines: string[]): string[] {
        const result: string[] = [];
        let consecutiveEmpty = 0;

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const isEmpty = line.trim() === '';

            if (isEmpty) {
                let nextNonEmpty = '';
                for (let j = i + 1; j < lines.length; j++) {
                    const t = lines[j].trim();
                    if (t !== '') {
                        nextNonEmpty = t;
                        break;
                    }
                }
                if (nextNonEmpty.startsWith('?') || nextNonEmpty.startsWith(':') || nextNonEmpty.startsWith('.')) {
                    continue;
                }

                consecutiveEmpty++;
                if (consecutiveEmpty <= 1) {
                    result.push(line);
                }
            } else {
                consecutiveEmpty = 0;
                result.push(line);
            }
        }

        return result;
    }
}
