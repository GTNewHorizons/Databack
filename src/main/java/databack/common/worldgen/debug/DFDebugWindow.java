package databack.common.worldgen.debug;

import databack.common.worldgen.dag.DispatchShape;
import mcgpu.core.hwaccel.buffer.BufferDataType;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing debug window for GPU density function introspection.
 *
 * <h3>Opening</h3>
 * Call {@link #open()} from any thread — it uses {@link SwingUtilities#invokeLater} internally.
 *
 * <h3>UI layout</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │ JSplitPane (divider at 280)                                 │
 * │ ┌──────────────┐ ┌────────────────────────────────────────┐ │
 * │ │ LEFT         │ │ JTabbedPane                            │ │
 * │ │ [Enable]     │ │  Kernels | GLSL | Values | Heatmap     │ │
 * │ │ [Disable]    │ │                                        │ │
 * │ │ [Replay]     │ │                                        │ │
 * │ │ [Clear]      │ │                                        │ │
 * │ │              │ │                                        │ │
 * │ │ chunk list   │ │                                        │ │
 * │ └──────────────┘ └────────────────────────────────────────┘ │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 */
public final class DFDebugWindow extends JFrame {

    // ---- Singleton ----
    private static DFDebugWindow instance;

    /**
     * Opens the debug window, creating it if necessary. Safe to call from any thread.
     */
    public static synchronized void open() {
        if (instance == null || !instance.isDisplayable()) {
            instance = new DFDebugWindow();
        }
        SwingUtilities.invokeLater(() -> {
            instance.setVisible(true);
            instance.toFront();
        });
    }

    // ---- Models ----
    private final DefaultListModel<ChunkDebugCapture> chunkListModel = new DefaultListModel<>();
    private final KernelTableModel kernelTableModel = new KernelTableModel();
    private final ValueTableModel  valueTableModel  = new ValueTableModel();
    private final DFMapTableModel  dfMapTableModel  = new DFMapTableModel();

    // ---- Component references needed for updates ----
    private JList<ChunkDebugCapture> chunkList;
    private JTextArea glslArea;
    private HeatmapPanel heatmapPanel;

    // ---- State ----
    private ChunkDebugCapture selectedCapture = null;
    private boolean refreshing = false;

    // -------------------------------------------------------------------------

    private DFDebugWindow() {
        super("GPU Density Function Debugger");
        setSize(1200, 800);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        refreshCaptureList();

        // Poll the store every 500 ms so the chunk list stays current as chunks generate.
        Timer pollTimer = new Timer(500, e -> refreshCaptureList());
        pollTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { pollTimer.stop(); }
        });
    }

    private void buildUI() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(280);
        split.setLeftComponent(buildLeftPanel());
        split.setRightComponent(buildRightPanel());
        add(split, BorderLayout.CENTER);
    }

    // ---- Left panel: chunk list + controls -----------------------------------------

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Buttons
        JPanel buttons = new JPanel(new GridLayout(4, 1, 2, 2));
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JButton enableBtn  = new JButton("Enable Capture");
        JButton disableBtn = new JButton("Disable Capture");
        JButton replayBtn  = new JButton("Replay Chunk");
        JButton clearBtn   = new JButton("Clear All");

        enableBtn.addActionListener(e ->
            DebugCaptureStore.getInstance().setEnabled(true));
        disableBtn.addActionListener(e ->
            DebugCaptureStore.getInstance().setEnabled(false));
        clearBtn.addActionListener(e -> {
            DebugCaptureStore.getInstance().clear();
            refreshCaptureList();
        });
        replayBtn.addActionListener(e -> doReplay());

        buttons.add(enableBtn);
        buttons.add(disableBtn);
        buttons.add(replayBtn);
        buttons.add(clearBtn);

        // Chunk list
        chunkList = new JList<>(chunkListModel);
        chunkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chunkList.setCellRenderer(new ChunkCaptureRenderer());
        chunkList.addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting() && !refreshing) {
                ChunkDebugCapture newSelection = chunkList.getSelectedValue();
                if (newSelection != selectedCapture) {
                    selectedCapture = newSelection;
                    onCaptureSelected(selectedCapture);
                }
            }
        });

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(chunkList), BorderLayout.CENTER);
        return panel;
    }

    // ---- Right panel: tabbed inspector ---------------------------------------------

    private JTabbedPane buildRightPanel() {
        JTabbedPane tabs = new JTabbedPane();

        // Tab 0: Kernels
        JTable kernelTable = new JTable(kernelTableModel);
        kernelTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        kernelTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        kernelTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                int row = kernelTable.getSelectedRow();
                KernelRecord rec = row >= 0 ? kernelTableModel.getRecord(row) : null;
                onRecordSelected(rec);
            }
        });
        tabs.addTab("Kernels", new JScrollPane(kernelTable));

        // Tab 1: GLSL
        glslArea = new JTextArea();
        glslArea.setEditable(false);
        glslArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        glslArea.setLineWrap(false);
        tabs.addTab("GLSL", new JScrollPane(glslArea));

        // Tab 2: Values
        JTable valuesTable = new JTable(valueTableModel);
        valuesTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabs.addTab("Values", new JScrollPane(valuesTable));

        // Tab 3: Heatmap
        heatmapPanel = new HeatmapPanel();
        tabs.addTab("Heatmap", heatmapPanel);

        // Tab 4: DF Map
        JTable dfMapTable = new JTable(dfMapTableModel);
        dfMapTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabs.addTab("DF Map", new JScrollPane(dfMapTable));

        return tabs;
    }

    // ---- Event handlers ------------------------------------------------------------

    private void onCaptureSelected(ChunkDebugCapture capture) {
        List<KernelRecord> records = (capture != null) ? capture.kernelRecords : Collections.emptyList();
        kernelTableModel.setRecords(records);
        glslArea.setText("");
        valueTableModel.setRecord(null);
        heatmapPanel.setData(null, null);
        dfMapTableModel.setCapture(capture);
    }

    private void onRecordSelected(KernelRecord record) {
        glslArea.setText(record != null ? record.glslSource : "");
        glslArea.setCaretPosition(0);
        valueTableModel.setRecord(record);
        heatmapPanel.setData(record, selectedCapture);
    }

    private void doReplay() {
        ChunkDebugCapture sel = selectedCapture;
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Select a chunk first.", "Replay",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Replay blocks the calling thread (GPU submit is synchronous), so run off-EDT.
        // This is a debug tool; we accept the threading limitation.
        Thread t = new Thread(() -> {
            try {
                DebugCaptureStore.getInstance().replay(sel.chunkX, sel.chunkZ);
                SwingUtilities.invokeLater(this::refreshCaptureList);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                        "Replay failed: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE));
            }
        }, "DB-Debug-Replay");
        t.setDaemon(true);
        t.start();
    }

    // ---- Capture list refresh ------------------------------------------------------

    /**
     * Refreshes the chunk list from the store. Safe to call from any thread.
     */
    public void refreshCaptureList() {
        Runnable r = () -> {
            List<ChunkDebugCapture> caps = DebugCaptureStore.getInstance().getCaptures();
            ChunkDebugCapture previousSelection = selectedCapture;
            refreshing = true;
            try {
                chunkListModel.clear();
                int restoreIndex = -1;
                for (int i = 0; i < caps.size(); i++) {
                    ChunkDebugCapture c = caps.get(i);
                    chunkListModel.addElement(c);
                    if (c == previousSelection) restoreIndex = i;
                }
                if (restoreIndex >= 0) {
                    chunkList.setSelectedIndex(restoreIndex);
                }
            } finally {
                refreshing = false;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    // ---- Inner: KernelTableModel ---------------------------------------------------

    private static final class KernelTableModel extends AbstractTableModel {

        private static final String[] COLUMNS =
            {"#", "Shape", "chunkY", "outputBarrierId", "inputs", "dataType", "elements", "range"};

        private List<KernelRecord> records = Collections.emptyList();

        void setRecords(List<KernelRecord> r) {
            records = new ArrayList<>(r);
            fireTableDataChanged();
        }

        KernelRecord getRecord(int row) {
            return records.get(row);
        }

        @Override public int getRowCount()    { return records.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            KernelRecord r = records.get(row);
            if (col == 0) return row;
            if (col == 1) return r.shape;
            if (col == 2) return r.chunkKey[1];
            if (col == 3) return r.outputBarrierId != null ? r.outputBarrierId : "(terminal)";
            if (col == 4) return String.join(", ", r.inputBarrierIds);
            if (col == 5) return r.dataType;
            if (col == 6) return r.outputValues != null ? r.outputValues.length : 0;
            if (col == 7) {
                if (r.outputValues == null || r.outputValues.length == 0) return "\u2014";
                boolean u32 = r.dataType == BufferDataType.u32;
                float rawMin = r.outputValues[0], rawMax = r.outputValues[0];
                for (float v : r.outputValues) {
                    if (v < rawMin) rawMin = v;
                    if (v > rawMax) rawMax = v;
                }
                if (u32) {
                    return Float.floatToRawIntBits(rawMin) + " .. " + Float.floatToRawIntBits(rawMax);
                }
                return rawMin + " .. " + rawMax;
            }
            return "";
        }
    }

    // ---- Inner: ValueTableModel ----------------------------------------------------

    private static final class ValueTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"index", "value"};
        private KernelRecord record = null;

        void setRecord(KernelRecord r) {
            record = r;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return (record == null || record.outputValues == null) ? 0 : record.outputValues.length;
        }

        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            if (col == 0) return row;
            float raw = record.outputValues[row];
            // For u32 buffers, the float bits encode the raw unsigned int.
            if (record.dataType == BufferDataType.u32) {
                return Float.floatToRawIntBits(raw);
            }
            return raw;
        }
    }

    // ---- Inner: HeatmapPanel -------------------------------------------------------

    /**
     * Draws a 2D slice of a kernel output buffer.
     * PER_VOXEL: 16×16 slice at relY=0. PER_COLUMN: 16×16. PER_CORNER: 5×5.
     * Colour: blue = negative / below-zero, red = positive / above-zero.
     */
    private static final class HeatmapPanel extends JPanel {

        private KernelRecord record = null;
        private ChunkDebugCapture capture = null;

        void setData(KernelRecord r, ChunkDebugCapture c) {
            record  = r;
            capture = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (record == null || record.outputValues == null) {
                // Draw final density (terminal output) when no kernel is selected.
                if (capture != null) {
                    drawDensities(g, capture.densities);
                } else {
                    g.drawString("Select a kernel or chunk to view data", 10, 20);
                }
                return;
            }

            drawKernelSlice(g, record);
        }

        private void drawKernelSlice(Graphics g, KernelRecord r) {
            int dim;
            if (r.shape == DispatchShape.PER_VOXEL || r.shape == DispatchShape.PER_COLUMN) {
                dim = 16;
            } else {
                dim = 5; // PER_CORNER
            }

            float[] vals = r.outputValues;
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float v : vals) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            float range = (max - min) > 1e-9f ? (max - min) : 1f;

            int cellW = Math.max(1, (getWidth()  - 10) / dim);
            int cellH = Math.max(1, (getHeight() - 30) / dim);

            // Title
            g.setColor(Color.BLACK);
            g.drawString(r.outputBarrierId + "  [" + r.shape + "]  min=" + min + " max=" + max, 5, 15);

            for (int z = 0; z < dim; z++) {
                for (int x = 0; x < dim; x++) {
                    int idx;
                    if (r.shape == DispatchShape.PER_VOXEL) {
                        idx = z * 256 + 0 * 16 + x; // relY=0 slice
                    } else {
                        idx = z * dim + x;
                    }
                    float norm = (idx < vals.length) ? (vals[idx] - min) / range : 0f;
                    g.setColor(sliceColor(norm));
                    g.fillRect(5 + x * cellW, 20 + z * cellH, cellW, cellH);
                }
            }
        }

        /** Draws Y=8 (middle section) of the final density data. */
        private void drawDensities(Graphics g, float[][] densities) {
            float[] section = densities[8];
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float v : section) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            float range = (max - min) > 1e-9f ? (max - min) : 1f;

            int cellW = Math.max(1, (getWidth()  - 10) / 16);
            int cellH = Math.max(1, (getHeight() - 30) / 16);

            g.setColor(Color.BLACK);
            g.drawString("Final density  Y=8  min=" + min + " max=" + max, 5, 15);

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    // Use relY=8 (middle of the section): index = z*256 + 8*16 + x
                    float norm = (section[z * 256 + 8 * 16 + x] - min) / range;
                    g.setColor(sliceColor(norm));
                    g.fillRect(5 + x * cellW, 20 + z * cellH, cellW, cellH);
                }
            }
        }

        /** Maps [0,1] to blue→red. Values near 0.5 are purple; below = blue, above = red. */
        private static Color sliceColor(float norm) {
            norm = Math.max(0f, Math.min(1f, norm));
            int r = (int)(norm * 255);
            int b = (int)((1f - norm) * 255);
            return new Color(r, 0, b);
        }
    }

    // ---- Inner: DFMapTableModel ----------------------------------------------------

    /**
     * Table model for the "DF Map" tab. One row per kernel group (topological order),
     * showing the density function types that make up each partitioned kernel.
     */
    private static final class DFMapTableModel extends AbstractTableModel {

        private static final String[] COLUMNS =
            {"Barrier", "Kind", "Source DF", "Inlined DFs", "Dispatches"};

        /** Each element: {barrierId, kind, sourceDFType, inlinedDFTypes, dispatchCount}. */
        private List<Object[]> rows = Collections.emptyList();

        void setCapture(ChunkDebugCapture c) {
            if (c == null) {
                rows = Collections.emptyList();
                fireTableDataChanged();
                return;
            }

            // Count dispatches per barrier ID from captured records.
            Map<String, Integer> dispatchCounts = new HashMap<>();
            for (KernelRecord r : c.kernelRecords) {
                String key = r.outputBarrierId != null ? r.outputBarrierId : "(terminal)";
                dispatchCounts.merge(key, 1, Integer::sum);
            }

            List<Object[]> newRows = new ArrayList<>();
            for (Map.Entry<String, String[]> e : c.kernelGroupLabels.entrySet()) {
                String bid  = e.getKey();
                String[] lbl = e.getValue();
                newRows.add(new Object[]{bid, lbl[0], lbl[1], lbl[2],
                    dispatchCounts.getOrDefault(bid, 0)});
            }
            rows = newRows;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            return rows.get(row)[col];
        }
    }

    // ---- Inner: ChunkCaptureRenderer -----------------------------------------------

    private static final class ChunkCaptureRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ChunkDebugCapture) {
                ChunkDebugCapture c = (ChunkDebugCapture) value;
                setText(String.format("[%d]  (%d, %d)   %d kernel records",
                    index, c.chunkX, c.chunkZ, c.kernelRecords.size()));
            }
            return this;
        }
    }
}
