package LogViewer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.example.InappropriateLanguageCheckerServer;

public class LSPLogViewer extends JFrame {
    private JTextArea logArea;
    private JScrollPane scrollPane;
    private JButton clearButton;
    private JButton startServerButton;
    private JButton stopServerButton;
    private JPanel buttonPanel;
    private Process serverProcess;
    private final String LOG_FILE = "log.txt";
    private long lastFilePosition = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public LSPLogViewer() {
        setupUI();
        setupLogMonitoring();
    }

    private void setupUI() {
        setTitle("LSP Server Log Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Create components
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        // Create buttons
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startServerButton = new JButton("Start Server");
        stopServerButton = new JButton("Stop Server");
        clearButton = new JButton("Clear Logs");

        stopServerButton.setEnabled(false);

        buttonPanel.add(startServerButton);
        buttonPanel.add(stopServerButton);
        buttonPanel.add(clearButton);

        // Add components to frame
        setLayout(new BorderLayout());
        add(buttonPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Add button listeners
        startServerButton.addActionListener(e -> startServer());
        stopServerButton.addActionListener(e -> stopServer());
        clearButton.addActionListener(e -> clearLogs());
    }

    private void setupLogMonitoring() {
        scheduler.scheduleAtFixedRate(this::checkForNewLogs, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void checkForNewLogs() {
        File logFile = new File(LOG_FILE);
        if (!logFile.exists()) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long fileLength = logFile.length();
            if (fileLength < lastFilePosition) {
                // File was truncated or recreated
                lastFilePosition = 0;
            }

            if (fileLength > lastFilePosition) {
                raf.seek(lastFilePosition);
                String line;
                StringBuilder newContent = new StringBuilder();

                while ((line = raf.readLine()) != null) {
                    newContent.append(line).append("\n");
                }

                final String newText = newContent.toString();
                SwingUtilities.invokeLater(() -> {
                    logArea.append(newText);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });

                lastFilePosition = raf.getFilePointer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startServer() {
        try {
            // Assuming the server class is in the same directory
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            String className = InappropriateLanguageCheckerServer.class.getCanonicalName();

            ProcessBuilder builder = new ProcessBuilder(
                    javaBin, "-cp", classpath, className
            );

            serverProcess = builder.start();
            startServerButton.setEnabled(false);
            stopServerButton.setEnabled(true);

            // Read server's error stream in a separate thread
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String errorLine = line;
                        SwingUtilities.invokeLater(() ->
                                logArea.append("ERROR: " + errorLine + "\n"));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to start server: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            serverProcess = null;
            startServerButton.setEnabled(true);
            stopServerButton.setEnabled(false);
        }
    }

    private void clearLogs() {
        logArea.setText("");
        try {
            new FileWriter(LOG_FILE, false).close();
            lastFilePosition = 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        stopServer();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LSPLogViewer viewer = new LSPLogViewer();
            viewer.setVisible(true);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(viewer::shutdown));
        });
    }
}