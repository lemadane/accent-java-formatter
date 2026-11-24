# Accent Java Formatter (VS Code & IntelliJ IDEA)

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/34525.svg?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/34525)

> Enterprise-grade Java Code Formatter for **Visual Studio Code** and **IntelliJ IDEA** (Plugin ID: `34525`), implementing standardized A Java Code Style rules.

> [!IMPORTANT]
> **Release v0.1.0**: Fits assignment call headers within 70 columns including indentation; longer headers move `= call(` onto a continuation line with arguments indented one level further. Keeps generic bounds such as `<E extends Enum<E>>` together in both editors while preserving inheritance and `throws` clause wrapping. Uses Ube’s indentation scheme in both editors: four-space blocks and independent two-space continuations, alongside recursive call and chain formatting. Strings, character literals, text blocks, and comments are protected throughout formatting. Both implementations use the same regression fixtures for structural layout, token preservation, and repeat-format stability.

The **Accent Java Formatter** brings standardized Java code formatting, independent two-space continuation indentation, Eclipse JDT XML profile compatibility, intelligent import organization, and configurable code styles directly to Visual Studio Code and IntelliJ IDEA.

---

## 🔗 JetBrains Marketplace Links & Embed Widgets

- **JetBrains Marketplace Page**: [plugins.jetbrains.com/plugin/34525](https://plugins.jetbrains.com/plugin/34525)
- **Plugin ID**: `34525`

### Web Embed Snippets

#### 1. Marketplace Card Widget
```html
<script src="https://plugins.jetbrains.com/assets/scripts/mp-widget.js"></script>
<script>
  MarketplaceWidget.setupMarketplaceWidget('card', 34525, "#yourelement");
</script>
```

#### 2. Install Button Widget
```html
<script src="https://plugins.jetbrains.com/assets/scripts/mp-widget.js"></script>
<script>
  MarketplaceWidget.setupMarketplaceWidget('install', 34525, "#yourelement");
</script>
```

## 💻 Installation Instructions

### Visual Studio Code (.vsix) 🟦

#### Option 1: Via Terminal (Quickest) 🚀
```bash
code --install-extension /home/lem/Projects/accent-java-formatter/vscode/accent-java-formatter-0.1.0.vsix
```

#### Option 2: Via VS Code GUI 🖥️
1. Open **Visual Studio Code**.
2. Open the **Extensions** panel (`Ctrl+Shift+X` or `Cmd+Shift+X`).
3. Click the `...` menu in the top-right corner of the Extensions panel -> **Install from VSIX...**.
4. Select `accent-java-formatter-0.1.0.vsix`.

---

### IntelliJ IDEA Plugin (.zip) 🟧

#### Option 1: Via IntelliJ GUI 🖥️
1. Open **IntelliJ IDEA**.
2. Go to **Settings/Preferences** (`Ctrl+Alt+S` or `Cmd+,`) -> **Plugins**.
3. Click the gear icon next to the "Installed" tab -> **Install Plugin from Disk...**.
4. Choose `accent-java-formatter-0.1.0.zip` (located in `intellij/build/distributions/`).
5. Click **Apply** and restart IntelliJ IDEA.

---

## 🌟 Standard A Formatting Rules

| Rule | Description | Code Example |
| :--- | :--- | :--- |
| **Rule 1** | Any annotation sits on its own dedicated line (preserving method header prefixes). | `@PostMapping`<br>`@Validated`<br>`public ResponseEntity<ProductResponse> create(` |
| **Rule 4** | Method and constructor declarations with $>1$ argument (or single long parameter $\ge 70$ cols) formatted 1 per line. | `public int add(`<br>`  int a,`<br>`  int b) {` |
| **Rule 5** | `extends`, `implements`, `throws` sit on dedicated next lines together with associated types. | `public class CustomController`<br>`  extends BaseController`<br>`  implements ControllerInterface {` |
| **Rule 6** | Method calls with $>1$ argument formatted multiline (1 per line). | `service.pay(`<br>`  id,`<br>`  amount);` |
| **Rule 7** | Assignment RHS indented +2 spaces when on next line & spaces around `=` normalized. | `final var product =`<br>`  this.productService.create(request);` |
| **Rule 8** | Chained method calls ($\ge 2$ dots) split from 2nd dot, and lambda chained calls indented +2 spaces. | `return this.productService.getById(id)`<br>`  .map(ResponseEntity::ok)`<br>`  .orElseGet(`<br>`    () -> ResponseEntity`<br>`      .notFound()`<br>`      .build());` |
| **Rule 9** | 70-column line limit wraps assignment RHS, annotations, or method arguments onto next line. | `maxLineLength: 70` |
| **Rule 10** | Automatic formatting triggered on document save or `Ctrl+Alt+L` / `Cmd+Option+L`. | `onSave: true`, `formatOnChange: false` |

---

## ⚙️ Configuration Settings (VS Code)

Configure options in `.vscode/settings.json` or the VS Code Settings UI:

```json
{
  "accent.java.format.enabled": true,
  "accent.java.format.preset": "A Standard",
  "accent.java.format.tabSize": 4,
  "accent.java.format.continuationIndent": 2,
  "accent.java.format.insertSpaces": true,
  "accent.java.format.maxLineLength": 70,
  "accent.java.format.comments.enabled": true,
  "accent.java.format.imports.organizeOnFormat": true,
  "accent.java.format.formatOnChange": false,
  "accent.java.format.onSave": true,
  "accent.java.format.settings.url": ".vscode/accent-java-formatter.xml"
}
```

---

## 🧪 Verification

Run `npm test` in `vscode/` and `./gradlew test` in `intellij/` (Java 17).
The suites include 25 shared input/expected-output fixtures in `formatting-parity/ube/`.
Each fixture checks exact layout, idempotency, and token preservation using an independent
Java parser. The VS Code suite also covers selection formatting across comments and text blocks.

The new structural rules apply across physical lines: multiple arguments use separate lines,
nested calls receive continuation indentation, chained calls are split consistently, and simple
trailing calls such as `.limit(10)` stay compact. Closing parentheses follow the last argument,
except when a trailing line comment requires a separate closing line. Nonempty executable
blocks are expanded. Java annotations and generic argument lists remain intact.

Both editors default to four-space blocks and two-space continuation steps. Continuations
are measured in columns independently of block width, including when indentation uses tabs.
VS Code exposes `accent.java.format.continuationIndent` separately from `tabSize`;
explicit user block-width settings and other presets still take precedence over defaults.
The IntelliJ formatter configuration uses the same independent `continuationIndent` value.
Six additional shared fixtures in `formatting-parity/indentation/` cover these defaults;
separate tests cover custom block widths and mixed tabs/spaces.

> [!IMPORTANT]
> **Java Runtime Compatibility for IntelliJ/Gradle Tasks**
> - Use **Java 21** (recommended) or **Java 17** for IntelliJ plugin Gradle tasks (`test`, `buildPlugin`).
> - Running these Gradle tasks with **Java 25** may fail due to current toolchain compatibility.
> - This does **not** mean Java 25 project source code cannot be formatted. It only affects the plugin build/test runtime.

```bash
# Execute VS Code Test Suite
cd vscode && npm test

# Execute IntelliJ Plugin Test Suite
cd intellij
export JAVA_HOME=/path/to/jdk-21
./gradlew test
```

---

## 🛠️ Building & Packaging

### VS Code VSIX Package
```bash
cd vscode
npm run compile
npx vsce package --out accent-java-formatter-0.1.0.vsix
```

### IntelliJ Plugin ZIP Package
```bash
cd intellij
export JAVA_HOME=/path/to/jdk-21
./gradlew buildPlugin
```
Output artifact: `intellij/build/distributions/accent-java-formatter-0.1.0.zip`

---

## 📜 License

Copyright © 2026 A. All Rights Reserved.
