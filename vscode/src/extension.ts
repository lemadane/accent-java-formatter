import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { JavaFormattingEditProvider } from './providers/javaFormattingProvider';
import { JavaCodeActionProvider } from './providers/codeActionProvider';
import { getFormatterConfig, generateAccentXmlConfig, PRESET_CONFIGS } from './formatter/config';
import { JavaFormatter } from './formatter/javaFormatter';
import { organizeJavaImports } from './formatter/organizeImports';

let statusBarItem: vscode.StatusBarItem;

export function activate(context: vscode.ExtensionContext) {
    console.log(
      '[Accent Java Formatter] Extension activated successfully.');
    const JAVA_SELECTOR: vscode.DocumentSelector = [
    {
        scheme: 'file',
        language: 'java'
    },
    {
        scheme: 'untitled',
        language: 'java'
    }
    ];
    const formattingProvider = new JavaFormattingEditProvider();
    // 1. Register Document Formatting Edit Provider
    context.subscriptions.push(
      vscode.languages.registerDocumentFormattingEditProvider(
        JAVA_SELECTOR,
        formattingProvider));
    // 2. Register Range Formatting Edit Provider
    context.subscriptions.push(
      vscode.languages.registerDocumentRangeFormattingEditProvider(
        JAVA_SELECTOR,
        formattingProvider));
    // 3. Register Code Actions Provider
    context.subscriptions.push(
      vscode.languages.registerCodeActionsProvider(
        JAVA_SELECTOR,
        new JavaCodeActionProvider(),
        {
            providedCodeActionKinds: JavaCodeActionProvider.providedCodeActionKinds
        }));
    // 4. Create Status Bar Item
    statusBarItem = vscode.window.createStatusBarItem(
      vscode.StatusBarAlignment.Right,
      100);
    statusBarItem.command = 'accent.java.format.selectProfile';
    context.subscriptions.push(statusBarItem);
    updateStatusBarItem(vscode.window.activeTextEditor);
    context.subscriptions.push(
      vscode.window.onDidChangeActiveTextEditor(
          editor => updateStatusBarItem(editor)));
    // 5. Register Commands
    context.subscriptions.push(
      vscode.commands.registerCommand(
        'accent.java.format.document',
        async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor
              || editor.document.languageId !== 'java') {
                vscode.window.showWarningMessage(
                  'Accent Java Formatter: Please open a Java file to format.');
                return;
            }
            const edits =
              await formattingProvider.provideDocumentFormattingEdits(
                editor.document,
                {
                    tabSize: editor.options.tabSize as number,
                    insertSpaces: editor.options.insertSpaces as boolean
                },
                new vscode.CancellationTokenSource().token);
            if (edits
              && edits.length > 0) {
                const workspaceEdit = new vscode.WorkspaceEdit();
                workspaceEdit.set(
                  editor.document.uri,
                  edits);
                await vscode.workspace.applyEdit(workspaceEdit);
                vscode.window.showInformationMessage(
                  'Accent Java Formatter: Document formatted cleanly.');
            }
        }));
    context.subscriptions.push(
      vscode.commands.registerCommand(
        'accent.java.format.selection',
        async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor
              || editor.document.languageId !== 'java'
              || editor.selection.isEmpty) {
                vscode.window.showWarningMessage(
                  'Accent Java Formatter: Please select Java code to format.');
                return;
            }
            const edits =
              await formattingProvider.provideDocumentRangeFormattingEdits(
                editor.document,
                editor.selection,
                {
                    tabSize: editor.options.tabSize as number,
                    insertSpaces: editor.options.insertSpaces as boolean
                },
                new vscode.CancellationTokenSource().token);
            if (edits
              && edits.length > 0) {
                const workspaceEdit = new vscode.WorkspaceEdit();
                workspaceEdit.set(
                  editor.document.uri,
                  edits);
                await vscode.workspace.applyEdit(workspaceEdit);
            }
        }));
    context.subscriptions.push(
      vscode.commands.registerCommand(
        'accent.java.format.organizeImports',
        async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor
              || editor.document.languageId !== 'java') {
                vscode.window.showWarningMessage(
                  'Accent Java Formatter: Please open a Java file to organize imports.');
                return;
            }
            const text = editor.document.getText();
            const lines = text.split(/\r?\n/);
            const result = organizeJavaImports(lines);
            if (result.hasImports
              && result.startLineIndex !== -1
              && result.endLineIndex !== -1) {
                const range = new vscode.Range(
                  new vscode.Position(
                    result.startLineIndex,
                    0),
                  new vscode.Position(
                    result.endLineIndex,
                    lines[result.endLineIndex].length));
                const workspaceEdit = new vscode.WorkspaceEdit();
                workspaceEdit.replace(
                  editor.document.uri,
                  range,
                  result.importsText);
                await vscode.workspace.applyEdit(workspaceEdit);
                vscode.window.showInformationMessage(
                  'Accent Java Formatter: Java imports organized.');
            }
        }));
    context.subscriptions.push(
      vscode.commands.registerCommand(
        'accent.java.format.selectProfile',
        async () => {
            const profiles = Object.keys(PRESET_CONFIGS);
            const selected = await vscode.window.showQuickPick(
              profiles,
              {
                  placeHolder: 'Select Accent Java Formatter Profile'
              });
            if (selected) {
                const config = vscode.workspace.getConfiguration(
                  'accent.java.format');
                await config.update(
                  'preset',
                  selected,
                  vscode.ConfigurationTarget.Global);
                vscode.window.showInformationMessage(
                  `Accent Java Formatter: Switched style profile to "${selected}".`);
                updateStatusBarItem(vscode.window.activeTextEditor);
            }
        }));
    context.subscriptions.push(
      vscode.commands.registerCommand(
        'accent.java.format.exportConfig',
        async () => {
            const workspaceFolders =
              vscode.workspace.workspaceFolders;
            if (!workspaceFolders
              || workspaceFolders.length === 0) {
                vscode.window.showErrorMessage(
                  'Accent Java Formatter: Open a workspace folder first to export configuration XML.');
                return;
            }
            const vscodeDir = path.join(
              workspaceFolders[0].uri.fsPath,
              '.vscode');
            if (!fs.existsSync(vscodeDir)) {
                fs.mkdirSync(
                  vscodeDir,
                  {
                      recursive: true
                  });
            }
            const targetPath = path.join(
              vscodeDir,
              'accent-java-formatter.xml');
            const xmlContent = generateAccentXmlConfig();
            fs.writeFileSync(
              targetPath,
              xmlContent,
              'utf8');
            const doc = await vscode.workspace.openTextDocument(
              targetPath);
            await vscode.window.showTextDocument(doc);
            vscode.window.showInformationMessage(
              `Accent Java Formatter XML profile created at ${targetPath}`);
        }));
    context.subscriptions.push(
      vscode.commands.registerCommand(
        'accent.java.format.setAsDefault',
        async () => {
            const javaConfig = vscode.workspace.getConfiguration(
              '[java]');
            await javaConfig.update(
              'editor.defaultFormatter',
              'accent-java-formatter',
              vscode.ConfigurationTarget.Global);
            vscode.window.showInformationMessage(
              'Accent Java Formatter is now set as the default Java formatter in VS Code.');
        }));
    // 6. Format on Save Handler
    context.subscriptions.push(
      vscode.workspace.onWillSaveTextDocument(
          event => {
            if (event.document.languageId !== 'java') return;
            const config = getFormatterConfig(event.document);
            if (config.onSave
              || vscode.workspace.getConfiguration(
                'accent.java.format',
                event.document.uri)
                .get(
                  'onSave',
                  false)) {
                const formatter = new JavaFormatter(config);
                const original = event.document.getText();
                const formatted = formatter.formatDocument(original);
                if (original !== formatted) {
                    const range = new vscode.Range(
                      event.document.positionAt(0),
                      event.document.positionAt(original.length));
                    event.waitUntil(
                      Promise.resolve(
                        [vscode.TextEdit.replace(
                          range,
                          formatted)]));
                }
            }
        }));
    // 7. Format on Code Change Handler (Debounced 500ms)
    let changeDebounceTimer: NodeJS.Timeout | undefined;
    let isSelfFormatting = false;
    context.subscriptions.push(
      vscode.workspace.onDidChangeTextDocument(
        async event => {
            if (isSelfFormatting
              || event.document.languageId !== 'java') return;
            if (event.contentChanges.length === 0) return;
            const config = getFormatterConfig(event.document);
            if (!config.formatOnChange) return;
            if (changeDebounceTimer) {
                clearTimeout(changeDebounceTimer);
            }
            changeDebounceTimer = setTimeout(
              async () => {
                  const editor = vscode.window.activeTextEditor;
                  if (!editor
                    || editor.document !== event.document) return;
                  const formatter = new JavaFormatter(config);
                  const original = event.document.getText();
                  const formatted =
                    formatter.formatDocument(original);
                  if (original !== formatted) {
                      isSelfFormatting = true;
                      try {
                          const fullRange = new vscode.Range(
                            event.document.positionAt(0),
                            event.document.positionAt(
                              original.length));
                          const workspaceEdit =
                            new vscode.WorkspaceEdit();
                          workspaceEdit.replace(
                            event.document.uri,
                            fullRange,
                            formatted);
                          await vscode.workspace.applyEdit(
                            workspaceEdit);
                      } finally {
                          isSelfFormatting = false;
                      }
                  }
              },
              500);
        }));
}

function updateStatusBarItem(
  editor: vscode.TextEditor | undefined): void {
    if (editor
      && editor.document.languageId === 'java') {
        const config = getFormatterConfig(editor.document);
        statusBarItem.text = `$(code) A Java: ${config.preset}`;
        statusBarItem.tooltip =
          'Click to switch Accent Java Formatter style profile';
        statusBarItem.show();
    } else {
        statusBarItem.hide();
    }
}

export function deactivate() { }
