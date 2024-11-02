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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class InappropriateLanguageCheckerServer implements LanguageServer, LanguageClientAware {

    private static final List<String> INAPPROPRIATE_WORDS = Arrays.asList("badword1", "badword2");
    private static final Logger logger = Logger.getLogger("InappropriateLanguageCheckerLogger");
    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private LanguageClient client;
    private int exitCode = 1;

    public InappropriateLanguageCheckerServer() {
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

            for (String word : INAPPROPRIATE_WORDS) {
                int index = text.toLowerCase().indexOf(word.toLowerCase());
                while (index >= 0) {
                    Diagnostic diagnostic = new Diagnostic();
                    diagnostic.setRange(new Range(
                            new Position(0, index),
                            new Position(0, index + word.length())
                    ));
                    diagnostic.setSeverity(DiagnosticSeverity.Warning);
                    diagnostic.setSource("inappropriate-language-checker");
                    diagnostic.setMessage("Inappropriate language detected: " + word);
                    diagnostics.add(diagnostic);

                    index = text.toLowerCase().indexOf(word.toLowerCase(), index + 1);
                }
            }

            if (client != null) {
                logger.info("Bad Word Found ");
                client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
            }
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
        InappropriateLanguageCheckerServer server = new InappropriateLanguageCheckerServer();

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