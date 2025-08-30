import java.util.Objects;

public class Task {
    public String text;

    public Task(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task)) return false;
        Task task = (Task) o;
        return Objects.equals(text, task.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    public String getText() {
        return text;
    }
}
