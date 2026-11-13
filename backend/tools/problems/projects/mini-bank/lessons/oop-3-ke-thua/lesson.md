### Task

- **`Employee`**: name and base salary; `Employee(String name, long baseSalary)`; `public long salary()` returns the base salary; `public String role()` returns `Employee`; `toString()` returns `<name> (<role>): <salary>`.
- **`Manager extends Employee`**: `Manager(String name, long baseSalary, long bonus)`; `salary()` = base + bonus (use `super.salary()`); `role()` = `Manager`.
- **`Intern extends Employee`**: `salary()` = base / 2 (integer division); `role()` = `Intern`.

`Main` reads `n`, then `n` lines: `E <name> <base>`, `M <name> <base> <bonus>` or `I <name> <base>` (one-word names). Store them in one `List<Employee>`, print each, then `Total salary: <sum of salary()>`.

### Example

Input:

```
3
E Ann 10000000
M Ha 20000000 5000000
I Tu 6000000
```

Output:

```
Ann (Employee): 10000000
Ha (Manager): 25000000
Tu (Intern): 3000000
Total salary: 38000000
```

### Grading

- Structure: `Employee` has `public long salary()` and `public String role()`; `Manager` and `Intern` extend `Employee` and override `salary()`.
- Stdout compared line by line, extra spaces ignored.
