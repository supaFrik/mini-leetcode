public class AddCommand implements Command {
    @Override
    public String name() {
        return "ADD";
    }

    @Override
    public String run(String args) {
        long total = 0;
        for (String token : args.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                total += Long.parseLong(token);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Không phải số: " + token, e);
            }
        }
        return String.valueOf(total);
    }
}
