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
