import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.datatransfer.*;
import java.io.*;
import java.util.*;

public class NowNext extends JFrame {
    private DefaultListModel<Task> forNowModel;
    private DefaultListModel<Task> nextModel;
    private DefaultListModel<Task> futureModel;

    private static final String FILE_NAME = "tasks.txt";

    // Default tasks for "Next"
    private final java.util.List<String> defaultNextTasks = java.util.List.of(
            "Brush teeth",
            "Cat food",
            "Cat water",
            "Rabbits",
            "Hoover",
            "Mop",
            "Bathroom",
            "Clothes wash",
            "Drying",
            "Tidy kitchen",
            "Check e-mail",
            "Take out trash",
            "Water plants",
            "Pay bills"
    );

    public NowNext() {
        super("Now / Next / Future");

        setSize(900, 400);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Top input panel
        JPanel inputPanel = new JPanel();
        JTextField taskInput = new JTextField(20);
        JButton addButton = new JButton("Add to Next");
        JButton deleteButton = new JButton("Delete Selected");
        inputPanel.add(taskInput);
        inputPanel.add(addButton);
        inputPanel.add(deleteButton);
        add(inputPanel, BorderLayout.NORTH);

        // Models and lists
        forNowModel = new DefaultListModel<>();
        nextModel = new DefaultListModel<>();
        futureModel = new DefaultListModel<>();

        // Add default "Next" tasks if not already present
        for (String text : defaultNextTasks) {
            nextModel.addElement(new Task(text));
        }

        // Lists
        JList<Task> forNowList = new JList<>(forNowModel);
        JList<Task> nextList = new JList<>(nextModel);
        JList<Task> futureList = new JList<>(futureModel);

        enableDragAndDrop(forNowList);
        enableDragAndDrop(nextList);
        enableDragAndDrop(futureList);

        // Labels + lists in panels
        JPanel listsPanel = new JPanel(new GridLayout(1, 3));

        JPanel nowPanel = new JPanel(new BorderLayout());
        nowPanel.add(new JLabel("NOW", SwingConstants.CENTER), BorderLayout.NORTH);
        nowPanel.add(new JScrollPane(forNowList), BorderLayout.CENTER);

        JPanel nextPanel = new JPanel(new BorderLayout());
        nextPanel.add(new JLabel("NEXT", SwingConstants.CENTER), BorderLayout.NORTH);
        nextPanel.add(new JScrollPane(nextList), BorderLayout.CENTER);

        JPanel futurePanel = new JPanel(new BorderLayout());
        futurePanel.add(new JLabel("FUTURE", SwingConstants.CENTER), BorderLayout.NORTH);
        futurePanel.add(new JScrollPane(futureList), BorderLayout.CENTER);

        listsPanel.add(nowPanel);
        listsPanel.add(nextPanel);
        listsPanel.add(futurePanel);

        add(listsPanel, BorderLayout.CENTER);

        // Button actions
        addButton.addActionListener(e -> {
            String text = taskInput.getText().trim();
            if (!text.isEmpty() && !containsTask(nextModel, text)) {
                nextModel.addElement(new Task(text));
                taskInput.setText("");
                saveTasksToFile();
            }
        });

        deleteButton.addActionListener(e -> {
            JList<Task>[] lists = new JList[]{forNowList, nextList, futureList};
            for (JList<Task> list : lists) {
                Task selected = list.getSelectedValue();
                if (selected != null) {
                    ((DefaultListModel<Task>) list.getModel()).removeElement(selected);
                    saveTasksToFile();
                }
            }
        });

        // Load previous tasks
        loadTasksFromFile();

        // Schedule midnight reset
        scheduleMidnightReset();

        setVisible(true);
    }

    private boolean containsTask(DefaultListModel<Task> model, String text) {
        for (int i = 0; i < model.getSize(); i++) {
            if (model.get(i).text.equals(text)) return true;
        }
        return false;
    }

    // Drag-and-drop moves tasks
    private void enableDragAndDrop(JList<Task> list) {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.ON_OR_INSERT);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                JList<Task> source = (JList<Task>) c;
                return new StringSelection(source.getSelectedValue().text);
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                    int index = dl.getIndex();

                    String droppedText = (String) support.getTransferable()
                            .getTransferData(DataFlavor.stringFlavor);

                    // Add to target list
                    DefaultListModel<Task> targetModel = (DefaultListModel<Task>) ((JList<?>) support.getComponent()).getModel();
                    if (!containsTask(targetModel, droppedText)) {
                        if (index < 0) targetModel.addElement(new Task(droppedText));
                        else targetModel.add(index, new Task(droppedText));
                    }

                    // Remove from all other lists
                    DefaultListModel<Task>[] models = new DefaultListModel[]{forNowModel, nextModel, futureModel};
                    for (DefaultListModel<Task> m : models) {
                        if (m != targetModel) {
                            for (int i = 0; i < m.getSize(); i++) {
                                if (m.get(i).text.equals(droppedText)) {
                                    m.remove(i);
                                    break;
                                }
                            }
                        }
                    }

                    saveTasksToFile();
                    return true;

                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        });
    }

    // Load tasks from file
    private void loadTasksFromFile() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) continue;
                String category = parts[0];
                String text = parts[1];
                Task t = new Task(text);
                switch (category) {
                    case "NOW" -> forNowModel.addElement(t);
                    case "NEXT" -> nextModel.addElement(t);
                    case "FUTURE" -> futureModel.addElement(t);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Save tasks to file
    private void saveTasksToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
            saveModel(writer, "NOW", forNowModel);
            saveModel(writer, "NEXT", nextModel);
            saveModel(writer, "FUTURE", futureModel);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Error saving tasks: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveModel(PrintWriter writer, String category, DefaultListModel<Task> model) {
        for (int i = 0; i < model.getSize(); i++) {
            writer.println(category + "|" + model.get(i).text);
        }
    }

    // Midnight reset
    private void scheduleMidnightReset() {
        java.util.Timer timer = new java.util.Timer(true);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long delay = calendar.getTimeInMillis() - System.currentTimeMillis();
        long period = 24 * 60 * 60 * 1000; // 24 hours

        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                resetLists();
            }
        }, delay, period);
    }

    private void resetLists() {
        SwingUtilities.invokeLater(() -> {
            // Move Now → Next
            for (int i = 0; i < forNowModel.getSize(); i++) {
                Task t = forNowModel.get(i);
                if (!nextModel.contains(t)) {
                    nextModel.addElement(t);
                }
            }
            forNowModel.clear();

            // Ensure all default Next tasks are present
            for (String text : defaultNextTasks) {
                if (!containsTask(nextModel, text)) {
                    nextModel.addElement(new Task(text));
                }
            }

            saveTasksToFile();
        });
    }

    public static void main(String[] args) {
        new NowNext();
    }
}
