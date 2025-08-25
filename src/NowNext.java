import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Calendar;
import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;


public class NowNext extends JFrame {
    private DefaultListModel<Task> forNowModel;
    private DefaultListModel<Task> nextModel;
    private DefaultListModel<Task> futureModel;

    private static final String FILE_NAME = "tasks.txt";

    // Default Next tasks
    private List<String> defaultNextTasks = new ArrayList<>(List.of(
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

    // Undo stack
    private Stack<List<Task>[]> undoStack = new Stack<>();

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
        JButton undoButton = new JButton("Undo");
        JButton editDefaultsButton = new JButton("Edit Default Next Tasks");

        inputPanel.add(taskInput);
        inputPanel.add(addButton);
        inputPanel.add(deleteButton);
        inputPanel.add(undoButton);
        inputPanel.add(editDefaultsButton);

        add(inputPanel, BorderLayout.NORTH);

        // Models
        forNowModel = new DefaultListModel<>();
        nextModel = new DefaultListModel<>();
        futureModel = new DefaultListModel<>();

        // Load previous tasks
        loadTasksFromFile();

        // Lists
        JList<Task> forNowList = new JList<>(forNowModel);
        JList<Task> nextList = new JList<>(nextModel);
        JList<Task> futureList = new JList<>(futureModel);

        enableDragAndDrop(forNowList);
        enableDragAndDrop(nextList);
        enableDragAndDrop(futureList);

        // Panels with labels
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
                saveStateForUndo();
                nextModel.addElement(new Task(text));
                taskInput.setText("");
                saveTasksToFile();
            }
        });

        deleteButton.addActionListener(e -> {
            JList<Task>[] lists = new JList[]{forNowList, nextList, futureList};
            boolean changed = false;
            for (JList<Task> list : lists) {
                Task selected = list.getSelectedValue();
                if (selected != null) {
                    if (!changed) saveStateForUndo();
                    ((DefaultListModel<Task>) list.getModel()).removeElement(selected);
                    changed = true;
                }
            }
            if (changed) saveTasksToFile();
        });

        undoButton.addActionListener(e -> undo());
        editDefaultsButton.addActionListener(e -> openDefaultNextEditor());

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

    private void enableDragAndDrop(JList<Task> list) {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.ON_OR_INSERT);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) { return MOVE; }

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

                    saveStateForUndo(); // Save before change

                    Task draggedTask = null;
                    DefaultListModel<Task>[] models = new DefaultListModel[]{forNowModel, nextModel, futureModel};
                    for (DefaultListModel<Task> m : models) {
                        for (int i = 0; i < m.getSize(); i++) {
                            Task t = m.get(i);
                            if (t.text.equals(droppedText)) {
                                draggedTask = t;
                                m.remove(i);
                                break;
                            }
                        }
                        if (draggedTask != null) break;
                    }
                    if (draggedTask == null) draggedTask = new Task(droppedText);

                    DefaultListModel<Task> targetModel = (DefaultListModel<Task>) ((JList<?>) support.getComponent()).getModel();
                    if (index < 0 || index > targetModel.getSize()) targetModel.addElement(draggedTask);
                    else targetModel.add(index, draggedTask);

                    saveTasksToFile();
                    return true;

                } catch (Exception e) { e.printStackTrace(); return false; }
            }
        });
    }

    private void saveStateForUndo() {
        List<Task> nowCopy = new ArrayList<>();
        for (int i = 0; i < forNowModel.getSize(); i++) nowCopy.add(forNowModel.get(i));

        List<Task> nextCopy = new ArrayList<>();
        for (int i = 0; i < nextModel.getSize(); i++) nextCopy.add(nextModel.get(i));

        List<Task> futureCopy = new ArrayList<>();
        for (int i = 0; i < futureModel.getSize(); i++) futureCopy.add(futureModel.get(i));

        undoStack.push(new List[]{nowCopy, nextCopy, futureCopy});
    }

    private void undo() {
        if (undoStack.isEmpty()) return;

        List<Task>[] previous = undoStack.pop();

        forNowModel.clear();
        nextModel.clear();
        futureModel.clear();

        previous[0].forEach(forNowModel::addElement);
        previous[1].forEach(nextModel::addElement);
        previous[2].forEach(futureModel::addElement);

        saveTasksToFile();
    }

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
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveTasksToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
            saveModel(writer, "NOW", forNowModel);
            saveModel(writer, "NEXT", nextModel);
            saveModel(writer, "FUTURE", futureModel);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveModel(PrintWriter writer, String category, DefaultListModel<Task> model) {
        for (int i = 0; i < model.getSize(); i++) {
            Task t = model.get(i);
            writer.println(category + "|" + t.text);
        }
    }

    private void scheduleMidnightReset() {
        java.util.Timer timer = new java.util.Timer(true);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long delay = calendar.getTimeInMillis() - System.currentTimeMillis();
        long period = 24 * 60 * 60 * 1000;

        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() { SwingUtilities.invokeLater(NowNext.this::resetLists); }
        }, delay, period);
    }

    private void resetLists() {
        for (int i = 0; i < forNowModel.getSize(); i++) {
            Task t = forNowModel.get(i);
            if (!nextModel.contains(t)) nextModel.addElement(t);
        }
        forNowModel.clear();

        for (String text : defaultNextTasks) {
            if (!containsTask(nextModel, text)) {
                nextModel.addElement(new Task(text));
            }
        }

        saveTasksToFile();
    }

    private void openDefaultNextEditor() {
        JDialog dialog = new JDialog(this, "Edit Default Next Tasks", true);
        dialog.setSize(400, 400);
        dialog.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String task : defaultNextTasks) model.addElement(task);

        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add Task");
        JButton removeButton = new JButton("Remove Selected");
        JButton saveButton = new JButton("Save & Close");

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(saveButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);

        addButton.addActionListener(e -> {
            String newTask = JOptionPane.showInputDialog(dialog, "Enter new task:");
            if (newTask != null && !newTask.trim().isEmpty() && !model.contains(newTask.trim())) {
                model.addElement(newTask.trim());
            }
        });

        removeButton.addActionListener(e -> {
            int selected = list.getSelectedIndex();
            if (selected != -1) model.remove(selected);
        });

        saveButton.addActionListener(e -> {
            defaultNextTasks.clear();
            for (int i = 0; i < model.getSize(); i++) defaultNextTasks.add(model.getElementAt(i));
            dialog.dispose();
        });

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        new NowNext();
    }
}
