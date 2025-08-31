import java.util.Objects;

public class WeeklyTask {
    private String text;   // what the task is
    private String day;    // which day it's assigned to (e.g. "Monday")

    public WeeklyTask(String text, String day) {
        this.text = text;
        this.day = day;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    @Override
    public String toString() {
        // this is what shows in a JList or console
        return day + ": " + text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeeklyTask)) return false;
        WeeklyTask that = (WeeklyTask) o;
        return Objects.equals(text, that.text) &&
                Objects.equals(day, that.day);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, day);
    }
}
