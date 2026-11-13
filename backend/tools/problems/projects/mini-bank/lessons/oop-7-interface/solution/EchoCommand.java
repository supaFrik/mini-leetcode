public class EchoCommand implements Command {
    @Override
    public String name() {
        return "ECHO";
    }

    @Override
    public String run(String args) {
        return args.trim().replaceAll("\\s+", " ");
    }
}
