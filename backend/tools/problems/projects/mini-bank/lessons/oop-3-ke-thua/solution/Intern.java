public class Intern extends Employee {
    public Intern(String name, long baseSalary) {
        super(name, baseSalary);
    }

    @Override
    public long salary() {
        return baseSalary / 2;
    }

    @Override
    public String role() {
        return "Thực tập";
    }
}
