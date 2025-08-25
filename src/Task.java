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
        return o instanceof Task t && t.text.equals(this.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }
}
