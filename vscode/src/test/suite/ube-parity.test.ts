import * as assert from 'assert';
import * as fs from 'fs';
import * as path from 'path';
import { JavaFormatter } from '../../formatter/javaFormatter';
import { ACCENT_DEFAULT_CONFIG } from '../../formatter/config';

// The same hand-written fixtures are consumed by the IntelliJ JUnit suite.
describe('Ube structural formatting parity', () => {
    const root = path.resolve(__dirname, '../../../../formatting-parity/ube');
    for (const file of fs.readdirSync(root).filter(f => f.endsWith('.input.java'))) {
        it(file.replace('.input.java', ''), async () => {
            const source = fs.readFileSync(path.join(root, file), 'utf8');
            const expected = fs.readFileSync(path.join(root, file.replace('.input.', '.expected.')), 'utf8');
            const formatter = new JavaFormatter({ ...ACCENT_DEFAULT_CONFIG, tabSize: 2, continuationIndent: 4, maxLineLength: 80, organizeImportsOnFormat: false });
            const actual = formatter.formatDocument(source);
            assert.strictEqual(actual, expected);
            assert.strictEqual(formatter.formatDocument(actual), actual, 'formatting must be idempotent');
            // Independent Java lexer/parser: reject invalid output and changed literal/comment tokens.
            const { lexAndParse } = await new Function('return import("java-parser")')();
            const before = lexAndParse(source);
            const after = lexAndParse(actual);
            assert.deepStrictEqual(after.tokens.map((t: any) => t.image), before.tokens.map((t: any) => t.image));
        });
    }
});

describe('Safe range formatting', () => {
    const formatter = new JavaFormatter({ ...ACCENT_DEFAULT_CONFIG, tabSize: 2, continuationIndent: 4, organizeImportsOnFormat: false });
    it('protects literals and computes indentation past trailing comments', () => {
        const source = 'class X {\nvoid run() { // {\nprint("a,b");\n}\n}\n';
        assert.strictEqual(formatter.formatRange(source, 2, 2), 'class X {\nvoid run() { // {\n    print("a,b");\n}\n}\n');
    });
    it('leaves a selection inside a text block untouched', () => {
        const source = 'class X {\nString s = """\na,b if(x)\n""";\n}';
        assert.strictEqual(formatter.formatRange(source, 2, 2), source);
    });
});
