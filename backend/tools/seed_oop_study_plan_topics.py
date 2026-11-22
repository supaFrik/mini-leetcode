import json
import subprocess

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

TOPICS = [
    {
        "id": 10,
        "slug": "topic-encapsulation-immutability",
        "title": "OOP Encapsulation & Strict Immutability",
        "summary": "Master data encapsulation, private state invariants, and immutable value objects.",
        "est_minutes": 15,
        "steps": [
            {
                "sort_order": 0,
                "type": "THEORY",
                "title": "Encapsulation & Immutability Guide",
                "content": {
                    "markdown": """## 1. True Encapsulation vs Getters/Setters

Encapsulation is not simply making fields `private` and generating getters and setters. True encapsulation means:
- **Information Hiding**: The internal representation of an object is hidden from callers.
- **Invariant Protection**: An object is solely responsible for maintaining valid state. Callers cannot bypass validity checks.

```java
// BAD: Anemic & Leaky
public class Employee {
    public double salary; // Anyone can set negative salary!
}

// GOOD: Invariant Enforced
public class Employee {
    private double salary;

    public Employee(double salary) {
        this.salary = Math.max(0.0, salary); // Invariant protected
    }

    public void raiseSalary(double pct) {
        if (pct > 0) this.salary *= (1.0 + pct / 100.0);
    }
}
```

## 2. Value Objects & Strict Immutability

A **Value Object** represents a concept whose identity is based on its values (e.g., `Money`, `Coordinates`, `Transaction`).

**Core Rules for Strict Immutability:**
1. All fields are declared `private final`.
2. No setter methods exist.
3. The object cannot be mutated after construction.
4. Naturally thread-safe and safe from unintended side-effects.
"""
                },
                "answer": None
            },
            {
                "sort_order": 1,
                "type": "CHOICE",
                "title": "Value Object Finality",
                "content": {
                    "prompt": "Why should fields in an immutable Value Object (such as `Transaction`) be declared `private final`?",
                    "options": [
                        {"id": "A", "text": "To guarantee that references cannot be reassigned after construction, ensuring thread-safety and audit integrity."},
                        {"id": "B", "text": "To allow other classes to modify the fields directly without getters."},
                        {"id": "C", "text": "To remove the need for constructors in the class."}
                    ],
                    "multiple": False,
                    "hint": "Think about what happens when state cannot be reassigned after creation."
                },
                "answer": {
                    "correct": ["A"],
                    "explanation": "Marking fields final guarantees that their values are set once in the constructor and cannot be altered, ensuring strict immutability."
                }
            }
        ]
    },
    {
        "id": 11,
        "slug": "topic-inheritance-composition",
        "title": "Inheritance vs Composition in Java",
        "summary": "Design polymorphic class hierarchies and apply the principle of Composition over Inheritance.",
        "est_minutes": 15,
        "steps": [
            {
                "sort_order": 0,
                "type": "THEORY",
                "title": "Inheritance & Composition Guide",
                "content": {
                    "markdown": """## 1. Inheritance and the IS-A Relationship

Use inheritance (`extends`) only when an authentic `IS-A` relationship exists:
- `Circle` IS-A `Shape`.
- `Rectangle` IS-A `Shape`.

Both inherit abstract contracts (`getArea()`, `getPerimeter()`), and concrete subclasses must annotate overridden methods with `@Override` for compiler safety.

## 2. The Perils of Class Explosion

If you use inheritance for every behavioral variation (e.g., `Order` -> `DiscountedOrder` -> `PercentageDiscountedOrder` -> `FixedDiscountedOrder`), class counts explode exponentially.

## 3. Composition over Inheritance (HAS-A)

Instead of rigid hierarchies, compose behavior dynamically:
- `Order` HAS-A `DiscountStrategy`.
- Strategies can be swapped at runtime without modifying the `Order` class.
"""
                },
                "answer": None
            },
            {
                "sort_order": 1,
                "type": "CHOICE",
                "title": "Composition Principle",
                "content": {
                    "prompt": "What is the primary architectural benefit of 'Composition over Inheritance' when designing discount engines?",
                    "options": [
                        {"id": "A", "text": "It prevents class explosion by allowing behaviors to be injected and swapped dynamically at runtime."},
                        {"id": "B", "text": "It allows child classes to access private fields of parent classes directly."},
                        {"id": "C", "text": "It forces all classes to inherit from a single abstract base class."}
                    ],
                    "multiple": False,
                    "hint": "Consider flexibility and avoiding dozens of subclasses."
                },
                "answer": {
                    "correct": ["A"],
                    "explanation": "Composition allows strategies to be configured or swapped at runtime without creating a rigid subclass hierarchy."
                }
            }
        ]
    },
    {
        "id": 12,
        "slug": "topic-polymorphism-strategy",
        "title": "Interface Abstraction & Strategy Pattern",
        "summary": "Decouple callers from concrete implementations using Java interfaces and the Strategy Pattern.",
        "est_minutes": 20,
        "steps": [
            {
                "sort_order": 0,
                "type": "THEORY",
                "title": "Strategy Pattern & Polymorphism",
                "content": {
                    "markdown": """## 1. Program to an Interface, Not an Implementation

The **Dependency Inversion Principle** (SOLID) dictates that high-level modules should depend upon abstractions rather than concrete classes.

```java
// Client depends solely on the interface
public interface PaymentStrategy {
    boolean pay(double amount);
    String getMethodName();
}
```

## 2. Pluggable Providers

Whether processing Credit Cards, PayPal, Crypto, or writing to Local Disk vs Amazon S3:
- The caller code (`PaymentProcessor`, `StorageManager`) remains untouched.
- Adding a new provider requires zero modifications to existing client code (Open/Closed Principle).
"""
                },
                "answer": None
            },
            {
                "sort_order": 1,
                "type": "CHOICE",
                "title": "Interface Decoupling",
                "content": {
                    "prompt": "Which design pattern enables interchangeable algorithms or service providers behind a unified contract without altering caller logic?",
                    "options": [
                        {"id": "A", "text": "Strategy Pattern"},
                        {"id": "B", "text": "Singleton Pattern"},
                        {"id": "C", "text": "Prototype Pattern"}
                    ],
                    "multiple": False,
                    "hint": "It defines a family of algorithms and makes them interchangeable."
                },
                "answer": {
                    "correct": ["A"],
                    "explanation": "The Strategy Pattern encapsulates interchangeable algorithms behind a common interface, decoupling clients from concrete execution."
                }
            }
        ]
    },
    {
        "id": 13,
        "slug": "topic-rich-domain-models",
        "title": "Rich Domain Models vs Anemic Domain Models",
        "summary": "Protect business invariants inside entities and avoid anti-patterns of getter/setter anemic data holders.",
        "est_minutes": 20,
        "steps": [
            {
                "sort_order": 0,
                "type": "THEORY",
                "title": "Rich Domain Architecture",
                "content": {
                    "markdown": """## 1. The Anemic Domain Model Anti-Pattern

An Anemic Domain Model occurs when domain entities are reduced to dumb data structures with only getters and setters, while all business logic is scattered in procedural scripts or `Main.java`.

```java
// BAD: Anemic cart - caller mutates state arbitrarily
cart.setTotal(cart.getSubtotal() - couponDiscount);
```

## 2. The Rich Domain Model Solution

In a Rich Domain Model:
- Entities combine both **state** and **behavior**.
- Invariants are protected within the entity boundary.
- `ShoppingCart.addItem(sku, name, price, qty)` validates input, merges duplicate SKUs, and updates state internally.
"""
                },
                "answer": None
            },
            {
                "sort_order": 1,
                "type": "CHOICE",
                "title": "Rich Domain Characteristics",
                "content": {
                    "prompt": "Which of the following is a signature characteristic of a Rich Domain Model?",
                    "options": [
                        {"id": "A", "text": "Entities encapsulate both state and the business methods that mutate and guard that state."},
                        {"id": "B", "text": "Entities have only public fields and no constructors."},
                        {"id": "C", "text": "All business logic is written in static methods inside Main.java."}
                    ],
                    "multiple": False,
                    "hint": "Entities are not passive data bags; they own their operations."
                },
                "answer": {
                    "correct": ["A"],
                    "explanation": "In a Rich Domain Model, domain entities contain business logic and state validation methods to protect domain invariants."
                }
            }
        ]
    },
    {
        "id": 14,
        "slug": "topic-clean-code-decoupling",
        "title": "Clean Code & Loose Coupling (CBO Limits)",
        "summary": "Enforce low coupling (CBO limits), small methods, Single Responsibility, and clean interface contracts.",
        "est_minutes": 20,
        "steps": [
            {
                "sort_order": 0,
                "type": "THEORY",
                "title": "Clean Code & Low CBO Architecture",
                "content": {
                    "markdown": """## 1. Coupling Between Objects (CBO)

**CBO** measures the number of other classes to which a class is coupled.
- **High CBO (> 6)**: Fragile classes, high maintenance cost, hard to unit test.
- **Low CBO (<= 6)**: Highly modular, easy to refactor, decoupled via interfaces.

## 2. Method Size & Single Responsibility

- Limit methods to a maximum of 30 lines.
- Limit classes to cohesive sets of methods (<= 10 methods per class).
- Separate presentation / CLI parsing from business dispatching.
"""
                },
                "answer": None
            },
            {
                "sort_order": 1,
                "type": "CHOICE",
                "title": "Coupling Metric",
                "content": {
                    "prompt": "Why is keeping Coupling Between Objects (CBO <= 6) a critical metric in enterprise Java systems?",
                    "options": [
                        {"id": "A", "text": "It keeps classes modular and minimizes cascading ripple effects when dependencies are modified."},
                        {"id": "B", "text": "It eliminates the need for unit testing."},
                        {"id": "C", "text": "It forces all code into a single file."}
                    ],
                    "multiple": False,
                    "hint": "Fewer dependencies mean fewer breaking changes."
                },
                "answer": {
                    "correct": ["A"],
                    "explanation": "Low CBO keeps classes loosely coupled, meaning changes to one component will not cascade and break multiple unrelated classes."
                }
            }
        ]
    }
]

# 1. Upsert Topics and Topic Steps
for t in TOPICS:
    tid = t["id"]
    slug = t["slug"]
    title = t["title"]
    summary = t["summary"]
    est = t["est_minutes"]

    run_mysql(f"""
    INSERT INTO topics (id, slug, title, summary, est_minutes, status, created_by)
    VALUES ({tid}, '{sql_esc(slug)}', '{sql_esc(title)}', '{sql_esc(summary)}', {est}, 'PUBLISHED', 1)
    ON DUPLICATE KEY UPDATE
        title = '{sql_esc(title)}',
        summary = '{sql_esc(summary)}',
        est_minutes = {est},
        status = 'PUBLISHED';
    """)
    print(f"[*] Topic {tid} '{title}' saved.")

    run_mysql(f"DELETE FROM topic_steps WHERE topic_id = {tid};")
    for s in t["steps"]:
        so = s["sort_order"]
        stype = s["type"]
        stitle = s["title"]
        c_json = json.dumps(s["content"])
        a_json = json.dumps(s["answer"]) if s["answer"] else "NULL"

        if a_json == "NULL":
            run_mysql(f"""
            INSERT INTO topic_steps (topic_id, sort_order, type, title, content, answer)
            VALUES ({tid}, {so}, '{stype}', '{sql_esc(stitle)}', '{sql_esc(c_json)}', NULL);
            """)
        else:
            run_mysql(f"""
            INSERT INTO topic_steps (topic_id, sort_order, type, title, content, answer)
            VALUES ({tid}, {so}, '{stype}', '{sql_esc(stitle)}', '{sql_esc(c_json)}', '{sql_esc(a_json)}');
            """)
        print(f"  [+] Step {so}: {stype} '{stitle}'")

# 2. Link Topics to Study Plan sessions in study_plan_entries
# Items in Study Plan 8:
# Item 27, 28 -> Stage 1 -> Topic 10 (Encapsulation & Immutability)
# Item 29, 30 -> Stage 2 -> Topic 11 (Inheritance vs Composition)
# Item 31, 32 -> Stage 3 -> Topic 12 (Polymorphism & Strategy)
# Item 33     -> Stage 4 -> Topic 13 (Rich Domain Models)
# Item 34     -> Stage 5 -> Topic 14 (Clean Code & Low Coupling)

STAGE_TOPIC_LINKS = [
    (27, 0, 0, 10),
    (28, 0, 0, 10),
    (29, 0, 0, 11),
    (30, 0, 0, 11),
    (31, 0, 0, 12),
    (32, 0, 0, 12),
    (33, 0, 0, 13),
    (34, 0, 0, 14),
]

run_mysql("DELETE FROM study_plan_entries WHERE type = 'LESSON' AND session_id BETWEEN 27 AND 34;")
for item_id, sort_order, stage_no, topic_id in STAGE_TOPIC_LINKS:
    # sort_order -1: lesson đứng trước đề (đề ở sort_order 0).
    run_mysql(f"""
    INSERT INTO study_plan_entries (session_id, sort_order, type, topic_id, problem_id)
    VALUES ({item_id}, {sort_order - 1}, 'LESSON', {topic_id}, NULL);
    """)
    print(f"Linked item {item_id} (stage {stage_no}) -> Topic {topic_id}")

print("\nALL 5 THEORY TOPICS AND STUDY PLAN STAGE LINKS SUCCESSFULLY SEEDED!")
