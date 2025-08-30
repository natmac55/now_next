import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;

public class NowNext extends JFrame {
    private DefaultListModel<Task> forNowModel;
    private DefaultListModel<Task> nextModel;
    private DefaultListModel<Task> futureModel;

    private static final String FILE_NAME = "tasks.txt";
    private static final String DEFAULTS_FILE = "default_tasks.txt";

    private List<String> defaultNextTasks; // loaded from file or fallback
    private Stack<Runnable> undoStack = new Stack<>();

    public NowNext() {
        super("Now / Next / Future");

        setSize(900, 400);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Load defaults (from file or hardcoded)
        loadDefaultTasks();

        // Top input panel
        JPanel inputPanel = new JPanel();
        JTextField taskInput = new JTextField(20);
        JButton addButton = new JButton("Add to Next");
        JButton addFuture = new JButton("Add to Future");
        JButton deleteButton = new JButton("Delete Selected");
        // JButton undoButton = new JButton("Undo");
        JButton resetButton = new JButton("Reset");
        JButton editDefaultsButton = new JButton("Edit Defaults");

        inputPanel.add(taskInput);
        inputPanel.add(addButton);
        inputPanel.add(addFuture);
        inputPanel.add(deleteButton);
        // inputPanel.add(undoButton);
        inputPanel.add(resetButton);
        inputPanel.add(editDefaultsButton);
        add(inputPanel, BorderLayout.NORTH);

        // Models and lists
        forNowModel = new DefaultListModel<>();
        nextModel = new DefaultListModel<>();
        futureModel = new DefaultListModel<>();

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

        taskInput.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = taskInput.getText().trim();
                if (!text.isEmpty() && !containsTask(nextModel, text)) {
                    Task newTask = new Task(text);
                    nextModel.addElement(newTask);
                    taskInput.setText("");
                    saveTasksToFile();
                    undoStack.push(() -> nextModel.removeElement(newTask));
                }
            }
        });

        addButton.addActionListener(e -> {
            String text = taskInput.getText().trim();
            if (!text.isEmpty() && !containsTask(nextModel, text)) {
                Task newTask = new Task(text);
                nextModel.addElement(newTask);
                taskInput.setText("");
                saveTasksToFile();
                undoStack.push(() -> nextModel.removeElement(newTask));
            }
        });

        addFuture.addActionListener(e -> {
            String text = taskInput.getText().trim();
            if (!text.isEmpty() && !containsTask(futureModel, text)) {
                Task newTask = new Task(text);
                futureModel.addElement(newTask);
                taskInput.setText("");
                saveTasksToFile();
                undoStack.push(() -> nextModel.removeElement(newTask));
            }
        });

        deleteButton.addActionListener(e -> {
            JList<Task>[] lists = new JList[]{forNowList, nextList, futureList};
            for (JList<Task> list : lists) {
                Task selected = list.getSelectedValue();
                if (selected != null) {
                    DefaultListModel<Task> model = (DefaultListModel<Task>) list.getModel();
                    model.removeElement(selected);
                    saveTasksToFile();
                    undoStack.push(() -> model.addElement(selected));
                }
            }
        });

        /*
        undoButton.addActionListener(e -> {
            if (!undoStack.isEmpty()) {
                undoStack.pop().run();
                saveTasksToFile();
            }
        });
        */


        resetButton.addActionListener(e -> resetLists());

        editDefaultsButton.addActionListener(e -> editDefaultTasks());

        // Load previous tasks
        loadTasksFromFile();

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

                    DefaultListModel<Task> targetModel = (DefaultListModel<Task>) ((JList<?>) support.getComponent()).getModel();
                    if (!containsTask(targetModel, droppedText)) {
                        if (index < 0 || index > targetModel.getSize()) {
                            targetModel.addElement(new Task(droppedText));
                        } else {
                            targetModel.add(index, new Task(droppedText));
                        }
                    }

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

    // Reset button logic
    private void resetLists() {
        nextModel.clear();

        // Add default tasks
        for (String text : defaultNextTasks) {
            nextModel.addElement(new Task(text));
        }

        // Add any extra tasks from forNowModel that aren't defaults
        for (int i = 0; i < forNowModel.size(); i++) {
            Task task = forNowModel.getElementAt(i);
            if (!defaultNextTasks.contains(task.getText())) {
                nextModel.addElement(task);
            }
        }

        // Clear forNow list
        forNowModel.clear();

        saveTasksToFile();
    }

    // Load defaults from file or use fallback
    private void loadDefaultTasks() {
        File file = new File(DEFAULTS_FILE);
        defaultNextTasks = new ArrayList<>();

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    defaultNextTasks.add(line.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (defaultNextTasks.isEmpty()) {
            defaultNextTasks = new ArrayList<>(List.of(
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
                    "Take out trash"
            ));
        }
    }

    // Pop-out editor for default tasks
    private void editDefaultTasks() {
        JDialog dialog = new JDialog(this, "Edit Default Tasks", true);
        dialog.setSize(400, 400);
        dialog.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String task : defaultNextTasks) {
            model.addElement(task);
        }

        JList<String> list = new JList<>(model);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton addBtn = new JButton("Add");
        JButton removeBtn = new JButton("Remove");
        JButton saveBtn = new JButton("Save");

        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(saveBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> {
            String newTask = JOptionPane.showInputDialog(dialog, "New Task:");
            if (newTask != null && !newTask.trim().isEmpty()) {
                model.addElement(newTask.trim());
            }
        });

        removeBtn.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null) {
                model.removeElement(selected);
            }
        });

        saveBtn.addActionListener(e -> {
            defaultNextTasks.clear();
            for (int i = 0; i < model.getSize(); i++) {
                defaultNextTasks.add(model.get(i));
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(DEFAULTS_FILE))) {
                for (String task : defaultNextTasks) {
                    writer.println(task);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        new NowNext();
    }
}
