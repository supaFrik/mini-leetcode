import requests

login = requests.post('http://localhost:8088/api/v1/auth/login', json={'usernameOrEmail': 'live_admin', 'password': 'Password123!'}).json()
tok = login.get('accessToken') or login.get('token')
headers = {'Authorization': 'Bearer ' + tok, 'Content-Type': 'application/json'}

print("Admin authenticated.")

DESCRIPTIONS = {}

# =========================================================================
# 140: Employee Payroll: Encapsulation & Domain Invariants
# =========================================================================
DESCRIPTIONS[140] = {
    "title": "Employee Payroll: Encapsulation & Domain Invariants",
    "difficulty": "EASY",
    "description": """### Problem Statement: Employee Payroll - Encapsulation & Domain Invariants

Design and implement an enterprise payroll processing domain model that strictly enforces **Data Encapsulation** and **Domain Invariant Protection**.

---

### 1. Domain Class: `Employee`
Create `Employee.java` using the **`+ Add Java file`** tab:

- **Private State Fields**:
  - `private String id;`
  - `private String name;`
  - `private double baseSalary;`
  - `private double taxRate;`

- **Constructor**:
  - `public Employee(String id, String name, double baseSalary, double taxRate)`
  - **Invariant Validations**:
    - If `baseSalary < 0`, clamp it to `0.0`.
    - If `taxRate < 0.0`, clamp to `0.0`. If `taxRate > 1.0`, clamp to `1.0`.

- **Public Business Methods**:
  - `public String getId()`
  - `public String getName()`
  - `public double getBaseSalary()`
  - `public double getTaxRate()`
  - `public double calculateTax()`: Returns `baseSalary * taxRate`.
  - `public double calculateNetSalary()`: Returns `baseSalary - calculateTax()`.
  - `public void raiseSalary(double percentage)`: If `percentage > 0`, increases base salary by that percentage (`baseSalary = baseSalary * (1.0 + percentage / 100.0)`). If non-positive, make no change.

> **OOP Enforcement**: All state fields must remain strictly `private`. Public setters that bypass invariants are forbidden.

---

### 2. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: An integer `N` representing the number of employees.
  - Next `N` lines: `id name baseSalary taxRate raisePercentage`
- **Output Format (stdout)**:
  - For each employee, output:
    `Employee[<id>] <name> | Base: <baseSalary> | Tax: <tax> | Net: <netSalary>`
    *(Format all monetary values to 2 decimal places using `Locale.ROOT`)*.
  - Final summary line:
    `Total Net Payroll: <sumOfNetSalaries>` *(2 decimal places)*.

---

### Example

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
"""
}

# =========================================================================
# 141: Digital Wallet & Immutable Transactions
# =========================================================================
DESCRIPTIONS[141] = {
    "title": "Digital Wallet & Immutable Transactions",
    "difficulty": "EASY",
    "description": """### Problem Statement: Digital Wallet & Immutable Transactions

Build a financial digital wallet ecosystem enforcing **Data Encapsulation** and **Strict Immutability** for audit safety.

---

### 1. Immutable Value Object: `Transaction`
Create `Transaction.java` using the **`+ Add Java file`** tab:

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

> **Strict Immutability Invariant**: Every field in `Transaction` must be declared `final`. Setter methods are strictly prohibited.

---

### 2. Domain Entity: `Wallet`
Create `Wallet.java` using the **`+ Add Java file`** tab:

- **Private Fields**:
  - `private String owner;`
  - `private double balance;`
  - `private List<Transaction> history;`

- **Constructor**:
  - `public Wallet(String owner)`: Initializes `balance = 0.0` and empty `history`.

- **Domain Operations**:
  - `public boolean deposit(String txId, double amount)`: If `amount > 0`, increases balance, appends `new Transaction(txId, amount, "DEPOSIT")` to history, and returns `true`. Otherwise returns `false`.
  - `public boolean withdraw(String txId, double amount)`: If `amount > 0` and `amount <= balance`, deducts balance, appends `new Transaction(txId, amount, "WITHDRAW")` to history, and returns `true`. Otherwise returns `false`.
  - `public double getBalance()`
  - `public String getOwner()`
  - `public List<Transaction> getHistory()`

---

### 3. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: `owner` (String)
  - Line 2: `N` (number of commands)
  - Next `N` lines: `DEPOSIT <txId> <amount>` or `WITHDRAW <txId> <amount>`
- **Output Format (stdout)**:
  - Line 1: `Owner: <owner> | Balance: <balance>` *(2 decimal places)*.
  - Line 2: `Transactions: <count>`
  - Next lines: `[<type>] ID: <id> | Amount: <amount>` *(2 decimal places)*.

---

### Example

**Input:**
```
Alex
3
DEPOSIT TX1 200.0
WITHDRAW TX2 50.0
DEPOSIT TX3 100.0
```

**Output:**
```
Owner: Alex | Balance: 250.00
Transactions: 3
[DEPOSIT] ID: TX1 | Amount: 200.00
[WITHDRAW] ID: TX2 | Amount: 50.00
[DEPOSIT] ID: TX3 | Amount: 100.00
```
"""
}

# =========================================================================
# 145: Shape Hierarchy & Canvas Calculation
# =========================================================================
DESCRIPTIONS[145] = {
    "title": "Shape Hierarchy & Canvas Calculation",
    "difficulty": "EASY",
    "description": """### Problem Statement: Shape Hierarchy & Canvas Calculation

Design an extensible geometric shape hierarchy for a graphic canvas using **Abstract Classes**, **Inheritance**, and **Dynamic Polymorphism**.

---

### 1. Abstract Base Class: `Shape`
Create `Shape.java` using the **`+ Add Java file`** tab:

```java
public abstract class Shape {
    private String name;

    public Shape(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract double getArea();
    public abstract double getPerimeter();
}
```

---

### 2. Concrete Subclasses

#### `Circle extends Shape`
Create `Circle.java`:
- `private double radius;`
- Constructor: `public Circle(String name, double radius)`
- `@Override public double getArea()`: Returns $\\pi \\times r^2$ (`Math.PI * radius * radius`).
- `@Override public double getPerimeter()`: Returns $2 \\times \\pi \\times r$ (`2.0 * Math.PI * radius`).

#### `Rectangle extends Shape`
Create `Rectangle.java`:
- `private double width;`
- `private double height;`
- Constructor: `public Rectangle(String name, double width, double height)`
- `@Override public double getArea()`: Returns $width \\times height$.
- `@Override public double getPerimeter()`: Returns $2 \\times (width + height)$.

> **OOP Enforcement**: Subclasses must use `@Override` on all implemented methods. Do not write business calculations inside `Main.java`.

---

### 3. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: An integer `N` representing the number of shapes.
  - Next `N` lines: `CIRCLE <name> <radius>` or `RECTANGLE <name> <width> <height>`
- **Output Format (stdout)**:
  - For each shape, print: `<name>: Area = <area>, Perimeter = <perimeter>` *(2 decimal places)*.
  - Final line: `Total Area = <totalArea>` *(2 decimal places)*.

---

### Example

**Input:**
```
2
CIRCLE C1 5.0
RECTANGLE R1 4.0 6.0
```

**Output:**
```
C1: Area = 78.54, Perimeter = 31.42
R1: Area = 24.00, Perimeter = 20.00
Total Area = 102.54
```
"""
}

# =========================================================================
# 146: E-Commerce Discount Engine: Composition over Inheritance
# =========================================================================
DESCRIPTIONS[146] = {
    "title": "E-Commerce Discount Engine: Composition over Inheritance",
    "difficulty": "MEDIUM",
    "description": """### Problem Statement: E-Commerce Discount Engine - Composition over Inheritance

Apply the fundamental OOP principle **Composition over Inheritance** to design a flexible promotional discount engine for an e-commerce platform without deep class hierarchies.

---

### 1. Strategy Interface: `DiscountStrategy`
Create `DiscountStrategy.java` using the **`+ Add Java file`** tab:

```java
public interface DiscountStrategy {
    double applyDiscount(double originalAmount);
}
```

---

### 2. Strategy Implementations
Create three implementations of `DiscountStrategy`:

- **`PercentageDiscount implements DiscountStrategy`**:
  - `private double percentage;`
  - Constructor: `public PercentageDiscount(double percentage)`
  - `@Override public double applyDiscount(double originalAmount)`:
    - If `percentage >= 100.0`, return `0.0`.
    - If `percentage <= 0.0`, return `originalAmount`.
    - Otherwise return `originalAmount * (1.0 - percentage / 100.0)`.

- **`FixedAmountDiscount implements DiscountStrategy`**:
  - `private double discountAmount;`
  - Constructor: `public FixedAmountDiscount(double discountAmount)`
  - `@Override public double applyDiscount(double originalAmount)`: Returns `Math.max(0.0, originalAmount - discountAmount)`.

- **`NoDiscount implements DiscountStrategy`**:
  - `@Override public double applyDiscount(double originalAmount)`: Returns `originalAmount`.

---

### 3. Domain Entity: `Order`
Create `Order.java` composed with `DiscountStrategy`:

- `private String orderId;`
- `private double subtotal;`
- `private DiscountStrategy discountStrategy;`
- Constructor: `public Order(String orderId, double subtotal, DiscountStrategy discountStrategy)`
  - If `subtotal < 0`, clamp to `0.0`.
  - If `discountStrategy == null`, default to `new NoDiscount()`.
- Methods:
  - `public String getOrderId()`
  - `public double getSubtotal()`
  - `public double getFinalTotal()`: Delegates calculation to `discountStrategy.applyDiscount(subtotal)`.

---

### 4. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: `N` (number of orders).
  - Next `N` lines: `<orderId> <subtotal> <strategyType> [param]`
    *(Strategy types: `PERCENTAGE <percentage>`, `FIXED <amount>`, or `NONE`)*.
- **Output Format (stdout)**:
  - For each order: `Order <orderId>: Subtotal = <subtotal>, Final = <finalTotal>` *(2 decimal places)*.
  - Final line: `Grand Total = <sumOfFinalTotals>` *(2 decimal places)*.

---

### Example

**Input:**
```
3
ORD-101 200.0 PERCENTAGE 10.0
ORD-102 150.0 FIXED 30.0
ORD-103 80.0 NONE
```

**Output:**
```
Order ORD-101: Subtotal = 200.00, Final = 180.00
Order ORD-102: Subtotal = 150.00, Final = 120.00
Order ORD-103: Subtotal = 80.00, Final = 80.00
Grand Total = 380.00
```
"""
}

# =========================================================================
# 142: Payment Gateway: Polymorphism & Strategy
# =========================================================================
DESCRIPTIONS[142] = {
    "title": "Payment Gateway: Polymorphism & Strategy",
    "difficulty": "MEDIUM",
    "description": """### Problem Statement: Payment Gateway - Polymorphism & Strategy

Implement an enterprise payment processing gateway using the **Strategy Design Pattern** and **Interface Polymorphism**.

---

### 1. Interface: `PaymentStrategy`
Create `PaymentStrategy.java` using the **`+ Add Java file`** tab:

```java
public interface PaymentStrategy {
    boolean pay(double amount);
    String getMethodName();
}
```

---

### 2. Implementations
Create three payment providers implementing `PaymentStrategy`:

#### `CreditCardPayment implements PaymentStrategy`:
- `private String cardNumber;`
- `private double creditLimit;`
- Constructor: `public CreditCardPayment(String cardNumber, double creditLimit)`
- `@Override public boolean pay(double amount)`: If `amount <= creditLimit`, deducts `creditLimit -= amount` and returns `true`. Otherwise returns `false`.
- `@Override public String getMethodName()`: Returns `"CreditCard"`.

#### `PayPalPayment implements PaymentStrategy`:
- `private String email;`
- `private double balance;`
- Constructor: `public PayPalPayment(String email, double balance)`
- `@Override public boolean pay(double amount)`: If `amount <= balance`, deducts `balance -= amount` and returns `true`. Otherwise returns `false`.
- `@Override public String getMethodName()`: Returns `"PayPal"`.

#### `CryptoPayment implements PaymentStrategy`:
- `private String walletAddress;`
- `private double gasFee;`
- `private double cryptoBalance;`
- Constructor: `public CryptoPayment(String walletAddress, double cryptoBalance, double gasFee)`
- `@Override public boolean pay(double amount)`: Total required = `amount + gasFee`. If `total <= cryptoBalance`, deducts `cryptoBalance -= total` and returns `true`. Otherwise returns `false`.
- `@Override public String getMethodName()`: Returns `"Crypto"`.

---

### 3. Main Driver: `Main.java`

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
    *(Amounts formatted to 2 decimal places)*.
  - Final line: `Total Processed: <successCount> / <totalCount>`

---

### Example

**Input:**
```
2
CARD 4111222233334444 500.0 120.0
PAYPAL user@test.com 200.0 50.0
```

**Output:**
```
[SUCCESS] CreditCard: Paid 120.00
[SUCCESS] PayPal: Paid 50.00
Total Processed: 2 / 2
```
"""
}

# =========================================================================
# 147: Cloud Storage Service: Interface Abstraction
# =========================================================================
DESCRIPTIONS[147] = {
    "title": "Cloud Storage Service: Interface Abstraction",
    "difficulty": "MEDIUM",
    "description": """### Problem Statement: Cloud Storage Service - Interface Abstraction

Design a multi-provider cloud document storage architecture adhering to the **Dependency Inversion Principle**.

---

### 1. Interface: `StorageService`
Create `StorageService.java` using the **`+ Add Java file`** tab:

```java
public interface StorageService {
    boolean upload(String fileId, long sizeBytes);
    boolean delete(String fileId);
    long getUsedCapacity();
    String getProviderName();
}
```

---

### 2. Implementations
Create two storage providers implementing `StorageService`:

#### `LocalStorageService implements StorageService`:
- `private long maxCapacity;`
- `private Map<String, Long> files;` (tracks active fileId to size in bytes).
- Constructor: `public LocalStorageService(long maxCapacity)`
- `upload(fileId, sizeBytes)`: If `sizeBytes <= 0`, return `false`. If `getUsedCapacity() - existingFileSize + sizeBytes > maxCapacity`, reject and return `false`. Otherwise save file size and return `true`.
- `delete(fileId)`: Removes file if present and returns `true`; if file does not exist, return `false`.
- `getUsedCapacity()`: Total bytes across all active files.
- `getProviderName()`: Returns `"Local"`.

#### `S3StorageService implements StorageService`:
- `private String bucketName;`
- `private Map<String, Long> files;`
- Constructor: `public S3StorageService(String bucketName)`
- S3 has unlimited storage capacity, but single files exceeding 5,000,000,000 bytes (5 GB) are rejected (`sizeBytes <= 0 || sizeBytes > 5000000000L` returns `false`).
- `delete(fileId)`: Removes file if present (returns `true`), else returns `false`.
- `getUsedCapacity()`: Total bytes stored in bucket.
- `getProviderName()`: Returns `"S3"`.

---

### 3. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: `LOCAL <maxCapacity>` OR `S3 <bucketName>`
  - Line 2: `N` (number of operations)
  - Next `N` lines: `UPLOAD <fileId> <size>` or `DELETE <fileId>`
- **Output Format (stdout)**:
  - For each operation:
    - `[<Provider>] UPLOAD <fileId> (<size> bytes): <SUCCESS|REJECTED>`
    - `[<Provider>] DELETE <fileId>: <SUCCESS|NOT_FOUND>`
  - Final line: `Final Used Capacity: <bytes> bytes`

---

### Example

**Input:**
```
LOCAL 1000
3
UPLOAD doc1.pdf 400
UPLOAD doc2.pdf 500
UPLOAD doc3.pdf 300
```

**Output:**
```
[Local] UPLOAD doc1.pdf (400 bytes): SUCCESS
[Local] UPLOAD doc2.pdf (500 bytes): SUCCESS
[Local] UPLOAD doc3.pdf (300 bytes): REJECTED
Final Used Capacity: 900 bytes
```
"""
}

# =========================================================================
# 143: Shopping Cart: Rich Domain Model (Anti-Anemic)
# =========================================================================
DESCRIPTIONS[143] = {
    "title": "Shopping Cart: Rich Domain Model (Anti-Anemic)",
    "difficulty": "MEDIUM",
    "description": """### Problem Statement: Shopping Cart - Rich Domain Model (Anti-Anemic)

Refactor an anemic data model into a **Rich Domain Model** where entities encapsulate their own business rules and guard state invariants.

---

### 1. Domain Class: `CartItem`
Create `CartItem.java` using the **`+ Add Java file`** tab:

- `private String sku;`
- `private String name;`
- `private double unitPrice;`
- `private int quantity;`
- Constructor: `public CartItem(String sku, String name, double unitPrice, int quantity)`
- Public Methods:
  - `public String getSku()`
  - `public String getName()`
  - `public double getUnitPrice()`
  - `public int getQuantity()`
  - `public double getSubtotal()`: Returns `unitPrice * quantity`.
  - `public void addQuantity(int extraQuantity)`: If `extraQuantity > 0`, increases quantity.

---

### 2. Root Entity: `ShoppingCart`
Create `ShoppingCart.java` using the **`+ Add Java file`** tab:

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
    - If `code` equals `"VIP10"`, set discount to `10.0`% and return `true`.
    - If `code` equals `"SAVE20"`, set discount to `20.0`% and return `true`.
    - Otherwise reject coupon and return `false`.
  - `public double calculateSubtotal()`: Sum of all item subtotals.
  - `public double calculateTotal()`: Subtotal after applying coupon discount.
  - `public List<CartItem> getItems()`
  - `public String getCustomerId()`
  - `public double getDiscountPercent()`

> **Anti-Anemic Invariant**: Do not place cart calculation logic in `Main.java`. Entities must compute their own state.

---

### 3. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: `customerId`
  - Line 2: `N` (number of commands)
  - Next `N` lines: `ADD <sku> <name> <price> <qty>` or `COUPON <code>`
- **Output Format (stdout)**:
  - Line 1: `Cart Customer: <customerId>`
  - For each item: `- <name> (x<qty>): <subtotal>` *(2 decimal places)*.
  - `Subtotal: <subtotal>`
  - `Discount: <discountPercent>%`
  - `Total: <total>` *(2 decimal places)*.

---

### Example

**Input:**
```
CUST100
3
ADD SKU1 Keyboard 50.0 2
ADD SKU2 Mouse 25.0 1
COUPON VIP10
```

**Output:**
```
Cart Customer: CUST100
- Keyboard (x2): 100.00
- Mouse (x1): 25.00
Subtotal: 125.00
Discount: 10%
Total: 112.50
```
"""
}

# =========================================================================
# 144: Notification Dispatcher: Clean Code & Loose Coupling
# =========================================================================
DESCRIPTIONS[144] = {
    "title": "Notification Dispatcher: Clean Code & Loose Coupling",
    "difficulty": "HARD",
    "description": """### Problem Statement: Notification Dispatcher - Clean Code & Loose Coupling

Build a modular event-driven notification hub adhering to **Clean Code limits** and **Loose Coupling (CBO $\\\\le$ 6)**.

---

### 1. Static Analysis Invariants
- **Clean Code Limits**: Maximum 30 lines per method, maximum 10 methods per class.
- **Loose Coupling**: Coupling Between Objects (CBO) must not exceed 6.
- **Mandatory @Override**: All overridden interface methods must have `@Override`.

---

### 2. Channel Interface: `NotificationChannel`
Create `NotificationChannel.java` using the **`+ Add Java file`** tab:

```java
public interface NotificationChannel {
    boolean send(String recipient, String message);
    String getChannelType();
}
```

---

### 3. Concrete Implementations
Create three channels:

- **`EmailChannel implements NotificationChannel`**:
  - Recipient must contain `'@'`.
  - If message length exceeds 50 characters, truncate to first 47 chars + `"..."`.
  - `getChannelType()` returns `"EMAIL"`.

- **`SmsChannel implements NotificationChannel`**:
  - Recipient must start with `'+'` and length must be $\\\\ge 10$.
  - If message length exceeds 30 characters, truncate to first 28 chars + `"..."`.
  - `getChannelType()` returns `"SMS"`.

- **`PushChannel implements NotificationChannel`**:
  - Recipient (device token) must not be empty. Message is not truncated.
  - `getChannelType()` returns `"PUSH"`.

---

### 4. Hub Entity: `NotificationDispatcher`
Create `NotificationDispatcher.java` using the **`+ Add Java file`** tab:

- Manages `List<NotificationChannel> channels`.
- `public void registerChannel(NotificationChannel channel)`
- `public int broadcast(String recipient, String message)`: Dispatches the message through all registered channels that validate the recipient. Returns count of successful transmissions.

---

### 5. Main Driver: `Main.java`

- **Input Format (stdin)**:
  - Line 1: `K` (number of channels to register: `EMAIL`, `SMS`, `PUSH`).
  - Next `K` lines: channel names.
  - Next line: `M` (number of broadcast requests).
  - Next `M` lines: `<recipient> <message>`
- **Output Format (stdout)**:
  - For each broadcast request:
    `Broadcast to <recipient>:`
    Followed by per-channel logs: `  - [<channelType>] Sent: <transmittedMessage>`
    And count: `Delivered: <successCount>/<registeredCount>`

---

### Example

**Input:**
```
2
EMAIL
SMS
2
alice@domain.com Welcome to our learning platform!
+1234567890 Your verification code is 4920
```

**Output:**
```
Broadcast to alice@domain.com:
  - [EMAIL] Sent: Welcome to our learning platform!
Delivered: 1/2
Broadcast to +1234567890:
  - [SMS] Sent: Your verification code is 49...
Delivered: 1/2
```
"""
}

# =========================================================================
# UPDATE ALL PROBLEMS VIA REST API
# =========================================================================
for pid, info in DESCRIPTIONS.items():
    body = {
        "title": info["title"],
        "description": info["description"],
        "difficulty": info["difficulty"],
        "timeLimitMs": 3000,
        "memoryLimitMb": 256,
        "problemType": "OOP_GRINDING"
    }
    res = requests.put(f"http://localhost:8088/api/v1/problems/{pid}", headers=headers, json=body)
    if res.status_code == 200:
        print(f"[*] Problem {pid} ({info['title']}) description updated successfully!")
    else:
        print(f"[!] Failed to update problem {pid}: {res.status_code} {res.text}")

print("\nALL 8 OOP PROBLEM DESCRIPTIONS REPAIRED AND UPDATED SUCCESSFULLY!")
