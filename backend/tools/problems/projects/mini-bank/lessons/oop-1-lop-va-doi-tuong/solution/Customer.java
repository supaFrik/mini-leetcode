public class Customer {
    private static int nextId = 1;

    private final int id;
    private final String name;

    public Customer(String name) {
        this.id = nextId++;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static int count() {
        return nextId - 1;
    }

    @Override
    public String toString() {
        return "Customer#" + id + ": " + name;
    }
}
