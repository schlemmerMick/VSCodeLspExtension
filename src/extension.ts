import * as vscode from 'vscode';
import * as path from 'path';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
    console.log('Activating LSP extension...');

    const serverJar = context.asAbsolutePath(path.join('server', 'target', 'LSP-1.jar'));
    console.log('Server JAR path:', serverJar);

    // Server options
    const serverOptions: ServerOptions = {
        run: {
            command: 'java',
            args: ['-jar', serverJar],
            transport: TransportKind.stdio,
            options: {
                env: {
                    ...process.env,
                    // Add JAVA_HOME if needed
                    JAVA_HOME: process.env.JAVA_HOME || ''
                }
            }
        },
        debug: {
            command: 'java',
            args: ['-jar', serverJar, '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005'],
            transport: TransportKind.stdio,
            options: {
                env: {
                    ...process.env,
                    JAVA_HOME: process.env.JAVA_HOME || ''
                }
            }
        }
    };

    // Client options
    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'plaintext' }],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.txt')
        }
    };

    // Create and start client
    client = new LanguageClient(
        'InappropriateLanguageChecker',
        'Inappropriate Language Checker',
        serverOptions,
        clientOptions
    );

    // Start the client
    client.start().catch(err => {
        console.error('Failed to start language client:', err);
        vscode.window.showErrorMessage('Failed to start language server');
    });
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}