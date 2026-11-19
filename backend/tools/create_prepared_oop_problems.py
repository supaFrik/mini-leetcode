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

# Helper to upload a testcase
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
        print(f"Uploaded testcase '{label}' for problem {prob_id} (hidden={is_hidden})")

# =========================================================================
# PROBLEM 1: oop-wallet-immutability (ID 141)
# =========================================================================
p1_id = 141
p1_slug = "oop-wallet-immutability"
p1_title = "Digital Wallet & Immutable Transactions"
p1_desc = """### Đề Bài: Digital Wallet & Immutable Transactions

Xây dựng hệ sinh thái ví tài chính số đơn giản nhằm luyện tập **Đóng gói dữ liệu (Encapsulation)** và **Tính bất biến (Strict Immutability)**:

#### 1. Lớp `Transaction` (Immutable Value Object)
- Thuộc tính:
  - `private final String id;`
  - `private final double amount;`
  - `private final String type;` (chỉ nhận giá trị `"DEPOSIT"` hoặc `"WITHDRAW"`)
- Constructor: `public Transaction(String id, double amount, String type)`
- Phương thức: `public String getId()`, `public double getAmount()`, `public String getType()`
- **Quy tắc**: Tuyệt đối không có setter, mọi trường đều phải khai báo `final`.

#### 2. Lớp `Wallet`
- Thuộc tính:
  - `private String owner;`
  - `private double balance;`
  - `private List<Transaction> history;`
- Constructor: `public Wallet(String owner)` (khởi tạo `balance = 0.0`, `history` rỗng)
- Phương thức:
  - `public boolean deposit(String txId, double amount)`: nếu `amount > 0`, tăng số dư, thêm một `new Transaction(txId, amount, "DEPOSIT")` vào history và trả về `true`. Ngược lại trả về `false`.
  - `public boolean withdraw(String txId, double amount)`: nếu `amount > 0` và `amount <= balance`, trừ số dư, thêm một `new Transaction(txId, amount, "WITHDRAW")` vào history và trả về `true`. Ngược lại trả về `false`.
  - `public double getBalance()`
  - `public String getOwner()`
  - `public List<Transaction> getHistory()`

#### 3. Lớp `Main`
- **Đầu vào (stdin)**:
  - Dòng 1: Tên chủ ví (`String owner`)
  - Dòng 2: Số nguyên $N$ (số lượng lệnh)
  - $N$ dòng tiếp theo: `DEPOSIT <txId> <amount>` hoặc `WITHDRAW <txId> <amount>`
- **Đầu ra (stdout)**:
  - Dòng 1: `Owner: <owner> | Balance: <balance>` (định dạng 2 chữ số thập phân, vd: `Balance: 150.00`)
  - Dòng 2: `Transactions: <count>`
  - Các dòng sau liệt kê từng giao dịch: `[<type>] ID: <id> | Amount: <amount>` (vd: `[DEPOSIT] ID: TX1 | Amount: 200.00`)
"""

p1_starters = [
    {"path": "Transaction.java", "content": "public class Transaction {\n    // TODO: Define private final fields: id, amount, type\n    // TODO: Constructor and getters (no setters)\n}\n"},
    {"path": "Wallet.java", "content": "import java.util.*;\n\npublic class Wallet {\n    // TODO: Define private fields: owner, balance, history\n    // TODO: Implement deposit, withdraw, getBalance, getHistory\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO: Read input and operate on Wallet\n    }\n}\n"}
]

p1_solution = [
    {"path": "Transaction.java", "content": "public class Transaction {\n    private final String id;\n    private final double amount;\n    private final String type;\n\n    public Transaction(String id, double amount, String type) {\n        this.id = id;\n        this.amount = amount;\n        this.type = type;\n    }\n\n    public String getId() { return id; }\n    public double getAmount() { return amount; }\n    public String getType() { return type; }\n}\n"},
    {"path": "Wallet.java", "content": "import java.util.*;\n\npublic class Wallet {\n    private String owner;\n    private double balance;\n    private List<Transaction> history;\n\n    public Wallet(String owner) {\n        this.owner = owner;\n        this.balance = 0.0;\n        this.history = new ArrayList<>();\n    }\n\n    public boolean deposit(String txId, double amount) {\n        if (amount <= 0) return false;\n        this.balance += amount;\n        this.history.add(new Transaction(txId, amount, \"DEPOSIT\"));\n        return true;\n    }\n\n    public boolean withdraw(String txId, double amount) {\n        if (amount <= 0 || amount > balance) return false;\n        this.balance -= amount;\n        this.history.add(new Transaction(txId, amount, \"WITHDRAW\"));\n        return true;\n    }\n\n    public double getBalance() { return balance; }\n    public String getOwner() { return owner; }\n    public List<Transaction> getHistory() { return history; }\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextLine()) return;\n        String owner = sc.nextLine().trim();\n        int n = Integer.parseInt(sc.nextLine().trim());\n        Wallet wallet = new Wallet(owner);\n\n        for (int i = 0; i < n; i++) {\n            String[] parts = sc.nextLine().trim().split(\"\\\\s+\");\n            String cmd = parts[0];\n            String id = parts[1];\n            double amount = Double.parseDouble(parts[2]);\n            if (cmd.equals(\"DEPOSIT\")) wallet.deposit(id, amount);\n            else if (cmd.equals(\"WITHDRAW\")) wallet.withdraw(id, amount);\n        }\n\n        System.out.println(String.format(Locale.ROOT, \"Owner: %s | Balance: %.2f\", wallet.getOwner(), wallet.getBalance()));\n        List<Transaction> txs = wallet.getHistory();\n        System.out.println(\"Transactions: \" + txs.size());\n        for (Transaction tx : txs) {\n            System.out.println(String.format(Locale.ROOT, \"[%s] ID: %s | Amount: %.2f\", tx.getType(), tx.getId(), tx.getAmount()));\n        }\n    }\n}\n"}
]

p1_grading = {
    "rules": [
        {
            "ruleId": "preset-encapsulation",
            "ruleName": "Enforce Encapsulation",
            "expectedType": "ANY",
            "targetClassName": "*",
            "enforceEncapsulation": True,
            "weight": 1.0
        },
        {
            "ruleId": "preset-strict-immutability",
            "ruleName": "Strict Immutability in Transaction",
            "targetClassName": "Transaction",
            "requireFinalFields": True,
            "weight": 2.0
        },
        {
            "ruleId": "preset-anti-monolithic",
            "ruleName": "Decouple Logic from main()",
            "targetClassName": "*",
            "weight": 1.0
        }
    ]
}

# =========================================================================
# PROBLEM 2: oop-payment-polymorphism (ID 142)
# =========================================================================
p2_id = 142
p2_slug = "oop-payment-polymorphism"
p2_title = "Payment Gateway: Polymorphism & Strategy"
p2_desc = """### Đề Bài: Payment Gateway (Polymorphism & Strategy Pattern)

Xây dựng cổng thanh toán đa kênh áp dụng tính **Đa hình (Polymorphism)**, **Trừu tượng hóa (Abstraction)** và gắn nhãn **`@Override`**:

#### 1. Interface `PaymentMethod`
- `String getMethodName();`
- `double calculateFee(double amount);`
- `boolean processPayment(double amount);`

#### 2. Các lớp thực thi `PaymentMethod`
- **`CreditCardPayment`**:
  - `private String cardNumber;`
  - Constructor: `public CreditCardPayment(String cardNumber)`
  - Phí: $2.5\\%$ giá trị giao dịch $+ 1.00$ USD (`amount * 0.025 + 1.00`).
  - `processPayment`: in `Processing CreditCard [<cardNumber>] for $<amount> (Fee: $<fee>)`, trả về `true`.
- **`MomoPayment`**:
  - `private String phoneNumber;`
  - Constructor: `public MomoPayment(String phoneNumber)`
  - Phí: $1.0\\%$ giá trị giao dịch (`amount * 0.01`).
  - `processPayment`: in `Processing Momo [<phoneNumber>] for $<amount> (Fee: $<fee>)`, trả về `true`.
- **`PaypalPayment`**:
  - `private String email;`
  - Constructor: `public PaypalPayment(String email)`
  - Phí: $3.5\\%$ giá trị giao dịch $+ 0.50$ USD (`amount * 0.035 + 0.50`).
  - `processPayment`: in `Processing Paypal [<email>] for $<amount> (Fee: $<fee>)`, trả về `true`.

#### 3. Lớp `PaymentProcessor`
- Thuộc tính: `private double totalFees;`
- Phương thức:
  - `public void executePayment(PaymentMethod method, double amount)`: gọi `processPayment`, tích lũy phí vào `totalFees`.
  - `public double getTotalFees()`

#### 4. Lớp `Main`
- **Đầu vào (stdin)**:
  - Dòng 1: Số nguyên $N$
  - $N$ dòng tiếp theo: `<TYPE> <IDENTIFIER> <AMOUNT>` (TYPE là `CARD`, `MOMO`, hoặc `PAYPAL`)
- **Đầu ra (stdout)**:
  - Dòng thông tin từng thanh toán được xử lý.
  - Dòng tổng kết: `Total Fees Collected: $<totalFees>` (2 chữ số thập phân).
"""

p2_starters = [
    {"path": "PaymentMethod.java", "content": "public interface PaymentMethod {\n    String getMethodName();\n    double calculateFee(double amount);\n    boolean processPayment(double amount);\n}\n"},
    {"path": "CreditCardPayment.java", "content": "public class CreditCardPayment implements PaymentMethod {\n    // TODO: implement with 2.5% + $1.00 fee\n}\n"},
    {"path": "MomoPayment.java", "content": "public class MomoPayment implements PaymentMethod {\n    // TODO: implement with 1.0% fee\n}\n"},
    {"path": "PaypalPayment.java", "content": "public class PaypalPayment implements PaymentMethod {\n    // TODO: implement with 3.5% + $0.50 fee\n}\n"},
    {"path": "PaymentProcessor.java", "content": "public class PaymentProcessor {\n    // TODO: manage payment execution via polymorphic interface\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // TODO: parse and process payments\n    }\n}\n"}
]

p2_solution = [
    {"path": "PaymentMethod.java", "content": "public interface PaymentMethod {\n    String getMethodName();\n    double calculateFee(double amount);\n    boolean processPayment(double amount);\n}\n"},
    {"path": "CreditCardPayment.java", "content": "import java.util.Locale;\n\npublic class CreditCardPayment implements PaymentMethod {\n    private String cardNumber;\n    public CreditCardPayment(String cardNumber) { this.cardNumber = cardNumber; }\n    @Override public String getMethodName() { return \"CreditCard\"; }\n    @Override public double calculateFee(double amount) { return amount * 0.025 + 1.00; }\n    @Override public boolean processPayment(double amount) {\n        System.out.println(String.format(Locale.ROOT, \"Processing CreditCard [%s] for $%.2f (Fee: $%.2f)\", cardNumber, amount, calculateFee(amount)));\n        return true;\n    }\n}\n"},
    {"path": "MomoPayment.java", "content": "import java.util.Locale;\n\npublic class MomoPayment implements PaymentMethod {\n    private String phoneNumber;\n    public MomoPayment(String phoneNumber) { this.phoneNumber = phoneNumber; }\n    @Override public String getMethodName() { return \"Momo\"; }\n    @Override public double calculateFee(double amount) { return amount * 0.01; }\n    @Override public boolean processPayment(double amount) {\n        System.out.println(String.format(Locale.ROOT, \"Processing Momo [%s] for $%.2f (Fee: $%.2f)\", phoneNumber, amount, calculateFee(amount)));\n        return true;\n    }\n}\n"},
    {"path": "PaypalPayment.java", "content": "import java.util.Locale;\n\npublic class PaypalPayment implements PaymentMethod {\n    private String email;\n    public PaypalPayment(String email) { this.email = email; }\n    @Override public String getMethodName() { return \"Paypal\"; }\n    @Override public double calculateFee(double amount) { return amount * 0.035 + 0.50; }\n    @Override public boolean processPayment(double amount) {\n        System.out.println(String.format(Locale.ROOT, \"Processing Paypal [%s] for $%.2f (Fee: $%.2f)\", email, amount, calculateFee(amount)));\n        return true;\n    }\n}\n"},
    {"path": "PaymentProcessor.java", "content": "public class PaymentProcessor {\n    private double totalFees = 0.0;\n    public void executePayment(PaymentMethod method, double amount) {\n        method.processPayment(amount);\n        totalFees += method.calculateFee(amount);\n    }\n    public double getTotalFees() { return totalFees; }\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextLine()) return;\n        int n = Integer.parseInt(sc.nextLine().trim());\n        PaymentProcessor processor = new PaymentProcessor();\n        for (int i = 0; i < n; i++) {\n            String[] parts = sc.nextLine().trim().split(\"\\\\s+\");\n            String type = parts[0];\n            String id = parts[1];\n            double amount = Double.parseDouble(parts[2]);\n            PaymentMethod method;\n            if (type.equals(\"CARD\")) method = new CreditCardPayment(id);\n            else if (type.equals(\"MOMO\")) method = new MomoPayment(id);\n            else method = new PaypalPayment(id);\n            processor.executePayment(method, amount);\n        }\n        System.out.println(String.format(Locale.ROOT, \"Total Fees Collected: $%.2f\", processor.getTotalFees()));\n    }\n}\n"}
]

p2_grading = {
    "rules": [
        {
            "ruleId": "preset-polymorphism",
            "ruleName": "Require Polymorphism / Abstraction",
            "targetClassName": "*",
            "weight": 2.0
        },
        {
            "ruleId": "preset-require-override",
            "ruleName": "Mandatory @Override Annotation",
            "targetClassName": "*",
            "requireOverrideAnnotation": True,
            "weight": 1.5
        },
        {
            "ruleId": "preset-encapsulation",
            "ruleName": "Enforce Encapsulation",
            "targetClassName": "*",
            "enforceEncapsulation": True,
            "weight": 1.0
        },
        {
            "ruleId": "preset-anti-monolithic",
            "ruleName": "Decouple Logic from main()",
            "targetClassName": "*",
            "weight": 1.0
        }
    ]
}

# =========================================================================
# PROBLEM 3: oop-cart-rich-domain (ID 143)
# =========================================================================
p3_id = 143
p3_slug = "oop-cart-rich-domain"
p3_title = "Shopping Cart: Rich Domain Model (Anti-Anemic)"
p3_desc = """### Đề Bài: Shopping Cart (Rich Domain Model)

Thiết kế giỏ hàng thương mại điện tử theo tư duy **Domain-Driven Design (DDD)**, chống lại mô hình thiếu máu (**Anti-Anemic Domain Model**):

#### 1. Lớp `CartItem`
- Thuộc tính: `private String name;`, `private double unitPrice;`, `private int quantity;`
- Constructor: `public CartItem(String name, double unitPrice, int quantity)`
- **Các phương thức hành vi nghiệp vụ**:
  - `public void adjustQuantity(int delta)`: điều chỉnh số lượng (nếu sau khi cộng $\le 0$ thì giữ mức tối thiểu là 1).
  - `public double calculateSubtotal()`: tính thành tiền $= unitPrice \\times quantity$.
  - `public void applyDiscount(double percent)`: giảm đơn giá theo phần trăm: $unitPrice = unitPrice \\times (1.0 - percent / 100.0)$.
  - Getters: `getName()`, `getUnitPrice()`, `getQuantity()`.

#### 2. Lớp `ShoppingCart`
- Thuộc tính: `private String customerId;`, `private List<CartItem> items;`, `private double discountRate;`
- Constructor: `public ShoppingCart(String customerId)`
- **Các phương thức hành vi nghiệp vụ**:
  - `public void addItem(CartItem item)`: nếu đã có món trùng tên thì gộp số lượng bằng cách gọi `adjustQuantity`; nếu chưa có thì thêm mới.
  - `public boolean removeItem(String itemName)`: xóa món khỏi giỏ hàng.
  - `public void applyCoupon(String code)`:
    - `"SAVE10"`: giảm $10\\%$ (`discountRate = 0.10`).
    - `"SAVE20"`: giảm $20\\%$ (`discountRate = 0.20`).
    - Khác: `discountRate = 0.0`.
  - `public double computeFinalTotal()`: tính tổng thành tiền của các item, sau đó áp dụng giảm giá: $total \\times (1.0 - discountRate)$.
  - `public List<CartItem> getItems()`, `public String getCustomerId()`

#### 3. Lớp `Main`
- **Đầu vào (stdin)**:
  - Dòng 1: `customerId`
  - Dòng 2: $N$
  - $N$ dòng lệnh:
    - `ADD <name> <price> <qty>`
    - `ADJUST <name> <delta>`
    - `REMOVE <name>`
    - `COUPON <code>`
- **Đầu ra (stdout)**:
  - `Customer: <customerId>`
  - Liệt kê từng món: `- <name>: <qty> x $<price> = $<subtotal>`
  - Dòng cuối: `Final Total: $<total>` (định dạng 2 chữ số thập phân).
"""

p3_starters = [
    {"path": "CartItem.java", "content": "public class CartItem {\n    // TODO: Define private fields and behavioral methods (adjustQuantity, calculateSubtotal, applyDiscount)\n}\n"},
    {"path": "ShoppingCart.java", "content": "import java.util.*;\n\npublic class ShoppingCart {\n    // TODO: Define private fields and behavioral methods (addItem, removeItem, applyCoupon, computeFinalTotal)\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // TODO: parse shopping cart commands and output final bill\n    }\n}\n"}
]

p3_solution = [
    {"path": "CartItem.java", "content": "public class CartItem {\n    private String name;\n    private double unitPrice;\n    private int quantity;\n\n    public CartItem(String name, double unitPrice, int quantity) {\n        this.name = name;\n        this.unitPrice = unitPrice;\n        this.quantity = Math.max(1, quantity);\n    }\n\n    public void adjustQuantity(int delta) {\n        this.quantity = Math.max(1, this.quantity + delta);\n    }\n\n    public double calculateSubtotal() {\n        return unitPrice * quantity;\n    }\n\n    public void applyDiscount(double percent) {\n        if (percent > 0 && percent <= 100) {\n            this.unitPrice = this.unitPrice * (1.0 - percent / 100.0);\n        }\n    }\n\n    public String getName() { return name; }\n    public double getUnitPrice() { return unitPrice; }\n    public int getQuantity() { return quantity; }\n}\n"},
    {"path": "ShoppingCart.java", "content": "import java.util.*;\n\npublic class ShoppingCart {\n    private String customerId;\n    private List<CartItem> items;\n    private double discountRate;\n\n    public ShoppingCart(String customerId) {\n        this.customerId = customerId;\n        this.items = new ArrayList<>();\n        this.discountRate = 0.0;\n    }\n\n    public void addItem(CartItem item) {\n        for (CartItem existing : items) {\n            if (existing.getName().equalsIgnoreCase(item.getName())) {\n                existing.adjustQuantity(item.getQuantity());\n                return;\n            }\n        }\n        items.add(item);\n    }\n\n    public boolean removeItem(String itemName) {\n        return items.removeIf(i -> i.getName().equalsIgnoreCase(itemName));\n    }\n\n    public void applyCoupon(String code) {\n        if (\"SAVE10\".equalsIgnoreCase(code)) this.discountRate = 0.10;\n        else if (\"SAVE20\".equalsIgnoreCase(code)) this.discountRate = 0.20;\n        else this.discountRate = 0.0;\n    }\n\n    public double computeFinalTotal() {\n        double total = 0.0;\n        for (CartItem item : items) total += item.calculateSubtotal();\n        return total * (1.0 - discountRate);\n    }\n\n    public List<CartItem> getItems() { return items; }\n    public String getCustomerId() { return customerId; }\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextLine()) return;\n        String customerId = sc.nextLine().trim();\n        int n = Integer.parseInt(sc.nextLine().trim());\n        ShoppingCart cart = new ShoppingCart(customerId);\n\n        for (int i = 0; i < n; i++) {\n            String[] parts = sc.nextLine().trim().split(\"\\\\s+\");\n            String cmd = parts[0];\n            if (cmd.equals(\"ADD\")) {\n                cart.addItem(new CartItem(parts[1], Double.parseDouble(parts[2]), Integer.parseInt(parts[3])));\n            } else if (cmd.equals(\"ADJUST\")) {\n                for (CartItem item : cart.getItems()) {\n                    if (item.getName().equalsIgnoreCase(parts[1])) {\n                        item.adjustQuantity(Integer.parseInt(parts[2]));\n                        break;\n                    }\n                }\n            } else if (cmd.equals(\"REMOVE\")) {\n                cart.removeItem(parts[1]);\n            } else if (cmd.equals(\"COUPON\")) {\n                cart.applyCoupon(parts[1]);\n            }\n        }\n\n        System.out.println(\"Customer: \" + cart.getCustomerId());\n        for (CartItem item : cart.getItems()) {\n            System.out.println(String.format(Locale.ROOT, \"- %s: %d x $%.2f = $%.2f\",\n                    item.getName(), item.getQuantity(), item.getUnitPrice(), item.calculateSubtotal()));\n        }\n        System.out.println(String.format(Locale.ROOT, \"Final Total: $%.2f\", cart.computeFinalTotal()));\n    }\n}\n"}
]

p3_grading = {
    "rules": [
        {
            "ruleId": "preset-anti-anemic",
            "ruleName": "Anti-Anemic Domain Model",
            "targetClassName": "*",
            "antiAnemic": True,
            "weight": 2.5
        },
        {
            "ruleId": "preset-encapsulation",
            "ruleName": "Enforce Encapsulation",
            "targetClassName": "*",
            "enforceEncapsulation": True,
            "weight": 1.0
        },
        {
            "ruleId": "preset-anti-monolithic",
            "ruleName": "Decouple Logic from main()",
            "targetClassName": "*",
            "weight": 1.0
        }
    ]
}

# =========================================================================
# PROBLEM 4: oop-notification-clean-code (ID 144)
# =========================================================================
p4_id = 144
p4_slug = "oop-notification-clean-code"
p4_title = "Notification Dispatcher: Clean Code & Loose Coupling"
p4_desc = """### Đề Bài: Notification Dispatcher (Clean Code & Loose Coupling)

Xây dựng hệ thống gửi thông báo phân tán áp dụng nguyên lý **Khớp nối lỏng (Loose Coupling - CBO $\\le$ 6)** và **Code sạch (Clean Code Limits)**:

#### 1. Interface `NotificationChannel`
- `void sendNotification(String target, String content);`
- `String getChannelType();`

#### 2. Các kênh gửi tin
- **`EmailChannel`** (implements `NotificationChannel`):
  - In ra console: `[EMAIL to <target>] <content>`
  - `getChannelType()`: trả về `"EMAIL"`
- **`SmsChannel`** (implements `NotificationChannel`):
  - In ra console: `[SMS to <target>] <content>` (nếu `content.length() > 30` thì cắt và thêm `...`)
  - `getChannelType()`: trả về `"SMS"`
- **`PushChannel`** (implements `NotificationChannel`):
  - In ra console: `[PUSH to <target>] <content>`
  - `getChannelType()`: trả về `"PUSH"`

#### 3. Lớp `NotificationDispatcher`
- `private Map<String, NotificationChannel> channels;`
- `private int dispatchCount;`
- Constructor: khởi tạo map và `dispatchCount = 0`
- `public void registerChannel(NotificationChannel channel)`: thêm channel vào map theo key `channel.getChannelType()`.
- `public boolean dispatch(String type, String target, String content)`: nếu channel tồn tại, gọi `sendNotification`, tăng `dispatchCount` và trả về `true`. Ngược lại trả về `false`.
- `public int getDispatchCount()`

#### 4. Lớp `Main`
- **Đầu vào (stdin)**:
  - Dòng 1: Số nguyên $N$
  - $N$ dòng tiếp theo: `<TYPE> <TARGET> <CONTENT>`
- **Đầu ra (stdout)**:
  - Các dòng log gửi tin
  - Dòng cuối: `Dispatched Messages: <dispatchCount>`
"""

p4_starters = [
    {"path": "NotificationChannel.java", "content": "public interface NotificationChannel {\n    void sendNotification(String target, String content);\n    String getChannelType();\n}\n"},
    {"path": "EmailChannel.java", "content": "public class EmailChannel implements NotificationChannel {\n    // TODO: implement Email channel\n}\n"},
    {"path": "SmsChannel.java", "content": "public class SmsChannel implements NotificationChannel {\n    // TODO: implement SMS channel (truncate > 30 chars with ...)\n}\n"},
    {"path": "PushChannel.java", "content": "public class PushChannel implements NotificationChannel {\n    // TODO: implement Push channel\n}\n"},
    {"path": "NotificationDispatcher.java", "content": "import java.util.*;\n\npublic class NotificationDispatcher {\n    // TODO: register and dispatch channels\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // TODO: parse commands and dispatch\n    }\n}\n"}
]

p4_solution = [
    {"path": "NotificationChannel.java", "content": "public interface NotificationChannel {\n    void sendNotification(String target, String content);\n    String getChannelType();\n}\n"},
    {"path": "EmailChannel.java", "content": "public class EmailChannel implements NotificationChannel {\n    @Override\n    public void sendNotification(String target, String content) {\n        System.out.println(\"[EMAIL to \" + target + \"] \" + content);\n    }\n    @Override\n    public String getChannelType() { return \"EMAIL\"; }\n}\n"},
    {"path": "SmsChannel.java", "content": "public class SmsChannel implements NotificationChannel {\n    @Override\n    public void sendNotification(String target, String content) {\n        String body = content.length() > 30 ? content.substring(0, 28) + \"...\" : content;\n        System.out.println(\"[SMS to \" + target + \"] \" + body);\n    }\n    @Override\n    public String getChannelType() { return \"SMS\"; }\n}\n"},
    {"path": "PushChannel.java", "content": "public class PushChannel implements NotificationChannel {\n    @Override\n    public void sendNotification(String target, String content) {\n        System.out.println(\"[PUSH to \" + target + \"] \" + content);\n    }\n    @Override\n    public String getChannelType() { return \"PUSH\"; }\n}\n"},
    {"path": "NotificationDispatcher.java", "content": "import java.util.*;\n\npublic class NotificationDispatcher {\n    private Map<String, NotificationChannel> channels = new HashMap<>();\n    private int dispatchCount = 0;\n\n    public void registerChannel(NotificationChannel channel) {\n        channels.put(channel.getChannelType().toUpperCase(), channel);\n    }\n\n    public boolean dispatch(String type, String target, String content) {\n        NotificationChannel ch = channels.get(type.toUpperCase());\n        if (ch == null) return false;\n        ch.sendNotification(target, content);\n        dispatchCount++;\n        return true;\n    }\n\n    public int getDispatchCount() { return dispatchCount; }\n}\n"},
    {"path": "Main.java", "content": "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (!sc.hasNextLine()) return;\n        int n = Integer.parseInt(sc.nextLine().trim());\n        NotificationDispatcher dispatcher = new NotificationDispatcher();\n        dispatcher.registerChannel(new EmailChannel());\n        dispatcher.registerChannel(new SmsChannel());\n        dispatcher.registerChannel(new PushChannel());\n\n        for (int i = 0; i < n; i++) {\n            String line = sc.nextLine().trim();\n            int firstSpace = line.indexOf(' ');\n            int secondSpace = line.indexOf(' ', firstSpace + 1);\n            String type = line.substring(0, firstSpace);\n            String target = line.substring(firstSpace + 1, secondSpace);\n            String content = line.substring(secondSpace + 1);\n            dispatcher.dispatch(type, target, content);\n        }\n        System.out.println(\"Dispatched Messages: \" + dispatcher.getDispatchCount());\n    }\n}\n"}
]

p4_grading = {
    "rules": [
        {
            "ruleId": "preset-clean-code-limits",
            "ruleName": "Clean Code / Size Limits",
            "targetClassName": "*",
            "maxLinesPerMethod": 30,
            "maxMethodsPerClass": 10,
            "weight": 1.5
        },
        {
            "ruleId": "preset-loose-coupling",
            "ruleName": "Loose Coupling (CBO Limit)",
            "targetClassName": "*",
            "maxCbo": 6,
            "weight": 1.5
        },
        {
            "ruleId": "preset-polymorphism",
            "ruleName": "Require Polymorphism / Abstraction",
            "targetClassName": "*",
            "weight": 1.5
        },
        {
            "ruleId": "preset-require-override",
            "ruleName": "Mandatory @Override Annotation",
            "targetClassName": "*",
            "requireOverrideAnnotation": True,
            "weight": 1.0
        }
    ]
}

# =========================================================================
# Execution: Upsert into MySQL
# =========================================================================
problems_data = [
    (p1_id, p1_slug, p1_title, "EASY", p1_desc, p1_starters, p1_solution, p1_grading, ["OOP", "Encapsulation", "Immutability"]),
    (p2_id, p2_slug, p2_title, "MEDIUM", p2_desc, p2_starters, p2_solution, p2_grading, ["OOP", "Polymorphism", "Abstraction", "Strategy"]),
    (p3_id, p3_slug, p3_title, "MEDIUM", p3_desc, p3_starters, p3_solution, p3_grading, ["OOP", "Anti-Anemic", "Domain Model"]),
    (p4_id, p4_slug, p4_title, "MEDIUM", p4_desc, p4_starters, p4_solution, p4_grading, ["OOP", "Clean Code", "Loose Coupling"])
]

admin_user_id = 83  # live_admin

for pid, slug, title, diff, desc, starters, sol, grading, tags in problems_data:
    starters_json = json.dumps(starters, ensure_ascii=False)
    sol_json = json.dumps(sol, ensure_ascii=False)
    grading_json = json.dumps(grading, ensure_ascii=False)
    
    sql = f"""
    INSERT INTO problems (id, slug, title, difficulty, description, time_limit_ms, memory_limit_mb, status, created_by, problem_type, starter_templates, reference_solution, grading_config)
    VALUES ({pid}, '{sql_esc(slug)}', '{sql_esc(title)}', '{diff}', '{sql_esc(desc)}', 3000, 256, 'DRAFT', {admin_user_id}, 'OOP_GRINDING', '{sql_esc(starters_json)}', '{sql_esc(sol_json)}', '{sql_esc(grading_json)}')
    ON DUPLICATE KEY UPDATE
        title = '{sql_esc(title)}',
        difficulty = '{diff}',
        description = '{sql_esc(desc)}',
        status = 'DRAFT',
        problem_type = 'OOP_GRINDING',
        starter_templates = '{sql_esc(starters_json)}',
        reference_solution = '{sql_esc(sol_json)}',
        grading_config = '{sql_esc(grading_json)}';
    """
    run_mysql(sql)
    
    # Insert tags
    run_mysql(f"DELETE FROM problem_tags WHERE problem_id = {pid};")
    for idx, tag in enumerate(tags):
        run_mysql(f"INSERT INTO problem_tags (problem_id, sort_order, tag) VALUES ({pid}, {idx}, '{sql_esc(tag)}');")
    
    print(f"Problem {pid} ({slug}) upserted successfully.")

# =========================================================================
# Seed Testcases for all 4 problems
# =========================================================================

# Problem 1 Testcases
tc1 = [
    # Sample 1
    ("Alice\n3\nDEPOSIT TX1 200.0\nWITHDRAW TX2 50.0\nDEPOSIT TX3 100.0\n",
     "Owner: Alice | Balance: 250.00\nTransactions: 3\n[DEPOSIT] ID: TX1 | Amount: 200.00\n[WITHDRAW] ID: TX2 | Amount: 50.00\n[DEPOSIT] ID: TX3 | Amount: 100.00\n",
     False, "Sample 1 · Regular Deposits and Withdrawals"),
    # Sample 2
    ("Bob\n2\nWITHDRAW W1 50.0\nDEPOSIT D1 80.0\n",
     "Owner: Bob | Balance: 80.00\nTransactions: 1\n[DEPOSIT] ID: D1 | Amount: 80.00\n",
     False, "Sample 2 · Reject Overdraft Withdrawal"),
    # Hidden 1
    ("Charlie\n4\nDEPOSIT C1 1000.0\nWITHDRAW C2 250.5\nWITHDRAW C3 749.5\nWITHDRAW C4 10.0\n",
     "Owner: Charlie | Balance: 0.00\nTransactions: 3\n[DEPOSIT] ID: C1 | Amount: 1000.00\n[WITHDRAW] ID: C2 | Amount: 250.50\n[WITHDRAW] ID: C3 | Amount: 749.50\n",
     True, "Hidden 1 · Exact Zero Balance & Rejection"),
    # Hidden 2
    ("ZeroTest\n2\nDEPOSIT Z1 -50.0\nDEPOSIT Z2 0.0\n",
     "Owner: ZeroTest | Balance: 0.00\nTransactions: 0\n",
     True, "Hidden 2 · Invalid Negative Amounts")
]

# Problem 2 Testcases
tc2 = [
    # Sample 1
    ("3\nCARD 4111-2222 100.0\nMOMO 0988776655 50.0\nPAYPAL user@test.com 200.0\n",
     "Processing CreditCard [4111-2222] for $100.00 (Fee: $3.50)\nProcessing Momo [0988776655] for $50.00 (Fee: $0.50)\nProcessing Paypal [user@test.com] for $200.00 (Fee: $7.50)\nTotal Fees Collected: $11.50\n",
     False, "Sample 1 · Multi-channel payments"),
    # Sample 2
    ("2\nCARD 5500-1234 20.0\nMOMO 0912345678 10.0\n",
     "Processing CreditCard [5500-1234] for $20.00 (Fee: $1.50)\nProcessing Momo [0912345678] for $10.00 (Fee: $0.10)\nTotal Fees Collected: $1.60\n",
     False, "Sample 2 · Card and Momo fees"),
    # Hidden 1
    ("4\nPAYPAL ceo@corp.com 500.0\nCARD 4000-0000 1000.0\nMOMO 0900000000 300.0\nPAYPAL dev@mini.com 50.0\n",
     "Processing Paypal [ceo@corp.com] for $500.00 (Fee: $18.00)\nProcessing CreditCard [4000-0000] for $1000.00 (Fee: $26.00)\nProcessing Momo [0900000000] for $300.00 (Fee: $3.00)\nProcessing Paypal [dev@mini.com] for $50.00 (Fee: $2.25)\nTotal Fees Collected: $49.25\n",
     True, "Hidden 1 · Bulk mixed transactions")
]

# Problem 3 Testcases
tc3 = [
    # Sample 1
    ("CUST_001\n4\nADD Apple 2.5 4\nADD Milk 3.0 2\nCOUPON SAVE10\nADJUST Apple -1\n",
     "Customer: CUST_001\n- Apple: 3 x $2.50 = $7.50\n- Milk: 2 x $3.00 = $6.00\nFinal Total: $12.15\n",
     False, "Sample 1 · Add, adjust and 10% coupon"),
    # Sample 2
    ("CUST_002\n4\nADD Keyboard 50.0 1\nADD Mouse 20.0 2\nREMOVE Mouse\nCOUPON SAVE20\n",
     "Customer: CUST_002\n- Keyboard: 1 x $50.00 = $50.00\nFinal Total: $40.00\n",
     False, "Sample 2 · Remove item and 20% coupon"),
    # Hidden 1
    ("VIP_888\n5\nADD Laptop 1000.0 1\nADD Laptop 1000.0 1\nADD Headphone 150.0 1\nCOUPON SAVE20\nADJUST Headphone 1\n",
     "Customer: VIP_888\n- Laptop: 2 x $1000.00 = $2000.00\n- Headphone: 2 x $150.00 = $300.00\nFinal Total: $1840.00\n",
     True, "Hidden 1 · Quantity merge and multi adjustment")
]

# Problem 4 Testcases
tc4 = [
    # Sample 1
    ("3\nEMAIL alice@example.com Welcome to MiniLeetCode\nSMS 0912345678 Your OTP code is 492102\nPUSH device_token_abc Daily challenge is live\n",
     "[EMAIL to alice@example.com] Welcome to MiniLeetCode\n[SMS to 0912345678] Your OTP code is 492102\n[PUSH to device_token_abc] Daily challenge is live\nDispatched Messages: 3\n",
     False, "Sample 1 · Regular multi-channel dispatch"),
    # Sample 2
    ("1\nSMS 0987654321 This message is extraordinarily long and definitely exceeds thirty characters limit\n",
     "[SMS to 0987654321] This message is extraordinar...\nDispatched Messages: 1\n",
     False, "Sample 2 · SMS length truncation"),
    # Hidden 1
    ("4\nEMAIL support@bank.com System maintenance notice\nUNKNOWN target message\nPUSH token_xyz New badge unlocked\nPUSH token_xyz Streak maintained\n",
     "[EMAIL to support@bank.com] System maintenance notice\n[PUSH to token_xyz] New badge unlocked\n[PUSH to token_xyz] Streak maintained\nDispatched Messages: 3\n",
     True, "Hidden 1 · Ignore unknown channel")
]

all_tcs = [
    (p1_id, tc1),
    (p2_id, tc2),
    (p3_id, tc3),
    (p4_id, tc4)
]

for pid, cases in all_tcs:
    print(f"\n--- Seeding testcases for Problem {pid} ---")
    for inp, out, is_hid, lbl in cases:
        upload_tc(pid, inp, out, is_hid, lbl)

# Publish all problems after testcases are seeded
for pid, _, _, _, _, _, _, _, _ in problems_data:
    run_mysql(f"UPDATE problems SET status = 'PUBLISHED' WHERE id = {pid};")
    print(f"Problem {pid} published successfully.")

print("\nAll 4 OOP Case Problems created, seeded and published successfully!")
