public class Employee {
    private final String name;
    protected final long baseSalary;

    public Employee(String name, long baseSalary) {
        this.name = name;
        this.baseSalary = baseSalary;
    }

    public long salary() {
        return baseSalary;
    }

    public String role() {
        return "Employee";
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + " (" + role() + "): " + salary();
    }
}
