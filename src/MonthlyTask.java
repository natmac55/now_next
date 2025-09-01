import java.time.LocalDate;

public class MonthlyTask {
    private String text;
    private LocalDate date;

    public MonthlyTask(String text, LocalDate date) {
        this.text = text;
        this.date = date;
    }

    public String getText() {
        return text;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return date.toString() + " - " + text;
    }
}
