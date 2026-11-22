import json
import requests
import subprocess
import time

API_BASE = "http://localhost:8088/api/v1"

# 1. Login to get Admin JWT
login_res = requests.post(f"{API_BASE}/auth/login", json={"usernameOrEmail": "live_admin", "password": "Password123!"})
login_res.raise_for_status()
tok = login_res.json().get("accessToken") or login_res.json().get("token")
headers = {"Authorization": f"Bearer {tok}"}
print("Admin authenticated successfully.")

# Helper to execute MySQL queries via Docker
def run_mysql(query: str):
    cmd = [
        "docker", "exec", "-i", "mini_leetcode_mysql",
        "mysql", "--default-character-set=utf8mb4",
        "-u", "leetcode", "-pleetcode_password", "mini_leetcode"
    ]
    proc = subprocess.run(cmd, input=query.encode("utf-8"), capture_output=True)
    if proc.returncode != 0:
        print("MySQL Error:", proc.stderr.decode("utf-8"))
    return proc.stdout.decode("utf-8")

def sql_esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")

def upload_tc(prob_id: int, inp: str, out: str, is_hidden: bool, label: str):
    files = {
        "inputFile": ("input.txt", inp.encode("utf-8"), "text/plain"),
        "outputFile": ("output.txt", out.encode("utf-8"), "text/plain"),
    }
    data = {
        "isHidden": "true" if is_hidden else "false",
        "label": label
    }
    res = requests.post(f"{API_BASE}/problems/{prob_id}/test-cases", headers=headers, files=files, data=data)
    if res.status_code not in (200, 201):
        print(f"Failed to upload testcase for problem {prob_id}: {res.status_code} {res.text}")
    else:
        print(f"  [+] Uploaded testcase '{label}' for problem {prob_id} (hidden={is_hidden})")

# Clean up legacy 118-124 problems and 101 by archiving them
run_mysql("UPDATE problems SET status = 'ARCHIVED' WHERE id IN (101, 118, 119, 120, 121, 122, 123, 124);")
print("Archived legacy problems 101 and 118-124.")

PROBLEMS_DATA = []

# =========================================================================
# PROBLEM 1 (ID 140): Employee Payroll: Encapsulation & Domain Invariants
# =========================================================================
p140 = {
    "id": 140,
    "slug": "oop-employee-payroll-encapsulation",
    "title": "Employee Payroll: Encapsulation & Domain Invariants",
    "difficulty": "EASY",
    "tags": "OOP, Encapsulation, Domain Invariants, Value Protection",
    "description": """### Problem Statement: Employee Payroll - Encapsulation & Domain Invariants

Design and implement a payroll processing domain model that strictly enforces **Data Encapsulation** and **Domain Invariant Protection**.

#### 1. Domain Class: `Employee`
Create an `Employee` class in `Employee.java` using the `+ Add Java file` tab:
- **Private Fields**:
  - `private String id;`
  - `private String name;`
  - `private double baseSalary;`
  - `private double taxRate;`
- **Constructor**:
  - `public Employee(String id, String name, double baseSalary, double taxRate)`
  - **Invariants**:
    - If `baseSalary < 0`, it must be clamped to `0.0`.
    - If `taxRate < 0.0`, clamp to `0.0`. If `taxRate > 1.0`, clamp to `1.0`.
- **Public Methods**:
  - `public String getId()`
  - `public String getName()`
  - `public double getBaseSalary()`
  - `public double getTaxRate()`
  - `public double calculateTax()`: returns `baseSalary * taxRate`.
  - `public double calculateNetSalary()`: returns `baseSalary - calculateTax()`.
  - `public void raiseSalary(double percentage)`: if `percentage > 0`, increases `baseSalary` by that percentage (`baseSalary = baseSalary * (1.0 + percentage / 100.0)`). If non-positive, make no change.
- **Rules**:
  - All fields must remain `private`. Do not expose public setter methods that bypass invariants.

#### 2. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: An integer `N` representing the number of employees.
  - Next `N` lines: `id name baseSalary taxRate raisePercentage`
- **Output Format (stdout)**:
  - For each employee, output:
    `Employee[<id>] <name> | Base: <baseSalary> | Tax: <tax> | Net: <netSalary>`
    (Values formatted to 2 decimal places with `Locale.ROOT`).
  - Final line:
    `Total Net Payroll: <sumOfNetSalaries>` (formatted to 2 decimal places).

#### Example
**Input:**
```
2
E01 Alice 3000.0 0.1 10.0
E02 Bob 5000.0 0.2 0.0
```
**Output:**
```
Employee[E01] Alice | Base: 3300.00 | Tax: 330.00 | Net: 2970.00
Employee[E02] Bob | Base: 5000.00 | Tax: 1000.00 | Net: 4000.00
Total Net Payroll: 6970.00
```
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create Employee.java using the '+ Add Java file' tab above\n        // TODO: Read inputs and compute payroll with strict encapsulation\n    }\n}\n"}
    ],
    "solution": [
        {"path": "Employee.java", "content": "public class Employee {\n    private String id;\n    private String name;\n    private double baseSalary;\n    private double taxRate;\n\n    public Employee(String id, String name, double baseSalary, double taxRate) {\n        this.id = id;\n        this.name = name;\n        this.baseSalary = (baseSalary < 0) ? 0.0 : baseSalary;\n        if (taxRate < 0.0) this.taxRate = 0.0;\n        else if (taxRate > 1.0) this.taxRate = 1.0;\n        else this.taxRate = taxRate;\n    }\n\n    public String getId() { return id; }\n    public String getName() { return name; }\n    public double getBaseSalary() { return baseSalary; }\n    public double getTaxRate() { return taxRate; }\n\n    public double calculateTax() {\n        return baseSalary * taxRate;\n    }\n\n    public double calculateNetSalary() {\n        return baseSalary - calculateTax();\n    }\n\n    public void raiseSalary(double percentage) {\n        if (percentage > 0) {\n            this.baseSalary = this.baseSalary * (1.0 + percentage / 100.0);\n        }\n    }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextInt()) return;\n        int n = sc.nextInt();\n        List<Employee> employees = new ArrayList<>();\n        for (int i = 0; i < n; i++) {\n            String id = sc.next();\n            String name = sc.next();\n            double base = sc.nextDouble();\n            double tax = sc.nextDouble();\n            double raise = sc.nextDouble();\n            Employee emp = new Employee(id, name, base, tax);\n            emp.raiseSalary(raise);\n            employees.add(emp);\n        }\n\n        double totalNet = 0.0;\n        for (Employee e : employees) {\n            double net = e.calculateNetSalary();\n            totalNet += net;\n            System.out.println(String.format(Locale.ROOT, \"Employee[%s] %s | Base: %.2f | Tax: %.2f | Net: %.2f\",\n                    e.getId(), e.getName(), e.getBaseSalary(), e.calculateTax(), net));\n        }\n        System.out.println(String.format(Locale.ROOT, \"Total Net Payroll: %.2f\", totalNet));\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Standard Employees", "hidden": False, "in": "2\nE01 Alice 3000.0 0.1 10.0\nE02 Bob 5000.0 0.2 0.0\n", "out": "Employee[E01] Alice | Base: 3300.00 | Tax: 330.00 | Net: 2970.00\nEmployee[E02] Bob | Base: 5000.00 | Tax: 1000.00 | Net: 4000.00\nTotal Net Payroll: 6970.00\n"},
        {"label": "Sample 2: Single Employee High Raise", "hidden": False, "in": "1\nE99 Charlie 1000.0 0.15 50.0\n", "out": "Employee[E99] Charlie | Base: 1500.00 | Tax: 225.00 | Net: 1275.00\nTotal Net Payroll: 1275.00\n"},
        {"label": "Hidden 1: Invariant Clamping (Negative Salary & Out-of-bounds Tax)", "hidden": True, "in": "2\nE03 Dave -500.0 -0.5 0.0\nE04 Eva 2000.0 1.5 20.0\n", "out": "Employee[E03] Dave | Base: 0.00 | Tax: 0.00 | Net: 0.00\nEmployee[E04] Eva | Base: 2400.00 | Tax: 2400.00 | Net: 0.00\nTotal Net Payroll: 0.00\n"},
        {"label": "Hidden 2: Multi-employee Mixed Boundary", "hidden": True, "in": "4\nEMP1 John 4000.0 0.12 5.0\nEMP2 Sarah 6000.0 0.25 0.0\nEMP3 Mike 2500.0 0.05 10.0\nEMP4 Emma 8000.0 0.30 15.0\n", "out": "Employee[EMP1] John | Base: 4200.00 | Tax: 504.00 | Net: 3696.00\nEmployee[EMP2] Sarah | Base: 6000.00 | Tax: 1500.00 | Net: 4500.00\nEmployee[EMP3] Mike | Base: 2750.00 | Tax: 137.50 | Net: 2612.50\nEmployee[EMP4] Emma | Base: 9200.00 | Tax: 2760.00 | Net: 6440.00\nTotal Net Payroll: 17248.50\n"}
    ]
}
PROBLEMS_DATA.append(p140)

# =========================================================================
# PROBLEM 2 (ID 141): Digital Wallet & Immutable Transactions
# =========================================================================
p141 = {
    "id": 141,
    "slug": "oop-wallet-immutability",
    "title": "Digital Wallet & Immutable Transactions",
    "difficulty": "EASY",
    "tags": "OOP, Encapsulation, Strict Immutability, Value Object",
    "description": """### Problem Statement: Digital Wallet & Immutable Transactions

Build a financial digital wallet ecosystem enforcing **Data Encapsulation** and **Strict Immutability** for audit safety.

#### 1. Immutable Value Object: `Transaction`
Create `Transaction.java` using the `+ Add Java file` tab:
- **Private Final Fields**:
  - `private final String id;`
  - `private final double amount;`
  - `private final String type;` (`"DEPOSIT"` or `"WITHDRAW"`)
- **Constructor**:
  - `public Transaction(String id, double amount, String type)`
- **Getters**:
  - `public String getId()`
  - `public double getAmount()`
  - `public String getType()`
- **Immutability Invariant**:
  - All fields must be declared `final`. Setter methods are strictly prohibited.

#### 2. Domain Entity: `Wallet`
Create `Wallet.java` using the `+ Add Java file` tab:
- **Private Fields**:
  - `private String owner;`
  - `private double balance;`
  - `private List<Transaction> history;`
- **Constructor**:
  - `public Wallet(String owner)` (initializes `balance = 0.0` and empty `history`).
- **Methods**:
  - `public boolean deposit(String txId, double amount)`: if `amount > 0`, increases balance, appends `new Transaction(txId, amount, "DEPOSIT")` to history and returns `true`. Otherwise returns `false`.
  - `public boolean withdraw(String txId, double amount)`: if `amount > 0` and `amount <= balance`, deducts balance, appends `new Transaction(txId, amount, "WITHDRAW")` to history and returns `true`. Otherwise returns `false`.
  - `public double getBalance()`
  - `public String getOwner()`
  - `public List<Transaction> getHistory()`

#### 3. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: `owner` (String)
  - Line 2: `N` (number of commands)
  - Next `N` lines: `DEPOSIT <txId> <amount>` or `WITHDRAW <txId> <amount>`
- **Output Format (stdout)**:
  - Line 1: `Owner: <owner> | Balance: <balance>` (formatted to 2 decimal places).
  - Line 2: `Transactions: <count>`
  - Next lines: `[<type>] ID: <id> | Amount: <amount>` (formatted to 2 decimal places).
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create Transaction.java and Wallet.java via '+ Add Java file'\n        // TODO: Enforce final fields on Transaction and encapsulate Wallet state\n    }\n}\n"}
    ],
    "solution": [
        {"path": "Transaction.java", "content": "public class Transaction {\n    private final String id;\n    private final double amount;\n    private final String type;\n\n    public Transaction(String id, double amount, String type) {\n        this.id = id;\n        this.amount = amount;\n        this.type = type;\n    }\n\n    public String getId() { return id; }\n    public double getAmount() { return amount; }\n    public String getType() { return type; }\n}\n"},
        {"path": "Wallet.java", "content": "import java.util.*;\n\npublic class Wallet {\n    private String owner;\n    private double balance;\n    private List<Transaction> history;\n\n    public Wallet(String owner) {\n        this.owner = owner;\n        this.balance = 0.0;\n        this.history = new ArrayList<>();\n    }\n\n    public boolean deposit(String txId, double amount) {\n        if (amount <= 0) return false;\n        this.balance += amount;\n        this.history.add(new Transaction(txId, amount, \"DEPOSIT\"));\n        return true;\n    }\n\n    public boolean withdraw(String txId, double amount) {\n        if (amount <= 0 || amount > balance) return false;\n        this.balance -= amount;\n        this.history.add(new Transaction(txId, amount, \"WITHDRAW\"));\n        return true;\n    }\n\n    public double getBalance() { return balance; }\n    public String getOwner() { return owner; }\n    public List<Transaction> getHistory() { return history; }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextLine()) return;\n        String owner = sc.nextLine().trim();\n        int n = Integer.parseInt(sc.nextLine().trim());\n        Wallet wallet = new Wallet(owner);\n\n        for (int i = 0; i < n; i++) {\n            String[] parts = sc.nextLine().trim().split(\"\\\\s+\");\n            String cmd = parts[0];\n            String id = parts[1];\n            double amount = Double.parseDouble(parts[2]);\n            if (cmd.equals(\"DEPOSIT\")) wallet.deposit(id, amount);\n            else if (cmd.equals(\"WITHDRAW\")) wallet.withdraw(id, amount);\n        }\n\n        System.out.println(String.format(Locale.ROOT, \"Owner: %s | Balance: %.2f\", wallet.getOwner(), wallet.getBalance()));\n        List<Transaction> txs = wallet.getHistory();\n        System.out.println(\"Transactions: \" + txs.size());\n        for (Transaction tx : txs) {\n            System.out.println(String.format(Locale.ROOT, \"[%s] ID: %s | Amount: %.2f\", tx.getType(), tx.getId(), tx.getAmount()));\n        }\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-strict-immutability", "ruleName": "Strict Immutability in Transaction", "targetClassName": "Transaction", "requireFinalFields": True, "weight": 2.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Standard Flow", "hidden": False, "in": "Alex\n3\nDEPOSIT TX1 200.0\nWITHDRAW TX2 50.0\nDEPOSIT TX3 100.0\n", "out": "Owner: Alex | Balance: 250.00\nTransactions: 3\n[DEPOSIT] ID: TX1 | Amount: 200.00\n[WITHDRAW] ID: TX2 | Amount: 50.00\n[DEPOSIT] ID: TX3 | Amount: 100.00\n"},
        {"label": "Sample 2: Overdraft Rejection", "hidden": False, "in": "Brian\n2\nDEPOSIT T1 100.0\nWITHDRAW T2 150.0\n", "out": "Owner: Brian | Balance: 100.00\nTransactions: 1\n[DEPOSIT] ID: T1 | Amount: 100.00\n"},
        {"label": "Hidden 1: Negative & Zero Operations Rejected", "hidden": True, "in": "Chloe\n4\nDEPOSIT T0 -50.0\nDEPOSIT T1 0.0\nDEPOSIT T2 300.0\nWITHDRAW T3 -20.0\n", "out": "Owner: Chloe | Balance: 300.00\nTransactions: 1\n[DEPOSIT] ID: T2 | Amount: 300.00\n"},
        {"label": "Hidden 2: Multiple Consecutive Operations", "hidden": True, "in": "David\n5\nDEPOSIT D1 500.0\nWITHDRAW D2 200.0\nWITHDRAW D3 200.0\nWITHDRAW D4 150.0\nDEPOSIT D5 50.0\n", "out": "Owner: David | Balance: 150.00\nTransactions: 4\n[DEPOSIT] ID: D1 | Amount: 500.00\n[WITHDRAW] ID: D2 | Amount: 200.00\n[WITHDRAW] ID: D3 | Amount: 200.00\n[DEPOSIT] ID: D5 | Amount: 50.00\n"}
    ]
}
PROBLEMS_DATA.append(p141)

# =========================================================================
# PROBLEM 3 (ID 145): Shape Hierarchy & Canvas Calculation
# =========================================================================
p145 = {
    "id": 145,
    "slug": "oop-shape-hierarchy-polymorphism",
    "title": "Shape Hierarchy & Canvas Calculation",
    "difficulty": "EASY",
    "tags": "OOP, Inheritance, Polymorphism, Abstraction, @Override",
    "description": """### Problem Statement: Shape Hierarchy & Canvas Calculation

Design an extensible geometric shape hierarchy for a graphic canvas using **Abstract Classes**, **Inheritance**, and **Dynamic Polymorphism**.

#### 1. Abstract Base Class: `Shape`
Create `Shape.java` via the `+ Add Java file` tab:
- `private String name;`
- Constructor: `public Shape(String name)`
- Method: `public String getName()`
- Abstract Methods:
  - `public abstract double getArea();`
  - `public abstract double getPerimeter();`

#### 2. Subclass: `Circle`
Create `Circle.java` extending `Shape`:
- `private double radius;`
- Constructor: `public Circle(String name, double radius)`
- `@Override public double getArea()`: $\\pi \\times r^2$ (`Math.PI * radius * radius`).
- `@Override public double getPerimeter()`: $2 \\times \\pi \\times r$ (`2.0 * Math.PI * radius`).

#### 3. Subclass: `Rectangle`
Create `Rectangle.java` extending `Shape`:
- `private double width;`
- `private double height;`
- Constructor: `public Rectangle(String name, double width, double height)`
- `@Override public double getArea()`: $width \\times height$.
- `@Override public double getPerimeter()`: $2 \\times (width + height)$.

#### 4. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: An integer `N` representing the number of shapes.
  - Next `N` lines: `CIRCLE <name> <radius>` or `RECTANGLE <name> <width> <height>`
- **Output Format (stdout)**:
  - For each shape, print: `<name>: Area = <area>, Perimeter = <perimeter>` (2 decimal places).
  - Final line: `Total Area = <totalArea>` (2 decimal places).
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create Shape.java (abstract), Circle.java, Rectangle.java via '+ Add Java file'\n        // TODO: Use polymorphism to process shapes and mandatory @Override annotations\n    }\n}\n"}
    ],
    "solution": [
        {"path": "Shape.java", "content": "public abstract class Shape {\n    private String name;\n\n    public Shape(String name) {\n        this.name = name;\n    }\n\n    public String getName() {\n        return name;\n    }\n\n    public abstract double getArea();\n    public abstract double getPerimeter();\n}\n"},
        {"path": "Circle.java", "content": "public class Circle extends Shape {\n    private double radius;\n\n    public Circle(String name, double radius) {\n        super(name);\n        this.radius = radius;\n    }\n\n    public double getRadius() { return radius; }\n\n    @Override\n    public double getArea() {\n        return Math.PI * radius * radius;\n    }\n\n    @Override\n    public double getPerimeter() {\n        return 2.0 * Math.PI * radius;\n    }\n}\n"},
        {"path": "Rectangle.java", "content": "public class Rectangle extends Shape {\n    private double width;\n    private double height;\n\n    public Rectangle(String name, double width, double height) {\n        super(name);\n        this.width = width;\n        this.height = height;\n    }\n\n    public double getWidth() { return width; }\n    public double getHeight() { return height; }\n\n    @Override\n    public double getArea() {\n        return width * height;\n    }\n\n    @Override\n    public double getPerimeter() {\n        return 2.0 * (width + height);\n    }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextInt()) return;\n        int n = sc.nextInt();\n        List<Shape> shapes = new ArrayList<>();\n\n        for (int i = 0; i < n; i++) {\n            String type = sc.next();\n            if (type.equalsIgnoreCase(\"CIRCLE\")) {\n                String name = sc.next();\n                double r = sc.nextDouble();\n                shapes.add(new Circle(name, r));\n            } else if (type.equalsIgnoreCase(\"RECTANGLE\")) {\n                String name = sc.next();\n                double w = sc.nextDouble();\n                double h = sc.nextDouble();\n                shapes.add(new Rectangle(name, w, h));\n            }\n        }\n\n        double totalArea = 0.0;\n        for (Shape s : shapes) {\n            double a = s.getArea();\n            totalArea += a;\n            System.out.println(String.format(Locale.ROOT, \"%s: Area = %.2f, Perimeter = %.2f\",\n                    s.getName(), a, s.getPerimeter()));\n        }\n        System.out.println(String.format(Locale.ROOT, \"Total Area = %.2f\", totalArea));\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-polymorphism", "ruleName": "Require Polymorphism / Abstraction", "targetClassName": "*", "weight": 2.0},
        {"ruleId": "preset-require-override", "ruleName": "Mandatory @Override Annotation", "targetClassName": "*", "requireOverrideAnnotation": True, "weight": 1.5},
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Circle & Rectangle", "hidden": False, "in": "2\nCIRCLE C1 5.0\nRECTANGLE R1 4.0 6.0\n", "out": "C1: Area = 78.54, Perimeter = 31.42\nR1: Area = 24.00, Perimeter = 20.00\nTotal Area = 102.54\n"},
        {"label": "Sample 2: Multiple Shapes", "hidden": False, "in": "3\nCIRCLE Small 1.0\nRECTANGLE Square 5.0 5.0\nCIRCLE Medium 3.0\n", "out": "Small: Area = 3.14, Perimeter = 6.28\nSquare: Area = 25.00, Perimeter = 20.00\nMedium: Area = 28.27, Perimeter = 18.85\nTotal Area = 56.42\n"},
        {"label": "Hidden 1: Decimal Dimensions Precision", "hidden": True, "in": "2\nCIRCLE CircFloat 2.5\nRECTANGLE RectFloat 3.2 4.5\n", "out": "CircFloat: Area = 19.63, Perimeter = 15.71\nRectFloat: Area = 14.40, Perimeter = 15.40\nTotal Area = 34.03\n"},
        {"label": "Hidden 2: Large Dimensions", "hidden": True, "in": "1\nCIRCLE Big10 10.0\n", "out": "Big10: Area = 314.16, Perimeter = 62.83\nTotal Area = 314.16\n"}
    ]
}
PROBLEMS_DATA.append(p145)

# =========================================================================
# PROBLEM 4 (ID 146): E-Commerce Discount Engine: Composition over Inheritance
# =========================================================================
p146 = {
    "id": 146,
    "slug": "oop-discount-composition",
    "title": "E-Commerce Discount Engine: Composition over Inheritance",
    "difficulty": "MEDIUM",
    "tags": "OOP, Composition over Inheritance, Polymorphism, Strategy",
    "description": """### Problem Statement: E-Commerce Discount Engine - Composition over Inheritance

Apply the fundamental OOP principle **Composition over Inheritance** to design a flexible promotional discount engine for an e-commerce order system.

#### 1. Strategy Interface: `DiscountStrategy`
Create `DiscountStrategy.java` via `+ Add Java file`:
```java
public interface DiscountStrategy {
    double applyDiscount(double originalAmount);
}
```

#### 2. Concrete Strategies
Create the following implementations via `+ Add Java file`:
- `PercentageDiscount implements DiscountStrategy`:
  - `private double percentage;`
  - Constructor: `public PercentageDiscount(double percentage)`
  - `@Override public double applyDiscount(double originalAmount)`:
    - If `percentage >= 100.0`, return `0.0`.
    - If `percentage <= 0.0`, return `originalAmount`.
    - Otherwise returns `originalAmount * (1.0 - percentage / 100.0)`.
- `FixedAmountDiscount implements DiscountStrategy`:
  - `private double discountAmount;`
  - Constructor: `public FixedAmountDiscount(double discountAmount)`
  - `@Override public double applyDiscount(double originalAmount)`: returns `Math.max(0.0, originalAmount - discountAmount)`.
- `NoDiscount implements DiscountStrategy`:
  - `@Override public double applyDiscount(double originalAmount)`: returns `originalAmount`.

#### 3. Domain Entity: `Order`
Create `Order.java` composed with `DiscountStrategy`:
- `private String orderId;`
- `private double subtotal;`
- `private DiscountStrategy discountStrategy;`
- Constructor: `public Order(String orderId, double subtotal, DiscountStrategy discountStrategy)`
- Methods:
  - `public String getOrderId()`
  - `public double getSubtotal()`
  - `public double getFinalTotal()`: delegates calculation to `discountStrategy.applyDiscount(subtotal)`.

#### 4. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: `N` (number of orders)
  - Next `N` lines: `<orderId> <subtotal> <strategyType> [param]`
    (Strategy types: `PERCENTAGE <p>`, `FIXED <amt>`, or `NONE`).
- **Output Format (stdout)**:
  - For each order: `Order <orderId>: Subtotal = <subtotal>, Final = <finalTotal>` (2 decimal places).
  - Final line: `Grand Total = <sumOfFinalTotals>` (2 decimal places).
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create DiscountStrategy.java, PercentageDiscount.java, FixedAmountDiscount.java, NoDiscount.java, Order.java\n        // TODO: Compose Order with DiscountStrategy rather than extending deep hierarchies\n    }\n}\n"}
    ],
    "solution": [
        {"path": "DiscountStrategy.java", "content": "public interface DiscountStrategy {\n    double applyDiscount(double originalAmount);\n}\n"},
        {"path": "PercentageDiscount.java", "content": "public class PercentageDiscount implements DiscountStrategy {\n    private double percentage;\n\n    public PercentageDiscount(double percentage) {\n        this.percentage = percentage;\n    }\n\n    public double getPercentage() { return percentage; }\n\n    @Override\n    public double applyDiscount(double originalAmount) {\n        if (percentage >= 100.0) return 0.0;\n        if (percentage <= 0.0) return originalAmount;\n        return originalAmount * (1.0 - percentage / 100.0);\n    }\n}\n"},
        {"path": "FixedAmountDiscount.java", "content": "public class FixedAmountDiscount implements DiscountStrategy {\n    private double discountAmount;\n\n    public FixedAmountDiscount(double discountAmount) {\n        this.discountAmount = discountAmount;\n    }\n\n    public double getDiscountAmount() { return discountAmount; }\n\n    @Override\n    public double applyDiscount(double originalAmount) {\n        double res = originalAmount - discountAmount;\n        return (res < 0.0) ? 0.0 : res;\n    }\n}\n"},
        {"path": "NoDiscount.java", "content": "public class NoDiscount implements DiscountStrategy {\n    @Override\n    public double applyDiscount(double originalAmount) {\n        return originalAmount;\n    }\n}\n"},
        {"path": "Order.java", "content": "public class Order {\n    private String orderId;\n    private double subtotal;\n    private DiscountStrategy discountStrategy;\n\n    public Order(String orderId, double subtotal, DiscountStrategy discountStrategy) {\n        this.orderId = orderId;\n        this.subtotal = (subtotal < 0) ? 0.0 : subtotal;\n        this.discountStrategy = (discountStrategy != null) ? discountStrategy : new NoDiscount();\n    }\n\n    public String getOrderId() { return orderId; }\n    public double getSubtotal() { return subtotal; }\n    public DiscountStrategy getDiscountStrategy() { return discountStrategy; }\n\n    public double getFinalTotal() {\n        return discountStrategy.applyDiscount(subtotal);\n    }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextInt()) return;\n        int n = sc.nextInt();\n        List<Order> orders = new ArrayList<>();\n\n        for (int i = 0; i < n; i++) {\n            String id = sc.next();\n            double subtotal = sc.nextDouble();\n            String strategyType = sc.next();\n\n            DiscountStrategy strategy;\n            if (strategyType.equalsIgnoreCase(\"PERCENTAGE\")) {\n                double p = sc.nextDouble();\n                strategy = new PercentageDiscount(p);\n            } else if (strategyType.equalsIgnoreCase(\"FIXED\")) {\n                double amt = sc.nextDouble();\n                strategy = new FixedAmountDiscount(amt);\n            } else {\n                strategy = new NoDiscount();\n            }\n            orders.add(new Order(id, subtotal, strategy));\n        }\n\n        double grandTotal = 0.0;\n        for (Order ord : orders) {\n            double fin = ord.getFinalTotal();\n            grandTotal += fin;\n            System.out.println(String.format(Locale.ROOT, \"Order %s: Subtotal = %.2f, Final = %.2f\",\n                    ord.getOrderId(), ord.getSubtotal(), fin));\n        }\n        System.out.println(String.format(Locale.ROOT, \"Grand Total = %.2f\", grandTotal));\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-polymorphism", "ruleName": "Require Polymorphism / Abstraction", "targetClassName": "*", "weight": 2.0},
        {"ruleId": "preset-require-override", "ruleName": "Mandatory @Override Annotation", "targetClassName": "*", "requireOverrideAnnotation": True, "weight": 1.5},
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Mixed Discount Strategies", "hidden": False, "in": "3\nORD-101 200.0 PERCENTAGE 10.0\nORD-102 150.0 FIXED 30.0\nORD-103 80.0 NONE\n", "out": "Order ORD-101: Subtotal = 200.00, Final = 180.00\nOrder ORD-102: Subtotal = 150.00, Final = 120.00\nOrder ORD-103: Subtotal = 80.00, Final = 80.00\nGrand Total = 380.00\n"},
        {"label": "Sample 2: Zero Final Boundary", "hidden": False, "in": "2\nORD-201 50.0 FIXED 70.0\nORD-202 100.0 PERCENTAGE 100.0\n", "out": "Order ORD-201: Subtotal = 50.00, Final = 0.00\nOrder ORD-202: Subtotal = 100.00, Final = 0.00\nGrand Total = 0.00\n"},
        {"label": "Hidden 1: Zero Percentage & High Subtotals", "hidden": True, "in": "3\nORD-301 500.0 PERCENTAGE 25.0\nORD-302 300.0 PERCENTAGE 0.0\nORD-303 450.0 FIXED 50.0\n", "out": "Order ORD-301: Subtotal = 500.00, Final = 375.00\nOrder ORD-302: Subtotal = 300.00, Final = 300.00\nOrder ORD-303: Subtotal = 450.00, Final = 400.00\nGrand Total = 1075.00\n"},
        {"label": "Hidden 2: Decimal Subtotals & Full Coverage", "hidden": True, "in": "4\nORD-401 120.5 PERCENTAGE 15.0\nORD-402 75.0 FIXED 15.5\nORD-403 200.0 FIXED 250.0\nORD-404 99.99 NONE\n", "out": "Order ORD-401: Subtotal = 120.50, Final = 102.43\nOrder ORD-402: Subtotal = 75.00, Final = 59.50\nOrder ORD-403: Subtotal = 200.00, Final = 0.00\nOrder ORD-404: Subtotal = 99.99, Final = 99.99\nGrand Total = 261.92\n"}
    ]
}
PROBLEMS_DATA.append(p146)

# =========================================================================
# PROBLEM 5 (ID 142): Payment Gateway: Polymorphism & Strategy
# =========================================================================
p142 = {
    "id": 142,
    "slug": "oop-payment-polymorphism",
    "title": "Payment Gateway: Polymorphism & Strategy",
    "difficulty": "MEDIUM",
    "tags": "OOP, Polymorphism, Strategy Pattern, Abstraction",
    "description": """### Problem Statement: Payment Gateway - Polymorphism & Strategy

Implement an enterprise payment processing gateway using the **Strategy Design Pattern** and **Interface Polymorphism**.

#### 1. Interface: `PaymentStrategy`
Create `PaymentStrategy.java` via `+ Add Java file`:
```java
public interface PaymentStrategy {
    boolean pay(double amount);
    String getMethodName();
}
```

#### 2. Implementations
Create three payment providers implementing `PaymentStrategy`:
- `CreditCardPayment`:
  - `private String cardNumber;`
  - `private double creditLimit;`
  - Constructor: `public CreditCardPayment(String cardNumber, double creditLimit)`
  - `@Override public boolean pay(double amount)`: If `amount <= creditLimit`, deducts `creditLimit -= amount` and returns `true`. Otherwise returns `false`.
  - `@Override public String getMethodName()`: returns `"CreditCard"`.
- `PayPalPayment`:
  - `private String email;`
  - `private double balance;`
  - Constructor: `public PayPalPayment(String email, double balance)`
  - `@Override public boolean pay(double amount)`: If `amount <= balance`, deducts `balance -= amount` and returns `true`. Otherwise returns `false`.
  - `@Override public String getMethodName()`: returns `"PayPal"`.
- `CryptoPayment`:
  - `private String walletAddress;`
  - `private double gasFee;`
  - `private double cryptoBalance;`
  - Constructor: `public CryptoPayment(String walletAddress, double cryptoBalance, double gasFee)`
  - `@Override public boolean pay(double amount)`: Total required = `amount + gasFee`. If `total <= cryptoBalance`, deducts `cryptoBalance -= total` and returns `true`. Otherwise returns `false`.
  - `@Override public String getMethodName()`: returns `"Crypto"`.

#### 3. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: `N` (number of transactions).
  - Next `N` lines:
    - `CARD <cardNumber> <creditLimit> <amount>`
    - `PAYPAL <email> <balance> <amount>`
    - `CRYPTO <walletAddress> <cryptoBalance> <gasFee> <amount>`
- **Output Format (stdout)**:
  - For each transaction:
    - If successful: `[SUCCESS] <Method>: Paid <amount>`
    - If failed: `[FAILED] <Method>: Insufficient funds for <amount>`
    (Amounts formatted to 2 decimal places).
  - Final line: `Total Processed: <successCount> / <totalCount>`
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create PaymentStrategy.java, CreditCardPayment.java, PayPalPayment.java, CryptoPayment.java via '+ Add Java file'\n        // TODO: Execute payments polymorphically and enforce @Override\n    }\n}\n"}
    ],
    "solution": [
        {"path": "PaymentStrategy.java", "content": "public interface PaymentStrategy {\n    boolean pay(double amount);\n    String getMethodName();\n}\n"},
        {"path": "CreditCardPayment.java", "content": "public class CreditCardPayment implements PaymentStrategy {\n    private String cardNumber;\n    private double creditLimit;\n\n    public CreditCardPayment(String cardNumber, double creditLimit) {\n        this.cardNumber = cardNumber;\n        this.creditLimit = creditLimit;\n    }\n\n    @Override\n    public boolean pay(double amount) {\n        if (amount <= creditLimit) {\n            creditLimit -= amount;\n            return true;\n        }\n        return false;\n    }\n\n    @Override\n    public String getMethodName() { return \"CreditCard\"; }\n}\n"},
        {"path": "PayPalPayment.java", "content": "public class PayPalPayment implements PaymentStrategy {\n    private String email;\n    private double balance;\n\n    public PayPalPayment(String email, double balance) {\n        this.email = email;\n        this.balance = balance;\n    }\n\n    @Override\n    public boolean pay(double amount) {\n        if (amount <= balance) {\n            balance -= amount;\n            return true;\n        }\n        return false;\n    }\n\n    @Override\n    public String getMethodName() { return \"PayPal\"; }\n}\n"},
        {"path": "CryptoPayment.java", "content": "public class CryptoPayment implements PaymentStrategy {\n    private String walletAddress;\n    private double cryptoBalance;\n    private double gasFee;\n\n    public CryptoPayment(String walletAddress, double cryptoBalance, double gasFee) {\n        this.walletAddress = walletAddress;\n        this.cryptoBalance = cryptoBalance;\n        this.gasFee = gasFee;\n    }\n\n    @Override\n    public boolean pay(double amount) {\n        double total = amount + gasFee;\n        if (total <= cryptoBalance) {\n            cryptoBalance -= total;\n            return true;\n        }\n        return false;\n    }\n\n    @Override\n    public String getMethodName() { return \"Crypto\"; }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextInt()) return;\n        int n = sc.nextInt();\n        int success = 0;\n\n        for (int i = 0; i < n; i++) {\n            String type = sc.next();\n            PaymentStrategy strategy = null;\n            double amount = 0.0;\n\n            if (type.equals(\"CARD\")) {\n                String num = sc.next();\n                double limit = sc.nextDouble();\n                amount = sc.nextDouble();\n                strategy = new CreditCardPayment(num, limit);\n            } else if (type.equals(\"PAYPAL\")) {\n                String email = sc.next();\n                double bal = sc.nextDouble();\n                amount = sc.nextDouble();\n                strategy = new PayPalPayment(email, bal);\n            } else if (type.equals(\"CRYPTO\")) {\n                String addr = sc.next();\n                double bal = sc.nextDouble();\n                double gas = sc.nextDouble();\n                amount = sc.nextDouble();\n                strategy = new CryptoPayment(addr, bal, gas);\n            }\n\n            if (strategy != null) {\n                boolean ok = strategy.pay(amount);\n                if (ok) {\n                    success++;\n                    System.out.println(String.format(Locale.ROOT, \"[SUCCESS] %s: Paid %.2f\", strategy.getMethodName(), amount));\n                } else {\n                    System.out.println(String.format(Locale.ROOT, \"[FAILED] %s: Insufficient funds for %.2f\", strategy.getMethodName(), amount));\n                }\n            }\n        }\n        System.out.println(String.format(Locale.ROOT, \"Total Processed: %d / %d\", success, n));\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-polymorphism", "ruleName": "Require Polymorphism / Abstraction", "targetClassName": "*", "weight": 2.0},
        {"ruleId": "preset-require-override", "ruleName": "Mandatory @Override Annotation", "targetClassName": "*", "requireOverrideAnnotation": True, "weight": 1.5},
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Standard Payments", "hidden": False, "in": "2\nCARD 4111222233334444 500.0 120.0\nPAYPAL user@test.com 200.0 50.0\n", "out": "[SUCCESS] CreditCard: Paid 120.00\n[SUCCESS] PayPal: Paid 50.00\nTotal Processed: 2 / 2\n"},
        {"label": "Sample 2: Crypto Gas Fee Exceeded", "hidden": False, "in": "2\nCRYPTO 0xABC123 100.0 15.0 90.0\nPAYPAL test@mail.com 10.0 50.0\n", "out": "[FAILED] Crypto: Insufficient funds for 90.00\n[FAILED] PayPal: Insufficient funds for 50.00\nTotal Processed: 0 / 2\n"},
        {"label": "Hidden 1: Exact Balance Boundary", "hidden": True, "in": "3\nCARD 1111 300.0 300.0\nPAYPAL user@pay.com 150.0 150.0\nCRYPTO 0xWALLET 200.0 20.0 180.0\n", "out": "[SUCCESS] CreditCard: Paid 300.00\n[SUCCESS] PayPal: Paid 150.00\n[SUCCESS] Crypto: Paid 180.00\nTotal Processed: 3 / 3\n"},
        {"label": "Hidden 2: Mixed Complex Sequence", "hidden": True, "in": "4\nCARD 9999 1000.0 800.0\nPAYPAL low@bal.com 50.0 51.0\nCRYPTO 0xHIGH 500.0 5.0 490.0\nCRYPTO 0xFAIL 100.0 10.0 95.0\n", "out": "[SUCCESS] CreditCard: Paid 800.00\n[FAILED] PayPal: Insufficient funds for 51.00\n[SUCCESS] Crypto: Paid 490.00\n[FAILED] Crypto: Insufficient funds for 95.00\nTotal Processed: 2 / 4\n"}
    ]
}
PROBLEMS_DATA.append(p142)

# =========================================================================
# PROBLEM 6 (ID 147): Cloud Storage Service: Interface Abstraction
# =========================================================================
p147 = {
    "id": 147,
    "slug": "oop-cloud-storage-abstraction",
    "title": "Cloud Storage Service: Interface Abstraction",
    "difficulty": "MEDIUM",
    "tags": "OOP, Abstraction, Interface, Dependency Inversion",
    "description": """### Problem Statement: Cloud Storage Service - Interface Abstraction

Design a multi-provider cloud document storage architecture adhering to the **Dependency Inversion Principle**.

#### 1. Interface: `StorageService`
Create `StorageService.java` via `+ Add Java file`:
```java
public interface StorageService {
    boolean upload(String fileId, long sizeBytes);
    boolean delete(String fileId);
    long getUsedCapacity();
    String getProviderName();
}
```

#### 2. Implementations
Create two storage providers:
- `LocalStorageService implements StorageService`:
  - `private long maxCapacity;`
  - `private Map<String, Long> files;` (tracks active fileId to size in bytes).
  - Constructor: `public LocalStorageService(long maxCapacity)`
  - `upload(fileId, sizeBytes)`: If `sizeBytes <= 0`, return false. If `getUsedCapacity() - existingFileSize + sizeBytes > maxCapacity`, reject and return `false`. Otherwise save file size and return `true`.
  - `delete(fileId)`: Removes file if present and returns `true`; if file does not exist, return `false`.
  - `getUsedCapacity()`: Total bytes across all active files.
  - `getProviderName()`: returns `"Local"`.
- `S3StorageService implements StorageService`:
  - `private String bucketName;`
  - `private Map<String, Long> files;`
  - Constructor: `public S3StorageService(String bucketName)`
  - S3 has unlimited storage capacity, but single files exceeding 5,000,000,000 bytes (5 GB) are rejected (`sizeBytes <= 0 || sizeBytes > 5000000000L` returns `false`).
  - `delete(fileId)`: Removes file if present (returns `true`), else returns `false`.
  - `getUsedCapacity()`: Total bytes stored in bucket.
  - `getProviderName()`: returns `"S3"`.

#### 3. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: `LOCAL <maxCapacity>` OR `S3 <bucketName>`
  - Line 2: `N` (number of operations)
  - Next `N` lines: `UPLOAD <fileId> <size>` or `DELETE <fileId>`
- **Output Format (stdout)**:
  - For each operation:
    `[<Provider>] UPLOAD <fileId> (<size> bytes): <SUCCESS|REJECTED>`
    `[<Provider>] DELETE <fileId>: <SUCCESS|NOT_FOUND>`
  - Final line: `Final Used Capacity: <bytes> bytes`
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create StorageService.java, LocalStorageService.java, S3StorageService.java via '+ Add Java file'\n        // TODO: Depend on interface abstraction rather than concrete storage implementations\n    }\n}\n"}
    ],
    "solution": [
        {"path": "StorageService.java", "content": "public interface StorageService {\n    boolean upload(String fileId, long sizeBytes);\n    boolean delete(String fileId);\n    long getUsedCapacity();\n    String getProviderName();\n}\n"},
        {"path": "LocalStorageService.java", "content": "import java.util.*;\n\npublic class LocalStorageService implements StorageService {\n    private long maxCapacity;\n    private Map<String, Long> files;\n\n    public LocalStorageService(long maxCapacity) {\n        this.maxCapacity = maxCapacity;\n        this.files = new LinkedHashMap<>();\n    }\n\n    public long getMaxCapacity() { return maxCapacity; }\n\n    @Override\n    public boolean upload(String fileId, long sizeBytes) {\n        if (sizeBytes <= 0) return false;\n        long currentUsed = getUsedCapacity();\n        long existingSize = files.getOrDefault(fileId, 0L);\n        long needed = currentUsed - existingSize + sizeBytes;\n        if (needed > maxCapacity) return false;\n        files.put(fileId, sizeBytes);\n        return true;\n    }\n\n    @Override\n    public boolean delete(String fileId) {\n        if (!files.containsKey(fileId)) return false;\n        files.remove(fileId);\n        return true;\n    }\n\n    @Override\n    public long getUsedCapacity() {\n        long total = 0L;\n        for (Long s : files.values()) total += s;\n        return total;\n    }\n\n    @Override\n    public String getProviderName() {\n        return \"Local\";\n    }\n}\n"},
        {"path": "S3StorageService.java", "content": "import java.util.*;\n\npublic class S3StorageService implements StorageService {\n    private String bucketName;\n    private Map<String, Long> files;\n    public static final long MAX_SINGLE_FILE_SIZE = 5000000000L;\n\n    public S3StorageService(String bucketName) {\n        this.bucketName = bucketName;\n        this.files = new LinkedHashMap<>();\n    }\n\n    public String getBucketName() { return bucketName; }\n\n    @Override\n    public boolean upload(String fileId, long sizeBytes) {\n        if (sizeBytes <= 0 || sizeBytes > MAX_SINGLE_FILE_SIZE) return false;\n        files.put(fileId, sizeBytes);\n        return true;\n    }\n\n    @Override\n    public boolean delete(String fileId) {\n        if (!files.containsKey(fileId)) return false;\n        files.remove(fileId);\n        return true;\n    }\n\n    @Override\n    public long getUsedCapacity() {\n        long total = 0L;\n        for (Long s : files.values()) total += s;\n        return total;\n    }\n\n    @Override\n    public String getProviderName() {\n        return \"S3\";\n    }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNext()) return;\n        String prov = sc.next();\n        StorageService service;\n\n        if (prov.equalsIgnoreCase(\"LOCAL\")) {\n            long cap = sc.nextLong();\n            service = new LocalStorageService(cap);\n        } else {\n            String bucket = sc.next();\n            service = new S3StorageService(bucket);\n        }\n\n        int n = sc.nextInt();\n        for (int i = 0; i < n; i++) {\n            String op = sc.next();\n            if (op.equalsIgnoreCase(\"UPLOAD\")) {\n                String fId = sc.next();\n                long sz = sc.nextLong();\n                boolean ok = service.upload(fId, sz);\n                System.out.println(String.format(Locale.ROOT, \"[%s] UPLOAD %s (%d bytes): %s\",\n                        service.getProviderName(), fId, sz, ok ? \"SUCCESS\" : \"REJECTED\"));\n            } else if (op.equalsIgnoreCase(\"DELETE\")) {\n                String fId = sc.next();\n                boolean ok = service.delete(fId);\n                System.out.println(String.format(Locale.ROOT, \"[%s] DELETE %s: %s\",\n                        service.getProviderName(), fId, ok ? \"SUCCESS\" : \"NOT_FOUND\"));\n            }\n        }\n        System.out.println(String.format(Locale.ROOT, \"Final Used Capacity: %d bytes\", service.getUsedCapacity()));\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-polymorphism", "ruleName": "Require Polymorphism / Abstraction", "targetClassName": "*", "weight": 2.0},
        {"ruleId": "preset-require-override", "ruleName": "Mandatory @Override Annotation", "targetClassName": "*", "requireOverrideAnnotation": True, "weight": 1.5},
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Local Storage Capacity Overflow", "hidden": False, "in": "LOCAL 1000\n3\nUPLOAD doc1.pdf 400\nUPLOAD doc2.pdf 500\nUPLOAD doc3.pdf 300\n", "out": "[Local] UPLOAD doc1.pdf (400 bytes): SUCCESS\n[Local] UPLOAD doc2.pdf (500 bytes): SUCCESS\n[Local] UPLOAD doc3.pdf (300 bytes): REJECTED\nFinal Used Capacity: 900 bytes\n"},
        {"label": "Sample 2: S3 Upload and Deletion", "hidden": False, "in": "S3 my-production-bucket\n4\nUPLOAD video.mp4 2000000\nUPLOAD image.png 500000\nDELETE video.mp4\nDELETE nonexistent.txt\n", "out": "[S3] UPLOAD video.mp4 (2000000 bytes): SUCCESS\n[S3] UPLOAD image.png (500000 bytes): SUCCESS\n[S3] DELETE video.mp4: SUCCESS\n[S3] DELETE nonexistent.txt: NOT_FOUND\nFinal Used Capacity: 500000 bytes\n"},
        {"label": "Hidden 1: S3 5GB Single File Limit Check", "hidden": True, "in": "S3 data-warehouse\n2\nUPLOAD raw_dataset.tar 6000000000\nUPLOAD small.csv 1024\n", "out": "[S3] UPLOAD raw_dataset.tar (6000000000 bytes): REJECTED\n[S3] UPLOAD small.csv (1024 bytes): SUCCESS\nFinal Used Capacity: 1024 bytes\n"},
        {"label": "Hidden 2: File Overwrite & Delete Workflow", "hidden": True, "in": "LOCAL 5000\n5\nUPLOAD fileA 2000\nUPLOAD fileB 2500\nUPLOAD fileA 1000\nDELETE fileB\nUPLOAD fileC 3000\n", "out": "[Local] UPLOAD fileA (2000 bytes): SUCCESS\n[Local] UPLOAD fileB (2500 bytes): SUCCESS\n[Local] UPLOAD fileA (1000 bytes): SUCCESS\n[Local] DELETE fileB: SUCCESS\n[Local] UPLOAD fileC (3000 bytes): SUCCESS\nFinal Used Capacity: 4000 bytes\n"}
    ]
}
PROBLEMS_DATA.append(p147)

# =========================================================================
# PROBLEM 7 (ID 143): Shopping Cart: Rich Domain Model (Anti-Anemic)
# =========================================================================
p143 = {
    "id": 143,
    "slug": "oop-cart-rich-domain",
    "title": "Shopping Cart: Rich Domain Model (Anti-Anemic)",
    "difficulty": "MEDIUM",
    "tags": "OOP, Rich Domain Model, Anti-Anemic, Domain Invariants",
    "description": """### Problem Statement: Shopping Cart - Rich Domain Model (Anti-Anemic)

Refactor an anemic data model into a **Rich Domain Model** where entities encapsulate their own business rules and guard state invariants.

#### 1. Domain Class: `CartItem`
Create `CartItem.java` via `+ Add Java file`:
- `private String sku;`
- `private String name;`
- `private double unitPrice;`
- `private int quantity;`
- Constructor: `public CartItem(String sku, String name, double unitPrice, int quantity)`
- Methods:
  - `public String getSku()`, `public String getName()`, `public double getUnitPrice()`, `public int getQuantity()`
  - `public double getSubtotal()`: returns `unitPrice * quantity`.
  - `public void addQuantity(int extraQuantity)`: if `extraQuantity > 0`, increases quantity.

#### 2. Root Entity: `ShoppingCart`
Create `ShoppingCart.java` via `+ Add Java file`:
- `private String customerId;`
- `private List<CartItem> items;`
- `private String couponCode;`
- `private double discountPercent;`
- Constructor: `public ShoppingCart(String customerId)`
- Rich Domain Methods:
  - `public void addItem(String sku, String name, double unitPrice, int quantity)`:
    - If an item with the same `sku` already exists, update its quantity using `item.addQuantity(quantity)`.
    - Otherwise append a new `CartItem`.
  - `public boolean applyCoupon(String code)`:
    - If `code` equals `"VIP10"`: set discount to `10.0`%, return `true`.
    - If `code` equals `"SAVE20"`: set discount to `20.0`%, return `true`.
    - Otherwise reject and return `false`.
  - `public double calculateSubtotal()`: sum of all item subtotals.
  - `public double calculateTotal()`: subtotal after applying coupon discount.
  - `public List<CartItem> getItems()`
  - `public String getCustomerId()`
  - `public double getDiscountPercent()`

#### 3. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: `customerId`
  - Line 2: `N` (number of commands)
  - Next `N` lines: `ADD <sku> <name> <price> <qty>` or `COUPON <code>`
- **Output Format (stdout)**:
  - Line 1: `Cart Customer: <customerId>`
  - For each item: `- <name> (x<qty>): <subtotal>` (2 decimal places).
  - `Subtotal: <subtotal>`
  - `Discount: <discountPercent>%`
  - `Total: <total>` (2 decimal places).
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create CartItem.java and ShoppingCart.java via '+ Add Java file'\n        // TODO: Embody business logic inside entities to prevent Anemic Domain Models\n    }\n}\n"}
    ],
    "solution": [
        {"path": "CartItem.java", "content": "public class CartItem {\n    private String sku;\n    private String name;\n    private double unitPrice;\n    private int quantity;\n\n    public CartItem(String sku, String name, double unitPrice, int quantity) {\n        this.sku = sku;\n        this.name = name;\n        this.unitPrice = unitPrice;\n        this.quantity = quantity;\n    }\n\n    public String getSku() { return sku; }\n    public String getName() { return name; }\n    public double getUnitPrice() { return unitPrice;\n    }\n    public int getQuantity() { return quantity; }\n\n    public void addQuantity(int extra) {\n        if (extra > 0) this.quantity += extra;\n    }\n\n    public double getSubtotal() {\n        return unitPrice * quantity;\n    }\n}\n"},
        {"path": "ShoppingCart.java", "content": "import java.util.*;\n\npublic class ShoppingCart {\n    private String customerId;\n    private List<CartItem> items;\n    private String couponCode;\n    private double discountPercent;\n\n    public ShoppingCart(String customerId) {\n        this.customerId = customerId;\n        this.items = new ArrayList<>();\n        this.couponCode = \"\";\n        this.discountPercent = 0.0;\n    }\n\n    public void addItem(String sku, String name, double unitPrice, int quantity) {\n        for (CartItem item : items) {\n            if (item.getSku().equals(sku)) {\n                item.addQuantity(quantity);\n                return;\n            }\n        }\n        items.add(new CartItem(sku, name, unitPrice, quantity));\n    }\n\n    public boolean applyCoupon(String code) {\n        if (code.equals(\"VIP10\")) {\n            this.couponCode = code;\n            this.discountPercent = 10.0;\n            return true;\n        } else if (code.equals(\"SAVE20\")) {\n            this.couponCode = code;\n            this.discountPercent = 20.0;\n            return true;\n        }\n        return false;\n    }\n\n    public double calculateSubtotal() {\n        double sum = 0.0;\n        for (CartItem item : items) sum += item.getSubtotal();\n        return sum;\n    }\n\n    public double calculateTotal() {\n        double sub = calculateSubtotal();\n        return sub * (1.0 - discountPercent / 100.0);\n    }\n\n    public List<CartItem> getItems() { return items; }\n    public String getCustomerId() { return customerId; }\n    public double getDiscountPercent() { return discountPercent; }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextLine()) return;\n        String custId = sc.nextLine().trim();\n        int n = Integer.parseInt(sc.nextLine().trim());\n        ShoppingCart cart = new ShoppingCart(custId);\n\n        for (int i = 0; i < n; i++) {\n            String line = sc.nextLine().trim();\n            String[] parts = line.split(\"\\\\s+\");\n            if (parts[0].equals(\"ADD\")) {\n                String sku = parts[1];\n                String name = parts[2];\n                double price = Double.parseDouble(parts[3]);\n                int qty = Integer.parseInt(parts[4]);\n                cart.addItem(sku, name, price, qty);\n            } else if (parts[0].equals(\"COUPON\")) {\n                cart.applyCoupon(parts[1]);\n            }\n        }\n\n        System.out.println(\"Cart Customer: \" + cart.getCustomerId());\n        for (CartItem item : cart.getItems()) {\n            System.out.println(String.format(Locale.ROOT, \"- %s (x%d): %.2f\", item.getName(), item.getQuantity(), item.getSubtotal()));\n        }\n        System.out.println(String.format(Locale.ROOT, \"Subtotal: %.2f\", cart.calculateSubtotal()));\n        System.out.println(String.format(Locale.ROOT, \"Discount: %.0f%%\", cart.getDiscountPercent()));\n        System.out.println(String.format(Locale.ROOT, \"Total: %.2f\", cart.calculateTotal()));\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-anti-anemic", "ruleName": "Anti-Anemic Domain Model", "targetClassName": "*", "antiAnemic": True, "weight": 2.5},
        {"ruleId": "preset-encapsulation", "ruleName": "Enforce Encapsulation", "targetClassName": "*", "expectedType": "ANY", "enforceEncapsulation": True, "weight": 1.0},
        {"ruleId": "preset-anti-monolithic", "ruleName": "Decouple Logic from main()", "targetClassName": "*", "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Standard Add & Coupon VIP10", "hidden": False, "in": "CUST100\n3\nADD SKU1 Keyboard 50.0 2\nADD SKU2 Mouse 25.0 1\nCOUPON VIP10\n", "out": "Cart Customer: CUST100\n- Keyboard (x2): 100.00\n- Mouse (x1): 25.00\nSubtotal: 125.00\nDiscount: 10%\nTotal: 112.50\n"},
        {"label": "Sample 2: Duplicate SKU Merging & Invalid Coupon", "hidden": False, "in": "CUST200\n4\nADD S1 Pen 2.5 3\nADD S1 Pen 2.5 2\nCOUPON INVALID99\nADD S2 Notebook 5.0 1\n", "out": "Cart Customer: CUST200\n- Pen (x5): 12.50\n- Notebook (x1): 5.00\nSubtotal: 17.50\nDiscount: 0%\nTotal: 17.50\n"},
        {"label": "Hidden 1: Large Discount Coupon SAVE20", "hidden": True, "in": "CUST300\n3\nADD M1 Monitor 300.0 1\nADD M2 Cable 20.0 2\nCOUPON SAVE20\n", "out": "Cart Customer: CUST300\n- Monitor (x1): 300.00\n- Cable (x2): 40.00\nSubtotal: 340.00\nDiscount: 20%\nTotal: 272.00\n"},
        {"label": "Hidden 2: Multiple Sequential Additions", "hidden": True, "in": "CUST400\n4\nADD A1 Book 15.0 1\nADD A2 Headset 80.0 1\nADD A1 Book 15.0 2\nADD A3 Stand 30.0 1\n", "out": "Cart Customer: CUST400\n- Book (x3): 45.00\n- Headset (x1): 80.00\n- Stand (x1): 30.00\nSubtotal: 155.00\nDiscount: 0%\nTotal: 155.00\n"}
    ]
}
PROBLEMS_DATA.append(p143)

# =========================================================================
# PROBLEM 8 (ID 144): Notification Dispatcher: Clean Code & Loose Coupling
# =========================================================================
p144 = {
    "id": 144,
    "slug": "oop-notification-clean-code",
    "title": "Notification Dispatcher: Clean Code & Loose Coupling",
    "difficulty": "HARD",
    "tags": "OOP, Clean Code, Loose Coupling, Low CBO, Polymorphism",
    "description": """### Problem Statement: Notification Dispatcher - Clean Code & Loose Coupling

Build a modular event-driven notification hub adhering to **Clean Code limits** and **Loose Coupling (CBO $\\le$ 6)**.

#### 1. Static Analysis Invariants
- **Clean Code Limits**: Maximum 30 lines per method, maximum 10 methods per class.
- **Loose Coupling**: Coupling Between Objects (CBO) must not exceed 6.
- **Mandatory @Override**: All overridden interface methods must have `@Override`.

#### 2. Channel Interface: `NotificationChannel`
Create `NotificationChannel.java` via `+ Add Java file`:
```java
public interface NotificationChannel {
    boolean send(String recipient, String message);
    String getChannelType();
}
```

#### 3. Implementations
Create three channels:
- `EmailChannel implements NotificationChannel`:
  - Recipient must contain `'@'`.
  - If message length exceeds 50 characters, truncate to first 47 chars + `"..."`.
  - `getChannelType()` returns `"EMAIL"`.
- `SmsChannel implements NotificationChannel`:
  - Recipient must start with `'+'` and length must be $\\ge 10$.
  - If message length exceeds 30 characters, truncate to first 28 chars + `"...""`.
  - `getChannelType()` returns `"SMS"`.
- `PushChannel implements NotificationChannel`:
  - Recipient (device token) must not be empty. Message is not truncated.
  - `getChannelType()` returns `"PUSH"`.

#### 4. Hub Entity: `NotificationDispatcher`
Create `NotificationDispatcher.java` via `+ Add Java file`:
- Manages `List<NotificationChannel> channels`.
- `public void registerChannel(NotificationChannel channel)`
- `public int broadcast(String recipient, String message)`: Dispatches the message through all registered channels that validate the recipient. Returns count of successful transmissions.

#### 5. Main Driver: `Main.java`
- **Input Format (stdin)**:
  - Line 1: `K` (number of channels to register: `EMAIL`, `SMS`, `PUSH`).
  - Next `K` lines: channel names.
  - Next line: `M` (number of broadcast requests).
  - Next `M` lines: `<recipient> <message>`
- **Output Format (stdout)**:
  - For each broadcast request:
    `Broadcast to <recipient>: <successCount>/<registeredCount> delivered`
    Followed by per-channel logs: `  - [<channelType>] Sent: <transmittedMessage>`
""",
    "starters": [
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Create NotificationChannel.java, EmailChannel.java, SmsChannel.java, PushChannel.java, NotificationDispatcher.java\n        // TODO: Enforce CBO <= 6, max 30 lines per method, and mandatory @Override\n    }\n}\n"}
    ],
    "solution": [
        {"path": "NotificationChannel.java", "content": "public interface NotificationChannel {\n    boolean send(String recipient, String message);\n    String getChannelType();\n}\n"},
        {"path": "EmailChannel.java", "content": "public class EmailChannel implements NotificationChannel {\n    @Override\n    public boolean send(String recipient, String message) {\n        if (recipient == null || !recipient.contains(\"@\")) return false;\n        String msg = (message.length() > 50) ? message.substring(0, 47) + \"...\" : message;\n        System.out.println(\"  - [EMAIL] Sent: \" + msg);\n        return true;\n    }\n\n    @Override\n    public String getChannelType() { return \"EMAIL\"; }\n}\n"},
        {"path": "SmsChannel.java", "content": "public class SmsChannel implements NotificationChannel {\n    @Override\n    public boolean send(String recipient, String message) {\n        if (recipient == null || !recipient.startsWith(\"+\") || recipient.length() < 10) return false;\n        String msg = (message.length() > 30) ? message.substring(0, 28) + \"...\" : message;\n        System.out.println(\"  - [SMS] Sent: \" + msg);\n        return true;\n    }\n\n    @Override\n    public String getChannelType() { return \"SMS\"; }\n}\n"},
        {"path": "PushChannel.java", "content": "public class PushChannel implements NotificationChannel {\n    @Override\n    public boolean send(String recipient, String message) {\n        if (recipient == null || recipient.isEmpty()) return false;\n        System.out.println(\"  - [PUSH] Sent: \" + message);\n        return true;\n    }\n\n    @Override\n    public String getChannelType() { return \"PUSH\"; }\n}\n"},
        {"path": "NotificationDispatcher.java", "content": "import java.util.*;\n\npublic class NotificationDispatcher {\n    private List<NotificationChannel> channels = new ArrayList<>();\n\n    public void registerChannel(NotificationChannel ch) {\n        channels.add(ch);\n    }\n\n    public int broadcast(String recipient, String message) {\n        int count = 0;\n        for (NotificationChannel ch : channels) {\n            if (ch.send(recipient, message)) count++;\n        }\n        return count;\n    }\n\n    public int getChannelCount() { return channels.size(); }\n}\n"},
        {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextInt()) return;\n        int k = sc.nextInt();\n        NotificationDispatcher dispatcher = new NotificationDispatcher();\n\n        for (int i = 0; i < k; i++) {\n            String type = sc.next();\n            if (type.equals(\"EMAIL\")) dispatcher.registerChannel(new EmailChannel());\n            else if (type.equals(\"SMS\")) dispatcher.registerChannel(new SmsChannel());\n            else if (type.equals(\"PUSH\")) dispatcher.registerChannel(new PushChannel());\n        }\n\n        int m = sc.nextInt();\n        for (int i = 0; i < m; i++) {\n            String recipient = sc.next();\n            String msg = sc.nextLine().trim();\n            System.out.println(String.format(Locale.ROOT, \"Broadcast to %s:\", recipient));\n            int delivered = dispatcher.broadcast(recipient, msg);\n            System.out.println(String.format(Locale.ROOT, \"Delivered: %d/%d\", delivered, dispatcher.getChannelCount()));\n        }\n    }\n}\n"}
    ],
    "rules": [
        {"ruleId": "preset-clean-code-limits", "ruleName": "Clean Code / Size Limits", "targetClassName": "*", "maxLinesPerMethod": 30, "maxMethodsPerClass": 10, "weight": 1.5},
        {"ruleId": "preset-loose-coupling", "ruleName": "Loose Coupling (CBO Limit)", "targetClassName": "*", "maxCbo": 6, "weight": 1.5},
        {"ruleId": "preset-polymorphism", "ruleName": "Require Polymorphism / Abstraction", "targetClassName": "*", "weight": 1.5},
        {"ruleId": "preset-require-override", "ruleName": "Mandatory @Override Annotation", "targetClassName": "*", "requireOverrideAnnotation": True, "weight": 1.0}
    ],
    "testcases": [
        {"label": "Sample 1: Email and SMS Dispatch", "hidden": False, "in": "2\nEMAIL\nSMS\n2\nalice@domain.com Welcome to our learning platform!\n+1234567890 Your verification code is 4920\n", "out": "Broadcast to alice@domain.com:\n  - [EMAIL] Sent: Welcome to our learning platform!\nDelivered: 1/2\nBroadcast to +1234567890:\n  - [SMS] Sent: Your verification code is 49...\nDelivered: 1/2\n"},
        {"label": "Sample 2: Invalid Recipient Format Check", "hidden": False, "in": "2\nEMAIL\nSMS\n2\ninvalidemail Hello there\n12345 Short phone\n", "out": "Broadcast to invalidemail:\nDelivered: 0/2\nBroadcast to 12345:\nDelivered: 0/2\n"},
        {"label": "Hidden 1: Message Length Boundaries Truncation", "hidden": True, "in": "2\nEMAIL\nSMS\n1\n+19876543210 This message is extraordinarily lengthy and detailed\n", "out": "Broadcast to +19876543210:\n  - [SMS] Sent: This message is extraordinar...\nDelivered: 1/2\n"},
        {"label": "Hidden 2: Multi-channel Push and Full Broadcast", "hidden": True, "in": "3\nEMAIL\nSMS\nPUSH\n1\ndeviceToken_XYZ System upgrade scheduled at midnight\n", "out": "Broadcast to deviceToken_XYZ:\n  - [PUSH] Sent: System upgrade scheduled at midnight\nDelivered: 1/3\n"}
    ]
}
PROBLEMS_DATA.append(p144)

# =========================================================================
# SEED EACH PROBLEM
# =========================================================================
for p in PROBLEMS_DATA:
    pid = p["id"]
    slug = p["slug"]
    title = p["title"]
    diff = p["difficulty"]
    desc = p["description"]
    starters_json = json.dumps(p["starters"])
    solution_json = json.dumps(p["solution"])
    rules_json = json.dumps({"rules": p["rules"]})

    print(f"\n==========================================")
    print(f"Processing Problem {pid}: {title}...")

    # Upsert problem in DRAFT status first
    check_sql = f"SELECT id FROM problems WHERE id = {pid};"
    exists = run_mysql(check_sql).strip()

    if exists:
        update_sql = f"""
        UPDATE problems SET
            slug = '{sql_esc(slug)}',
            title = '{sql_esc(title)}',
            description = '{sql_esc(desc)}',
            difficulty = '{sql_esc(diff)}',
            problem_type = 'OOP_GRINDING',
            status = 'DRAFT',
            starter_templates = '{sql_esc(starters_json)}',
            reference_solution = '{sql_esc(solution_json)}',
            grading_config = '{sql_esc(rules_json)}'
        WHERE id = {pid};
        """
        run_mysql(update_sql)
        print(f"Updated problem {pid} to DRAFT.")
    else:
        insert_sql = f"""
        INSERT INTO problems (id, slug, title, description, difficulty, time_limit_ms, memory_limit_mb, status, created_by, problem_type, starter_templates, reference_solution, grading_config)
        VALUES ({pid}, '{sql_esc(slug)}', '{sql_esc(title)}', '{sql_esc(desc)}', '{sql_esc(diff)}', 3000, 256, 'DRAFT', 1, 'OOP_GRINDING', '{sql_esc(starters_json)}', '{sql_esc(solution_json)}', '{sql_esc(rules_json)}');
        """
        run_mysql(insert_sql)
        print(f"Inserted new problem {pid} as DRAFT.")

    # Delete old test cases
    run_mysql(f"DELETE FROM test_cases WHERE problem_id = {pid};")

    # Upload test cases via API
    for tc in p["testcases"]:
        upload_tc(pid, tc["in"], tc["out"], tc["hidden"], tc["label"])

    # Publish problem
    run_mysql(f"UPDATE problems SET status = 'PUBLISHED' WHERE id = {pid};")
    print(f"Problem {pid} successfully PUBLISHED!")

# =========================================================================
# STUDY PLAN SETUP: "Mastering Java OOP"
# =========================================================================
print("\n==========================================")
print("Setting up Study Plan 'Mastering Java OOP'...")

plan_slug = "mastering-java-oop"
plan_title = "Mastering Java OOP"
plan_desc = "A comprehensive 5-stage mastery curriculum covering Encapsulation, Immutability, Inheritance, Polymorphism, Strategy Pattern, Rich Domain Models, and Clean Code principles."

plan_check = run_mysql(f"SELECT id FROM study_plans WHERE slug = '{plan_slug}';").strip()
if plan_check:
    plan_id = int(plan_check.split("\n")[1].strip())
    run_mysql(f"""
    UPDATE study_plans SET
        title = '{sql_esc(plan_title)}',
        description = '{sql_esc(plan_desc)}',
        status = 'PUBLISHED'
    WHERE id = {plan_id};
    """)
    print(f"Updated existing Study Plan {plan_id}.")
else:
    run_mysql(f"""
    INSERT INTO study_plans (slug, title, description, status, created_by)
    VALUES ('{plan_slug}', '{sql_esc(plan_title)}', '{sql_esc(plan_desc)}', 'PUBLISHED', 1);
    """)
    plan_id_res = run_mysql(f"SELECT id FROM study_plans WHERE slug = '{plan_slug}';").strip()
    plan_id = int(plan_id_res.split("\n")[1].strip())
    print(f"Created new Study Plan {plan_id}.")

# Remove old items for this plan
old_items = run_mysql(f"SELECT id FROM study_plan_items WHERE plan_id = {plan_id};").strip()
if old_items and len(old_items.split("\n")) > 1:
    item_ids = [line.strip() for line in old_items.split("\n")[1:] if line.strip()]
    if item_ids:
        ids_str = ",".join(item_ids)
        run_mysql(f"DELETE FROM study_plan_item_stages WHERE item_id IN ({ids_str});")
        run_mysql(f"DELETE FROM study_plan_items WHERE id IN ({ids_str});")

# Insert 8 items into study_plan_items
plan_items_def = [
    (0, "Stage 1: Employee Payroll (Encapsulation)", 140),
    (1, "Stage 1: Digital Wallet (Strict Immutability)", 141),
    (2, "Stage 2: Shape Hierarchy (Inheritance & Polymorphism)", 145),
    (3, "Stage 2: Discount Engine (Composition over Inheritance)", 146),
    (4, "Stage 3: Payment Gateway (Polymorphism & Strategy)", 142),
    (5, "Stage 3: Cloud Storage (Interface Abstraction)", 147),
    (6, "Stage 4: Shopping Cart (Rich Domain Model)", 143),
    (7, "Stage 5: Notification Dispatcher (Clean Code & Low Coupling)", 144)
]

for sort_order, item_title, prob_id in plan_items_def:
    run_mysql(f"""
    INSERT INTO study_plan_items (plan_id, sort_order, kind, title, description)
    VALUES ({plan_id}, {sort_order}, 'SESSION', '{sql_esc(item_title)}', 'Mastery exercise for {sql_esc(item_title)}');
    """)
    last_item_id = int(run_mysql("SELECT LAST_INSERT_ID();").strip().split("\n")[1].strip())
    run_mysql(f"""
    INSERT INTO study_plan_entries (session_id, sort_order, type, topic_id, problem_id)
    VALUES ({last_item_id}, 0, 'PROBLEM', NULL, {prob_id});
    """)
    print(f"Linked Stage {sort_order + 1}: '{item_title}' -> Problem {prob_id}")

print("\n==========================================")
print("ALL 8 PROBLEMS AND STUDY PLAN SUCCESSFULLY SEEDED AND PUBLISHED!")
