import * as assert from 'assert';
import * as fs from 'fs';
import * as path from 'path';
import { JavaFormatter } from '../../formatter/javaFormatter';
import { ACCENT_DEFAULT_CONFIG, getFormatterConfig } from '../../formatter/config';

describe('Ube indentation defaults', () => {
    const root = path.resolve(__dirname, '../../../../formatting-parity/indentation');
    it('uses four-space blocks by default in both config entry points', () => {
        assert.strictEqual(ACCENT_DEFAULT_CONFIG.tabSize, 4);
        assert.strictEqual(getFormatterConfig().tabSize, 4);
    });
    for (const file of fs.readdirSync(root).filter(f => f.endsWith('.input.java'))) {
        it(file, () => {
            const source = fs.readFileSync(path.join(root, file), 'utf8');
            const expected = fs.readFileSync(path.join(root, file.replace('.input.', '.expected.')), 'utf8');
            const formatter = new JavaFormatter(ACCENT_DEFAULT_CONFIG);
            const actual = formatter.formatDocument(source);
            assert.strictEqual(actual, expected);
            assert.strictEqual(formatter.formatDocument(actual), actual);
        });
    }
});

describe('Independent indentation widths', () => {
    for (const continuationIndent of [0, 2, 6]) {
        for (const insertSpaces of [true, false]) {
            it(`indents return concatenations by ${continuationIndent} columns with insertSpaces=${insertSpaces}`, () => {
                const formatter = new JavaFormatter({ ...ACCENT_DEFAULT_CONFIG, continuationIndent, insertSpaces });
                const source = 'class Sample {\nString url() {\nreturn normalizedBaseUrl\n+ "/transform/"\n+ projectName.trim();\n}\n}\n';
                const indent = (columns: number) => insertSpaces
                    ? ' '.repeat(columns)
                    : '\t'.repeat(Math.floor(columns / 4)) + ' '.repeat(columns % 4);
                const expected = `class Sample {\n${indent(4)}String url() {\n${indent(8)}return normalizedBaseUrl\n${indent(8 + continuationIndent)}+ "/transform/"\n${indent(8 + continuationIndent)}+ projectName.trim();\n${indent(4)}}\n}\n`;
                assert.strictEqual(formatter.formatDocument(source), expected);
                assert.strictEqual(formatter.formatDocument(expected), expected);
            });
        }
    }
    const source = 'class Sample {\nvoid run() {\nprint(convert(resolve(value)));\n}\n}\n';
    it('keeps two-space continuations with two-space blocks', () => {
        const formatter = new JavaFormatter({ ...ACCENT_DEFAULT_CONFIG, tabSize: 2 });
        const expected = 'class Sample {\n  void run() {\n    print(\n      convert(\n        resolve(value)));\n  }\n}\n';
        assert.strictEqual(formatter.formatDocument(source), expected);
        assert.strictEqual(formatter.formatDocument(expected), expected);
    });
    it('represents two continuation columns correctly when using tabs', () => {
        const formatter = new JavaFormatter({ ...ACCENT_DEFAULT_CONFIG, insertSpaces: false });
        const expected = 'class Sample {\n\tvoid run() {\n\t\tprint(\n\t\t  convert(\n\t\t\tresolve(value)));\n\t}\n}\n';
        assert.strictEqual(formatter.formatDocument(source), expected);
        assert.strictEqual(formatter.formatDocument(expected), expected);
    });
});
