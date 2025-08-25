import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;


public class NowNext extends JFrame {
    private DefaultListModel<Task> forNowModel;
    private DefaultListModel<Task> nextModel;
    private DefaultListModel<Task> futureModel;

    private static final String FILE_NAME = "tasks.txt";

    public NowNext() {
        super("Now, Next and Future");

        setSize(700, 400);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // ===== Input area (top) =====
        JPanel inputPanel = new JPanel();
        JTextField taskInput = new JTextField(20);
        JButton addButton = new JButton("Add to Now");
        JButton moveButton = new JButton("Move Selected");
        JButton deleteButton = new JButton("Delete Selected");

        inputPanel.add(taskInput);
        inputPanel.add(addButton);
        inputPanel.add(moveButton);
        inputPanel.add(deleteButton);
        add(inputPanel, BorderLayout.NORTH);

        // ===== Models and Lists =====
        forNowModel = new DefaultListModel<>();
        nextModel = new DefaultListModel<>();
        futureModel = new DefaultListModel<>();

        JList<Task> forNowList = new JList<>(forNowModel);
        JList<Task> nextList = new JList<>(nextModel);
        JList<Task> futureList = new JList<>(futureModel);

        // Add borders with titles
        forNowList.setBorder(BorderFactory.createTitledBorder("For Now"));
        nextList.setBorder(BorderFactory.createTitledBorder("Next"));
        futureList.setBorder(BorderFactory.createTitledBorder("Future"));

        // Enable drag and drop
        enableDragAndDrop(forNowList, forNowModel);
        enableDragAndDrop(nextList, nextModel);
        enableDragAndDrop(futureList, futureModel);

        // ===== Bottom panel with 3 sections =====
        JPanel bottomPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        bottomPanel.add(new JScrollPane(forNowList));
        bottomPanel.add(new JScrollPane(nextList));
        bottomPanel.add(new JScrollPane(futureList));
        add(bottomPanel, BorderLayout.CENTER);

        // ===== Button logic =====
        addButton.addActionListener(e -> {
            String text = taskInput.getText().trim();
            if (!text.isEmpty()) {
                forNowModel.addElement(new Task(text)); // new tasks go in "For Now"
                taskInput.setText("");
                saveTasksToFile();
            }
        });

        deleteButton.addActionListener(e -> {
            if (!forNowList.isSelectionEmpty()) {
                forNowModel.remove(forNowList.getSelectedIndex());
            } else if (!nextList.isSelectionEmpty()) {
                nextModel.remove(nextList.getSelectedIndex());
            } else if (!futureList.isSelectionEmpty()) {
                futureModel.remove(futureList.getSelectedIndex());
            }
            saveTasksToFile();
        });

        moveButton.addActionListener(e -> {
            if (!forNowList.isSelectionEmpty()) {
                Task t = forNowList.getSelectedValue();
                forNowModel.remove(forNowList.getSelectedIndex());
                nextModel.addElement(t);
            } else if (!nextList.isSelectionEmpty()) {
                Task t = nextList.getSelectedValue();
                nextModel.remove(nextList.getSelectedIndex());
                futureModel.addElement(t);
            } else if (!futureList.isSelectionEmpty()) {
                Task t = futureList.getSelectedValue();
                futureModel.remove(futureList.getSelectedIndex());
                forNowModel.addElement(t);
            }
            saveTasksToFile();
        });

        // ===== Load tasks from file =====
        loadTasksFromFile();

        setVisible(true);
    }

    // ===== Enable drag and drop for a list =====
    private void enableDragAndDrop(JList<Task> list, DefaultListModel<Task> model) {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                JList<Task> sourceList = (JList<Task>) c;
                Task selected = sourceList.getSelectedValue();
                return new StringSelection(selected.text);
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    String droppedText = (String) support.getTransferable()
                            .getTransferData(DataFlavor.stringFlavor);
                    model.addElement(new Task(droppedText));
                    saveTasksToFile();
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        });
    }

    // ===== File Persistence =====
    private void loadTasksFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    String category = parts[0];
                    String text = parts[1];
                    Task t = new Task(text);

                    switch (category) {
                        case "NOW" -> forNowModel.addElement(t);
                        case "NEXT" -> nextModel.addElement(t);
                        case "FUTURE" -> futureModel.addElement(t);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("No saved task file found — starting fresh.");
        }
    }

    private void saveTasksToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
            for (int i = 0; i < forNowModel.getSize(); i++) {
                writer.println("NOW|" + forNowModel.get(i).text);
            }
            for (int i = 0; i < nextModel.getSize(); i++) {
                writer.println("NEXT|" + nextModel.get(i).text);
            }
            for (int i = 0; i < futureModel.getSize(); i++) {
                writer.println("FUTURE|" + futureModel.get(i).text);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Error saving tasks: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        new NowNext();
    }
}
