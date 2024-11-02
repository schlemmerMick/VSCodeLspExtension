package org.example;

import LogViewer.LSPLogViewer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.launch.LSPLauncher;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LspServer implements LanguageServer, LanguageClientAware {

    private static final List<String> FAULTY_WORDS = Arrays.asList("badword1", "badword2");
    private static final Logger logger = Logger.getLogger("FalultyLanguageCheckerLogger");
    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private LanguageClient client;
    private int exitCode = 1;

    public LspServer() {
        this.textDocumentService = new TextDocumentServiceImpl();
        this.workspaceService = new WorkspaceServiceImpl();
        setupLogger();
    }

    private void setupLogger() {
        try {
            FileHandler fh = new FileHandler("log.txt", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            logger.info(e.toString());
        }
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        logger.info("Server initializing...");

        // Define server capabilities
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        InitializeResult result = new InitializeResult(capabilities);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        logger.info("Server shutting down...");
        exitCode = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        logger.info("Server exiting...");
        System.exit(exitCode);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        ((TextDocumentServiceImpl) this.textDocumentService).connect(client);
        logger.info("Client connected to server");
    }

    private class TextDocumentServiceImpl implements TextDocumentService {
        private LanguageClient client;

        public void connect(LanguageClient client) {
            this.client = client;
        }

        @Override
        public void didOpen(DidOpenTextDocumentParams params) {
            logger.info("Document opened: " + params.getTextDocument().getUri());
            checkDocument(params.getTextDocument().getUri(), params.getTextDocument().getText());
        }

        @Override
        public void didChange(DidChangeTextDocumentParams params) {
            logger.info("Document changed: " + params.getTextDocument().getUri());
            String text = params.getContentChanges().get(0).getText();
            checkDocument(params.getTextDocument().getUri(), text);
        }

        @Override
        public void didClose(DidCloseTextDocumentParams params) {
            logger.info("Document closed: " + params.getTextDocument().getUri());
        }

        @Override
        public void didSave(DidSaveTextDocumentParams params) {
            logger.info("Document saved: " + params.getTextDocument().getUri());
        }

        private void checkDocument(String uri, String text) {
            List<Diagnostic> diagnostics = new ArrayList<>();
            logger.info("Text changed: " + text);
            String[] lines = text.split("\\R", -1);

            for (int lineNum = 0; lineNum < lines.length; lineNum++) {
                String line = lines[lineNum];
                checkLineForFaultyWords(uri, line, lineNum, diagnostics);
            }

            if (client != null && !diagnostics.isEmpty()) {
                client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
            } else {
                // Clear diagnostics when no issues are found
                client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>()));
            }
        }

        private void checkLineForFaultyWords(String uri, String line, int lineNum, List<Diagnostic> diagnostics) {
            // Convert to lowercase once per line for efficiency
            String lowerLine = line.toLowerCase();

            for (String word : FAULTY_WORDS) {
                String lowerWord = word.toLowerCase();
                int index = 0;

                while ((index = findWordWithBoundary(lowerLine, lowerWord, index)) >= 0) {
                    Diagnostic diagnostic = new Diagnostic();
                    diagnostic.setRange(new Range(
                            new Position(lineNum, index),
                            new Position(lineNum, index + word.length())
                    ));
                    diagnostic.setSeverity(DiagnosticSeverity.Warning);
                    diagnostic.setSource("faulty-word-checker");
                    diagnostic.setMessage("faulty word detected: '" + word + "'");
                    diagnostic.setCode("faulty-word");
                    logger.info("faulty-word: " + word);
                    diagnostics.add(diagnostic);

                    index += word.length();
                }
            }
        }

        private int findWordWithBoundary(String text, String word, int startIndex) {
            int index = text.indexOf(word, startIndex);
            while (index >= 0) {
                // Check word boundaries
                boolean validPrefix = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
                boolean validSuffix = index + word.length() >= text.length() ||
                        !Character.isLetterOrDigit(text.charAt(index + word.length()));

                if (validPrefix && validSuffix) {
                    return index;
                }
                index = text.indexOf(word, index + 1);
            }
            return -1;
        }
    }

    private class WorkspaceServiceImpl implements WorkspaceService {
        @Override
        public void didChangeConfiguration(DidChangeConfigurationParams params) {
            // Not implemented
        }

        @Override
        public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
            // Not implemented
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LSPLogViewer viewer = new LSPLogViewer();
            viewer.setVisible(true);
        });

       logger.info("Starting Language Server...");
        LspServer server = new LspServer();

        // Create and start the language server
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server,
                System.in,
                System.out
        );

        // Get the client proxy
        LanguageClient client = launcher.getRemoteProxy();

        // Connect the server to the client
        server.connect(client);

        // Start listening
        Future<?> startListening = launcher.startListening();

        logger.info("Server is running...");

        try {
            startListening.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.severe("Error occurred while running the server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    }