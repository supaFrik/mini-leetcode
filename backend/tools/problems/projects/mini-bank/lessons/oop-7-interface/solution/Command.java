public interface Command {
    String name();

    String run(String args);
}
