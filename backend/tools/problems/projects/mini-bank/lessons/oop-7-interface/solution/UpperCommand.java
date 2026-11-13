import java.util.Locale;

public class UpperCommand implements Command {
    @Override
    public String name() {
        return "UPPER";
    }

    @Override
    public String run(String args) {
        return args.toUpperCase(Locale.ROOT);
    }
}
