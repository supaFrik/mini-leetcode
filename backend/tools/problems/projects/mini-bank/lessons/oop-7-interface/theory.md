## Interfaces are contracts

```java
public interface Command {
    String name();              // public abstract by default
    String run(String args);
}

public class EchoCommand implements Command {
    @Override public String name() { return "ECHO"; }
    @Override public String run(String args) { return args; }
}
```

Code that uses a `Command` does not need to know `EchoCommand` exists.

## Interface or abstract class?

| | Interface | Abstract class |
|---|-----------|----------------|
| A class can have | many (`implements A, B`) | one (`extends`) |
| State fields | no (constants only) | yes |
| Constructor | no | yes |
| Methods with bodies | `default`, `static`, `private` | any |
| Use for | a capability shared by unrelated classes | shared state and code in one family |

Mini Bank uses both: `Account` is abstract (shared id, owner, balance); a CLI command is an interface.

## default methods

```java
public interface Command {
    String name();
    String run(String args);

    default String usage() {
        return name() + " <args>";
    }
}
```

A `default` method can be added without breaking existing implementations.

## A command table instead of switch

```java
Map<String, Command> commands = new TreeMap<>();
for (Command c : List.of(new EchoCommand(), new AddCommand())) {
    commands.put(c.name(), c);
}

Command c = commands.get(name);
if (c == null) {
    System.out.println("Unknown command: " + name);
} else {
    System.out.println(c.run(args));
}
```

A new command is a new class; the loop does not change.

## Common mistakes

- Implementing an interface method without `public`.
- Declaring variables with the concrete class (`EchoCommand c`) instead of the interface.
- `HashMap` when the command list must be printed in order; use `TreeMap`.
