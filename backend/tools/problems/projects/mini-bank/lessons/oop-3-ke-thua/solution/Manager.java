public class Manager extends Employee {
    private final long bonus;

    public Manager(String name, long baseSalary, long bonus) {
        super(name, baseSalary);
        this.bonus = bonus;
    }

    @Override
    public long salary() {
        return super.salary() + bonus;
    }

    @Override
    public String role() {
        return "Quản lý";
    }
}
