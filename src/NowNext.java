import java.awt.datatransfer.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.datatransfer.*;

import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import javax.swing.*;

public class NowNext extends JFrame {
    private DefaultListModel<Task> forNowModel;
    private DefaultListModel<Task> nextModel;
    private DefaultListModel<Task> futureModel;

    private static final String FILE_NAME = "tasks.txt";
    private static final String DEFAULTS_FILE = "default_tasks.txt";
    private static final String WEEKLY_FILE = "weekly_tasks.txt";
    private static final String MONTHLY_FILE = "monthly_tasks.txt";

    private List<String> defaultNextTasks;
    private List<WeeklyTask> weeklyTasks = new ArrayList<>();
    private List<MonthlyTask> monthlyTasks = new ArrayList<>();
    private Stack<Runnable> undoStack = new Stack<>();

    public NowNext() {
        super("Now / Next / Future");
        setSize(1100, 500);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        loadDefaultTasks();
        loadWeeklyTasks();
        loadMonthlyTasks();

        // Top panel
        JPanel inputPanel = new JPanel();
        JTextField taskInput = new JTextField(20);
        JButton addButton = new JButton("Add to Next");
        JButton addFuture = new JButton("Add to Future");
        JButton deleteButton = new JButton("Delete Selected");
        JButton resetButton = new JButton("Reset");
        JButton editDefaultsButton = new JButton("Edit Defaults");
        JButton editWeeklyButton = new JButton("Edit Weekly Tasks");
        JButton editMonthlyButton = new JButton("Edit Monthly Tasks");

        inputPanel.add(taskInput);
        inputPanel.add(addButton);
        inputPanel.add(addFuture);
        inputPanel.add(deleteButton);
        inputPanel.add(resetButton);
        inputPanel.add(editDefaultsButton);
        inputPanel.add(editWeeklyButton);
        inputPanel.add(editMonthlyButton);
        add(inputPanel, BorderLayout.NORTH);

        // Lists
        forNowModel = new DefaultListModel<>();
        nextModel = new DefaultListModel<>();
        futureModel = new DefaultListModel<>();

        JList<Task> forNowList = new JList<>(forNowModel);
        JList<Task> nextList = new JList<>(nextModel);
        JList<Task> futureList = new JList<>(futureModel);

        forNowList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        nextList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        futureList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        enableDragAndDrop(forNowList);
        enableDragAndDrop(nextList);
        enableDragAndDrop(futureList);

        JPanel listsPanel = new JPanel(new GridLayout(1, 3));
        listsPanel.add(createListPanel("NOW", forNowList));
        listsPanel.add(createListPanel("NEXT", nextList));
        listsPanel.add(createListPanel("FUTURE", futureList));
        add(listsPanel, BorderLayout.CENTER);

        // Button actions
        taskInput.addActionListener(e -> addToNext(taskInput));
        addButton.addActionListener(e -> addToNext(taskInput));

        addFuture.addActionListener(e -> {
            String text = taskInput.getText().trim();
            if (!text.isEmpty() && !containsTask(futureModel, text)) {
                Task newTask = new Task(text);
                futureModel.addElement(newTask);
                taskInput.setText("");
                saveTasksToFile();
                undoStack.push(() -> futureModel.removeElement(newTask));
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

        resetButton.addActionListener(e -> resetLists());
        editDefaultsButton.addActionListener(e -> editDefaultTasks());
        editWeeklyButton.addActionListener(e -> editWeeklyTasks());
        editMonthlyButton.addActionListener(e -> editMonthlyTasks());

        loadTasksFromFile();
        setVisible(true);
    }

    private JPanel createListPanel(String title, JList<Task> list) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title, SwingConstants.CENTER), BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private void addToNext(JTextField taskInput) {
        String text = taskInput.getText().trim();
        if (!text.isEmpty() && !containsTask(nextModel, text)) {
            Task newTask = new Task(text);
            nextModel.addElement(newTask);
            taskInput.setText("");
            saveTasksToFile();
            undoStack.push(() -> nextModel.removeElement(newTask));
        }
    }

    private boolean containsTask(DefaultListModel<Task> model, String text) {
        for (int i = 0; i < model.getSize(); i++)
            if (model.get(i).getText().equals(text)) return true;
        return false;
    }

    private void enableDragAndDrop(JList<Task> list) {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.ON_OR_INSERT);
        list.setTransferHandler(new TransferHandler() {
            public int getSourceActions(JComponent c) { return MOVE; }
            protected Transferable createTransferable(JComponent c) {
                return new StringSelection(((JList<Task>)c).getSelectedValue().getText());
            }
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                    int index = dl.getIndex();
                    String droppedText = (String) support.getTransferable()
                            .getTransferData(DataFlavor.stringFlavor);

                    DefaultListModel<Task> targetModel = (DefaultListModel<Task>) ((JList<?>) support.getComponent()).getModel();
                    if (!containsTask(targetModel, droppedText)) {
                        if (index < 0 || index > targetModel.getSize()) targetModel.addElement(new Task(droppedText));
                        else targetModel.add(index, new Task(droppedText));
                    }

                    DefaultListModel<Task>[] models = new DefaultListModel[]{forNowModel, nextModel, futureModel};
                    for (DefaultListModel<Task> m : models) {
                        if (m != targetModel) {
                            for (int i = 0; i < m.getSize(); i++) {
                                if (m.get(i).getText().equals(droppedText)) {
                                    m.remove(i); break;
                                }
                            }
                        }
                    }
                    saveTasksToFile();
                    return true;
                } catch (Exception e) { e.printStackTrace(); return false; }
            }
        });
    }

    private void loadTasksFromFile() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) continue;
                Task t = new Task(parts[1]);
                switch (parts[0]) {
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
        for (int i = 0; i < model.getSize(); i++)
            writer.println(category + "|" + model.get(i).getText());
    }

    private void resetLists() {
        for (String text : defaultNextTasks)
            if (!containsTask(nextModel, text)) nextModel.addElement(new Task(text));

        for (int i = 0; i < forNowModel.size(); i++) {
            Task t = forNowModel.get(i);
            if (!defaultNextTasks.contains(t.getText())) nextModel.addElement(t);
        }

        forNowModel.clear();

        // Weekly tasks
        String today = LocalDate.now().getDayOfWeek().name();
        for (WeeklyTask wt : weeklyTasks)
            if (wt.getDay().equalsIgnoreCase(today) && !containsTask(nextModel, wt.getText()))
                nextModel.addElement(new Task(wt.getText()));

        // Monthly tasks
        LocalDate todayDate = LocalDate.now();
        for (MonthlyTask mt : monthlyTasks)
            if (mt.getDate().equals(todayDate) && !containsTask(nextModel, mt.getText()))
                nextModel.addElement(new Task(mt.getText()));

        saveTasksToFile();
    }

    private void loadDefaultTasks() {
        defaultNextTasks = new ArrayList<>();
        File file = new File(DEFAULTS_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) defaultNextTasks.add(line.trim());
            } catch (IOException e) { e.printStackTrace(); }
        }

        if (defaultNextTasks.isEmpty()) defaultNextTasks = new ArrayList<>(List.of(
                "Brush teeth","Cat food","Cat water","Rabbits","Hoover","Mop","Bathroom",
                "Clothes wash","Drying","Tidy kitchen","Check e-mail","Take out trash"
        ));
    }

    private void loadWeeklyTasks() {
        weeklyTasks.clear();
        File file = new File(WEEKLY_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) weeklyTasks.add(new WeeklyTask(parts[1], parts[0]));
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void loadMonthlyTasks() {
        monthlyTasks.clear();
        File file = new File(MONTHLY_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    LocalDate date = LocalDate.parse(parts[0]);
                    monthlyTasks.add(new MonthlyTask(parts[1], date));
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveMonthlyTasks() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(MONTHLY_FILE))) {
            for (MonthlyTask mt : monthlyTasks)
                writer.println(mt.getDate() + "|" + mt.getText());
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------- Editors ----------
    private void editDefaultTasks() {
        JDialog dialog = new JDialog(this, "Edit Default Tasks", true);
        dialog.setSize(400, 400);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String t : defaultNextTasks) model.addElement(t);

        JList<String> list = new JList<>(model);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel panel = new JPanel();
        JButton add = new JButton("Add"), remove = new JButton("Remove"), save = new JButton("Save");
        panel.add(add); panel.add(remove); panel.add(save);
        dialog.add(panel, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(dialog, "New Task:");
            if (input != null && !input.trim().isEmpty()) model.addElement(input.trim());
        });
        remove.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null) model.removeElement(selected);
        });
        save.addActionListener(e -> {
            defaultNextTasks.clear();
            for (int i = 0; i < model.size(); i++) defaultNextTasks.add(model.get(i));
            try (PrintWriter writer = new PrintWriter(new FileWriter(DEFAULTS_FILE))) {
                for (String t : defaultNextTasks) writer.println(t);
            } catch (IOException ex) { ex.printStackTrace(); }
            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    private void editWeeklyTasks() {
        JDialog dialog = new JDialog(this, "Edit Weekly Tasks", true);
        dialog.setSize(500, 400);
        DefaultListModel<WeeklyTask> model = new DefaultListModel<>();
        for (WeeklyTask t : weeklyTasks) model.addElement(t);

        JList<WeeklyTask> list = new JList<>(model);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel panel = new JPanel();
        JButton add = new JButton("Add"), remove = new JButton("Remove"), save = new JButton("Save");
        panel.add(add); panel.add(remove); panel.add(save);
        dialog.add(panel, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            String[] days = {"MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"};
            JComboBox<String> dayBox = new JComboBox<>(days);
            JTextField taskField = new JTextField(20);
            JPanel input = new JPanel();
            input.add(new JLabel("Day:")); input.add(dayBox);
            input.add(new JLabel("Task:")); input.add(taskField);
            int result = JOptionPane.showConfirmDialog(dialog, input, "Add Weekly Task", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String text = taskField.getText().trim();
                String day = (String) dayBox.getSelectedItem();
                if (!text.isEmpty()) model.addElement(new WeeklyTask(text, day));
            }
        });

        remove.addActionListener(e -> {
            WeeklyTask selected = list.getSelectedValue();
            if (selected != null) model.removeElement(selected);
        });

        save.addActionListener(e -> {
            weeklyTasks.clear();
            for (int i = 0; i < model.size(); i++) weeklyTasks.add(model.get(i));
            try (PrintWriter writer = new PrintWriter(new FileWriter(WEEKLY_FILE))) {
                for (WeeklyTask t : weeklyTasks) writer.println(t.getDay() + "|" + t.getText());
            } catch (IOException ex) { ex.printStackTrace(); }
            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    private void editMonthlyTasks() {
        JDialog dialog = new JDialog(this, "Edit Monthly Tasks", true);
        dialog.setSize(500, 400);
        DefaultListModel<MonthlyTask> model = new DefaultListModel<>();
        for (MonthlyTask t : monthlyTasks) model.addElement(t);

        JList<MonthlyTask> list = new JList<>(model);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel panel = new JPanel();
        JButton add = new JButton("Add"), remove = new JButton("Remove"), save = new JButton("Save");
        panel.add(add); panel.add(remove); panel.add(save);
        dialog.add(panel, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            JSpinner year = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2100, 1));
            JSpinner month = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
            JSpinner day = new JSpinner(new SpinnerNumberModel(LocalDate.now().getDayOfMonth(), 1, 31, 1));
            JTextField taskField = new JTextField(20);
            JPanel input = new JPanel();
            input.add(new JLabel("Year:")); input.add(year);
            input.add(new JLabel("Month:")); input.add(month);
            input.add(new JLabel("Day:")); input.add(day);
            input.add(new JLabel("Task:")); input.add(taskField);

            int result = JOptionPane.showConfirmDialog(dialog, input, "Add Monthly Task", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String text = taskField.getText().trim();
                try {
                    LocalDate date = LocalDate.of((int)year.getValue(), (int)month.getValue(), (int)day.getValue());
                    if (!text.isEmpty()) model.addElement(new MonthlyTask(text, date));
                } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, "Invalid date!"); }
            }
        });

        remove.addActionListener(e -> {
            MonthlyTask selected = list.getSelectedValue();
            if (selected != null) model.removeElement(selected);
        });

        save.addActionListener(e -> {
            monthlyTasks.clear();
            for (int i = 0; i < model.size(); i++) monthlyTasks.add(model.get(i));
            saveMonthlyTasks();
            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(NowNext::new);
    }
}
