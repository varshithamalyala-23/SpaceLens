import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

public class SpaceLens extends JFrame {

    private JTabbedPane tabs;
    private JTable driveTable, largestTable, duplicateTable, cleanupTable;
    private DefaultTableModel driveModel, largestModel, duplicateModel, cleanupModel;

    private JLabel statusLabel;
    private JLabel savingsLabel, groupsLabel, duplicateCountLabel;
    private JLabel largestCountLabel, largestSizeLabel, folderLabel;
    private JTextArea insightsArea;

    private JButton refreshButton, largestButton, duplicateButton, cancelButton;
    private JButton scanTempButton, deleteSelectedButton;
    private File tempRoot;

    private ScanWorker currentWorker;

    private List<FileInfo> largestFiles = new ArrayList<FileInfo>();
    private List<DuplicateGroup> duplicateGroups =
            new ArrayList<DuplicateGroup>();

    private long largestTotalBytes = 0;
    private long potentialSavings = 0;
    private int filesScanned = 0;
    private int duplicateGroupCount = 0;
    private int duplicateFileCount = 0;
    private String selectedFolder = "Not scanned";

    public SpaceLens() {
        setTitle("SpaceLens - Visual Storage Intelligence System");
        setSize(1100, 700);
        setMinimumSize(new Dimension(850, 550));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createInterface();
        refreshDrives();
        updateInsights();
    }

    private void createInterface() {
        setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel(
                "  SpaceLens - Visual Storage Intelligence System");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
        add(title, BorderLayout.NORTH);

        tabs = new JTabbedPane();

        driveModel = new DefaultTableModel(
                new String[]{"Drive", "Total Space", "Free Space",
                        "Used Space", "Usable Space"}, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        largestModel = new DefaultTableModel(
                new String[]{"File Name", "Size (Bytes)", "Full Path"}, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        duplicateModel = new DefaultTableModel(
                new String[]{"Group", "File Name", "Size (Bytes)",
                        "Full Path"}, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        driveTable = createTable(driveModel);
        largestTable = createTable(largestModel);
        duplicateTable = createTable(duplicateModel);

        tabs.addTab("Drive Overview", createDrivePanel());
        tabs.addTab("Largest Files", createLargestPanel());
        tabs.addTab("Duplicate Files", createDuplicatePanel());
        tabs.addTab("Storage Insights", createInsightsPanel());
        tabs.addTab("Smart Cleanup", createCleanupPanel());

        add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 5));
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        statusLabel = new JLabel("Ready.");
        cancelButton = new JButton("Cancel Scan");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelScan());

        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(cancelButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        return table;
    }

    private JPanel createDrivePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        refreshButton = new JButton("Refresh Drives");
        refreshButton.addActionListener(e -> refreshDrives());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refreshButton);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(driveTable), BorderLayout.CENTER);
        panel.add(new JLabel(
                "Storage information reported by the operating system."),
                BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLargestPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        largestButton = new JButton("Find 10 Largest Files");
        largestButton.addActionListener(e -> findLargestFiles());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(largestButton);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(largestTable), BorderLayout.CENTER);
        panel.add(new JLabel(
                "Large files are not automatically unnecessary."),
                BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createDuplicatePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        duplicateButton = new JButton("Choose Folder & Find Duplicates");
        duplicateButton.addActionListener(e -> chooseFolder());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(duplicateButton);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(duplicateTable), BorderLayout.CENTER);
        panel.add(new JLabel(
                "Files are compared by size and SHA-256 content hash."),
                BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createCleanupPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel note = new JLabel(
                "Older temporary files with explanations. Review before deleting.");

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(note, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));

        scanTempButton = new JButton("Scan Temporary Files");
        scanTempButton.addActionListener(e -> scanTemporaryFiles());

        deleteSelectedButton = new JButton("Delete Selected Files");
        deleteSelectedButton.addActionListener(
                e -> deleteSelectedTemporaryFiles());

        buttons.add(scanTempButton);
        buttons.add(deleteSelectedButton);
        top.add(buttons, BorderLayout.SOUTH);

        cleanupModel = new DefaultTableModel(
                new String[]{"Select", "File Name", "File Type", "What It Is",
                        "Recommendation", "Size", "Last Modified", "Full Path"}, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }
        };

        cleanupTable = createTable(cleanupModel);
        cleanupTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        cleanupTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        cleanupTable.getColumnModel().getColumn(2).setPreferredWidth(95);
        cleanupTable.getColumnModel().getColumn(3).setPreferredWidth(260);
        cleanupTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        cleanupTable.getColumnModel().getColumn(7).setPreferredWidth(360);

        JPanel bottom = new JPanel(new BorderLayout());

        bottom.add(new JLabel(
                "Only files under the temporary folder are listed. "
                + "Only files marked Review for cleanup can be deleted."),
                BorderLayout.NORTH);

        bottom.add(new JLabel(
                "Files marked Keep, Check first, or Protected cannot be deleted here."),
                BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(cleanupTable), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private void scanTemporaryFiles() {
        if (isScanning()) {
            return;
        }

        String tempPath = System.getProperty("java.io.tmpdir");

        if (tempPath == null || tempPath.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                    this, "The temporary folder could not be located.");
            return;
        }

        tempRoot = new File(tempPath).getAbsoluteFile();
        cleanupModel.setRowCount(0);

        final long cutoff = System.currentTimeMillis()
                - 7L * 24 * 60 * 60 * 1000;

        startScan("Scanning temporary files older than 7 days...", worker -> {

            List<CleanupFile> candidates = new ArrayList<CleanupFile>();

            Files.walkFileTree(tempRoot.toPath(),
                    new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(
                        Path path, BasicFileAttributes attrs) {

                    if (worker.scanCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    if (attrs.isRegularFile()
                            && attrs.lastModifiedTime().toMillis() < cutoff) {

                        candidates.add(new CleanupFile(
                                path.toFile(),
                                attrs.size(),
                                attrs.lastModifiedTime().toMillis()));
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(
                        Path path, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path path, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!worker.scanCancelled()) {
                SwingUtilities.invokeLater(() -> {
                    cleanupModel.setRowCount(0);

                    for (CleanupFile candidate : candidates) {
                        FileExplanation explanation =
                                explainFile(candidate.file);

                        cleanupModel.addRow(new Object[]{
                                Boolean.FALSE,
                                candidate.file.getName(),
                                explanation.fileType,
                                explanation.description,
                                explanation.recommendation,
                                formatBytes(candidate.size),
                                new java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm").format(
                                        new Date(candidate.lastModified)),
                                candidate.file.getAbsolutePath()
                        });
                    }

                    statusLabel.setText(
                            "Temporary scan complete: "
                            + candidates.size()
                            + " candidate file(s). Review before deleting.");
                });
            }

        }, () -> {
            tabs.setSelectedIndex(4);
            statusLabel.setText(
                    "Temporary-file scan completed. Review the recommendations.");
        });
    }

    private void deleteSelectedTemporaryFiles() {
        if (isScanning()) {
            return;
        }

        if (tempRoot == null || cleanupModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(
                    this, "Scan temporary files first.");
            return;
        }

        List<File> selected = new ArrayList<File>();
        long selectedBytes = 0;

        for (int row = 0; row < cleanupModel.getRowCount(); row++) {
            if (Boolean.TRUE.equals(cleanupModel.getValueAt(row, 0))) {

                String recommendation =
                        String.valueOf(cleanupModel.getValueAt(row, 4));

                if (!"Review for cleanup".equals(recommendation)) {
                    continue;
                }

                File file = new File(
                        String.valueOf(cleanupModel.getValueAt(row, 7)));

                selected.add(file);
                selectedBytes += Math.max(0, file.length());
            }
        }

        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select files marked 'Review for cleanup'. "
                    + "Other categories cannot be deleted here.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "Delete " + selected.size() + " selected temporary file(s)?\n"
                + "Estimated space: " + formatBytes(selectedBytes)
                + "\n\nThis cannot be undone. Continue?",
                "Confirm Cleanup",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        int deleted = 0;
        long reclaimed = 0;
        int failed = 0;

        Path rootPath = tempRoot.toPath()
                .toAbsolutePath().normalize();

        for (File file : selected) {
            try {
                Path candidate = file.toPath()
                        .toAbsolutePath().normalize();

                if (!candidate.startsWith(rootPath)
                        || !Files.isRegularFile(candidate)) {
                    failed++;
                    continue;
                }

                long size = Files.size(candidate);
                Files.delete(candidate);

                deleted++;
                reclaimed += size;

            } catch (IOException | SecurityException ex) {
                failed++;
            }
        }

        for (int row = cleanupModel.getRowCount() - 1; row >= 0; row--) {
            String path = String.valueOf(cleanupModel.getValueAt(row, 7));

            if (!new File(path).exists()) {
                cleanupModel.removeRow(row);
            } else {
                cleanupModel.setValueAt(Boolean.FALSE, row, 0);
            }
        }

        JOptionPane.showMessageDialog(
                this,
                "Cleanup finished.\nDeleted: " + deleted
                + " file(s)\nSpace reclaimed: " + formatBytes(reclaimed)
                + "\nFailed/skipped: " + failed,
                "Cleanup Results",
                JOptionPane.INFORMATION_MESSAGE);

        statusLabel.setText(
                "Cleanup complete. Reclaimed " + formatBytes(reclaimed) + ".");
    }

    private FileExplanation explainFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        String path = file.getAbsolutePath().toLowerCase(Locale.US);

        String extension = "No extension";
        int dot = name.lastIndexOf('.');

        if (dot > 0 && dot < name.length() - 1) {
            extension = name.substring(dot);
        }

        if (name.equals("pagefile.sys")
                || name.equals("hiberfil.sys")
                || name.equals("swapfile.sys")
                || path.contains("\\windows\\system32")) {

            return new FileExplanation(
                    extension,
                    "Windows system file or system-folder content. "
                    + "Windows may need it to operate.",
                    "Protected");
        }

        if (isPersonalOrProjectFile(extension)) {
            return new FileExplanation(
                    extension,
                    "May contain personal, college, or project work. "
                    + "Open it and confirm before removing.",
                    "Keep");
        }

        if (extension.equals(".tmp")
                || extension.equals(".temp")
                || extension.equals(".cache")) {

            return new FileExplanation(
                    extension,
                    "Often used for temporary data or cache. "
                    + "An application may still need it.",
                    "Review for cleanup");
        }

        if (extension.equals(".log")) {
            return new FileExplanation(
                    extension,
                    "A log may help an application troubleshoot problems. "
                    + "Check before removing.",
                    "Check first");
        }

        return new FileExplanation(
                extension,
                "SpaceLens cannot confidently identify this file "
                + "from its name and extension alone.",
                "Check first");
    }

    private boolean isPersonalOrProjectFile(String extension) {
        String[] extensions = {
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls",
            ".xlsx", ".txt", ".rtf", ".jpg", ".jpeg", ".png",
            ".gif", ".bmp", ".webp", ".mp4", ".mov", ".avi",
            ".mp3", ".wav", ".zip", ".rar", ".7z", ".java",
            ".class", ".c", ".cpp", ".h", ".py", ".js",
            ".html", ".css", ".sql", ".db", ".csv"
        };

        for (String item : extensions) {
            if (item.equals(extension)) {
                return true;
            }
        }

        return false;
    }

    private static class FileExplanation {
        String fileType;
        String description;
        String recommendation;

        FileExplanation(String fileType, String description,
                        String recommendation) {
            this.fileType = fileType;
            this.description = description;
            this.recommendation = recommendation;
        }
    }

    private JPanel createInsightsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Storage Insights");
        title.setFont(new Font("SansSerif", Font.BOLD, 21));

        JPanel metrics = new JPanel(new GridLayout(2, 2, 10, 10));

        savingsLabel = metric(
                "Potential Duplicate Savings", "Not scanned");
        groupsLabel = metric("Duplicate Groups", "0");
        duplicateCountLabel = metric("Duplicate Files", "0");
        largestCountLabel = metric("Files Scanned", "Not scanned");

        metrics.add(metricCard(savingsLabel));
        metrics.add(metricCard(groupsLabel));
        metrics.add(metricCard(duplicateCountLabel));
        metrics.add(metricCard(largestCountLabel));

        insightsArea = new JTextArea();
        insightsArea.setEditable(false);
        insightsArea.setLineWrap(true);
        insightsArea.setWrapStyleWord(true);
        insightsArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        insightsArea.setMargin(new Insets(10, 10, 10, 10));

        largestSizeLabel = new JLabel();
        folderLabel = new JLabel();

        JPanel details = new JPanel(new GridLayout(2, 1, 4, 4));
        details.add(largestSizeLabel);
        details.add(folderLabel);

        JPanel lower = new JPanel(new BorderLayout(8, 8));
        lower.add(new JLabel("Recommendations"), BorderLayout.NORTH);
        lower.add(new JScrollPane(insightsArea), BorderLayout.CENTER);
        lower.add(details, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.add(title, BorderLayout.NORTH);
        center.add(metrics, BorderLayout.CENTER);
        center.add(lower, BorderLayout.SOUTH);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JLabel metric(String name, String value) {
        return new JLabel(metricText(name, value), SwingConstants.CENTER);
    }

    private String metricText(String name, String value) {
        return "<html><div style='text-align:center'><b>"
                + name + "</b><br><br>" + value + "</div></html>";
    }

    private JPanel metricCard(JLabel label) {
        JPanel card = new JPanel(new BorderLayout());

        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(14, 8, 14, 8)));

        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private void refreshDrives() {
        if (isScanning()) {
            return;
        }

        driveModel.setRowCount(0);
        File[] roots = File.listRoots();

        if (roots != null) {
            for (File root : roots) {
                long total = root.getTotalSpace();
                long free = root.getFreeSpace();
                long usable = root.getUsableSpace();

                driveModel.addRow(new Object[]{
                        root.getAbsolutePath(),
                        formatBytes(total),
                        formatBytes(free),
                        formatBytes(Math.max(0, total - free)),
                        formatBytes(usable)
                });
            }
        }

        statusLabel.setText("Drive overview refreshed.");
    }

    private void findLargestFiles() {
        if (isScanning()) {
            return;
        }

        largestModel.setRowCount(0);
        largestFiles = new ArrayList<FileInfo>();
        filesScanned = 0;
        largestTotalBytes = 0;

        startScan("Searching accessible drives...", worker -> {

            List<FileInfo> top = new ArrayList<FileInfo>();
            final int[] scanned = {0};
            File[] roots = File.listRoots();

            if (roots != null) {
                for (File root : roots) {
                    if (worker.scanCancelled()) {
                        return;
                    }

                    try {
                        Files.walkFileTree(root.toPath(),
                                new SimpleFileVisitor<Path>() {

                            @Override
                            public FileVisitResult visitFile(
                                    Path path, BasicFileAttributes attrs) {

                                if (worker.scanCancelled()) {
                                    return FileVisitResult.TERMINATE;
                                }

                                if (attrs.isRegularFile()) {
                                    scanned[0]++;

                                    top.add(new FileInfo(
                                            path.toFile(), attrs.size()));

                                    Collections.sort(top, (a, b) ->
                                            Long.compare(b.size, a.size));

                                    if (top.size() > 10) {
                                        top.remove(top.size() - 1);
                                    }

                                    if (scanned[0] % 3000 == 0) {
                                        worker.reportStatus(
                                                "Files checked: " + scanned[0]);
                                    }
                                }

                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(
                                    Path path, IOException ex) {
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(
                                    Path path, IOException ex) {
                                return FileVisitResult.CONTINUE;
                            }
                        });

                    } catch (IOException | SecurityException ex) {
                        // Continue scanning other accessible locations.
                    }
                }
            }

            if (!worker.scanCancelled()) {
                largestFiles = top;
                filesScanned = scanned[0];

                for (FileInfo info : top) {
                    largestTotalBytes += info.size;
                }
            }

        }, () -> {
            largestModel.setRowCount(0);

            for (FileInfo info : largestFiles) {
                largestModel.addRow(new Object[]{
                        info.file.getName(),
                        info.size,
                        info.file.getAbsolutePath()
                });
            }

            updateInsights();
            statusLabel.setText("Largest-file scan completed.");
            tabs.setSelectedIndex(1);
        });
    }

    private void chooseFolder() {
        if (isScanning()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a folder to scan");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File folder = chooser.getSelectedFile();

        if (folder == null || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(
                    this, "Select a valid folder.");
            return;
        }

        selectedFolder = folder.getAbsolutePath();
        duplicateModel.setRowCount(0);
        duplicateGroups = new ArrayList<DuplicateGroup>();
        potentialSavings = 0;
        duplicateGroupCount = 0;
        duplicateFileCount = 0;

        startScan("Scanning selected folder for duplicates...", worker -> {

            Map<Long, List<File>> bySize =
                    new HashMap<Long, List<File>>();

            final int[] scanned = {0};

            Files.walkFileTree(folder.toPath(),
                    new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(
                        Path path, BasicFileAttributes attrs) {

                    if (worker.scanCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    if (attrs.isRegularFile()) {
                        List<File> list = bySize.get(attrs.size());

                        if (list == null) {
                            list = new ArrayList<File>();
                            bySize.put(attrs.size(), list);
                        }

                        list.add(path.toFile());
                        scanned[0]++;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(
                        Path path, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path path, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }
            });

            if (worker.scanCancelled()) {
                return;
            }

            List<DuplicateGroup> results =
                    new ArrayList<DuplicateGroup>();

            long savings = 0;
            int groupNumber = 1;

            for (Map.Entry<Long, List<File>> entry : bySize.entrySet()) {
                if (worker.scanCancelled()) {
                    return;
                }

                if (entry.getValue().size() < 2) {
                    continue;
                }

                Map<String, List<File>> byHash =
                        new HashMap<String, List<File>>();

                for (File file : entry.getValue()) {
                    if (worker.scanCancelled()) {
                        return;
                    }

                    try {
                        String hash = calculateSHA256(file);
                        List<File> list = byHash.get(hash);

                        if (list == null) {
                            list = new ArrayList<File>();
                            byHash.put(hash, list);
                        }

                        list.add(file);

                    } catch (IOException | NoSuchAlgorithmException ex) {
                        // Skip files that cannot be read.
                    }
                }

                for (Map.Entry<String, List<File>> hashEntry
                        : byHash.entrySet()) {

                    List<File> sameContent = hashEntry.getValue();

                    if (sameContent.size() > 1) {
                        results.add(new DuplicateGroup(
                                groupNumber++,
                                entry.getKey(),
                                sameContent));

                        savings += entry.getKey()
                                * (sameContent.size() - 1L);
                    }
                }
            }

            if (!worker.scanCancelled()) {
                duplicateGroups = results;
                potentialSavings = savings;
                duplicateGroupCount = results.size();

                int count = 0;

                for (DuplicateGroup group : results) {
                    count += group.files.size();
                }

                duplicateFileCount = count;
            }

        }, () -> {
            duplicateModel.setRowCount(0);

            for (DuplicateGroup group : duplicateGroups) {
                for (File file : group.files) {
                    duplicateModel.addRow(new Object[]{
                            "Group " + group.number,
                            file.getName(),
                            group.size,
                            file.getAbsolutePath()
                    });
                }
            }

            updateInsights();
            statusLabel.setText("Duplicate scan completed.");
            tabs.setSelectedIndex(2);
        });
    }

    private String calculateSHA256(File file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Scan cancelled.");
                }

                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();
        StringBuilder result = new StringBuilder();

        for (byte b : hash) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    private interface ScanTask {
        void run(ScanWorker worker) throws Exception;
    }

    private interface ScanCompletion {
        void complete();
    }

    private abstract class ScanWorker extends SwingWorker<Void, String> {

        public void reportStatus(String message) {
            publish(message);
        }

        public boolean scanCancelled() {
            return isCancelled();
        }
    }

    private void startScan(String message, ScanTask task,
                           ScanCompletion completion) {

        if (isScanning()) {
            return;
        }

        setButtonsEnabled(false);
        cancelButton.setEnabled(true);
        statusLabel.setText(message);

        currentWorker = new ScanWorker() {

            @Override
            protected Void doInBackground() throws Exception {
                task.run(this);
                return null;
            }

            @Override
            protected void process(List<String> messages) {
                if (!messages.isEmpty()) {
                    statusLabel.setText(
                            messages.get(messages.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        statusLabel.setText("Scan cancelled.");
                    } else {
                        get();
                        completion.complete();
                    }

                } catch (Exception ex) {
                    statusLabel.setText(
                            "Scan could not be completed.");

                    JOptionPane.showMessageDialog(
                            SpaceLens.this,
                            "Scan failed or was interrupted.\n"
                            + ex.getMessage(),
                            "Scan Information",
                            JOptionPane.INFORMATION_MESSAGE);

                } finally {
                    currentWorker = null;
                    setButtonsEnabled(true);
                    cancelButton.setEnabled(false);
                    updateInsights();
                }
            }
        };

        currentWorker.execute();
    }

    private boolean isScanning() {
        return currentWorker != null && !currentWorker.isDone();
    }

    private void cancelScan() {
        if (isScanning()) {
            currentWorker.cancel(true);
            statusLabel.setText("Cancelling scan...");
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        largestButton.setEnabled(enabled);
        duplicateButton.setEnabled(enabled);

        if (scanTempButton != null) {
            scanTempButton.setEnabled(enabled);
        }

        if (deleteSelectedButton != null) {
            deleteSelectedButton.setEnabled(enabled);
        }
    }

    private void updateInsights() {
        savingsLabel.setText(metricText(
                "Potential Duplicate Savings",
                "Not scanned".equals(selectedFolder)
                        ? "Not scanned yet"
                        : formatBytes(potentialSavings)));

        groupsLabel.setText(metricText(
                "Duplicate Groups",
                String.valueOf(duplicateGroupCount)));

        duplicateCountLabel.setText(metricText(
                "Duplicate Files",
                String.valueOf(duplicateFileCount)));

        largestCountLabel.setText(metricText(
                "Files Checked",
                filesScanned == 0
                        ? "Not scanned yet"
                        : String.valueOf(filesScanned)));

        largestSizeLabel.setText(
                "<html><b>Combined size of top 10 files:</b> "
                + (filesScanned == 0
                ? "Not scanned yet"
                : formatBytes(largestTotalBytes))
                + "</html>");

        folderLabel.setText(
                "<html><b>Duplicate scan folder:</b> "
                + escapeHtml(selectedFolder) + "</html>");

        StringBuilder text = new StringBuilder();
        text.append("STORAGE RECOMMENDATIONS\n\n");

        if ("Not scanned".equals(selectedFolder)) {
            text.append("1. Choose a folder to scan for duplicate files.\n");

        } else if (duplicateGroupCount == 0) {
            text.append(
                    "1. No duplicate groups were found in the last scan.\n");

        } else {
            text.append("1. Found ")
                    .append(duplicateGroupCount)
                    .append(" duplicate group(s).\n");

            text.append("Potential savings: ")
                    .append(formatBytes(potentialSavings))
                    .append(", assuming one copy is retained.\n");
        }

        text.append("\n2. Review large files before deciding whether they are needed.\n");
        text.append("3. Review old downloads and installers manually.\n");
        text.append("4. Do not manually delete Windows-managed files such as ");
        text.append("hiberfil.sys or pagefile.sys.\n");
        text.append("5. SpaceLens does not delete files automatically.\n");
        text.append("\nDuplicate scanning covers the selected folder. ");
        text.append("Largest-file scanning covers accessible drives separately.");

        insightsArea.setText(text.toString());
        insightsArea.setCaretPosition(0);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int index = 0;

        while (value >= 1024 && index < units.length - 1) {
            value /= 1024.0;
            index++;
        }

        return String.format(Locale.US, "%.2f %s",
                value, units[index]);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static class CleanupFile {
        File file;
        long size;
        long lastModified;

        CleanupFile(File file, long size, long lastModified) {
            this.file = file;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    private static class FileInfo {
        File file;
        long size;

        FileInfo(File file, long size) {
            this.file = file;
            this.size = size;
        }
    }

    private static class DuplicateGroup {
        int number;
        long size;
        List<File> files;

        DuplicateGroup(int number, long size, List<File> files) {
            this.number = number;
            this.size = size;
            this.files = new ArrayList<File>(files);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // Use the default appearance.
            }

            new SpaceLens().setVisible(true);
        });
    }
}