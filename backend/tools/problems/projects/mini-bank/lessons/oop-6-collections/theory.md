## List, Set, Map

| Interface | Properties | Common implementation |
|-----------|------------|-----------------------|
| `List<E>` | ordered, duplicates allowed, index access | `ArrayList` |
| `Set<E>` | no duplicates | `HashSet`, `LinkedHashSet` (insertion order), `TreeSet` (sorted) |
| `Map<K, V>` | key → value, unique keys | `HashMap`, `LinkedHashMap` (insertion order), `TreeMap` |

Declare with the interface, create with an implementation:

```java
List<Account> accounts = new ArrayList<>();
Map<Integer, Account> byId = new LinkedHashMap<>();
```

## Generics

```java
List<Account> accounts = new ArrayList<>();
accounts.add(new SavingsAccount(...));   // ok
accounts.add("hello");                   // compile error
Account first = accounts.get(0);         // no cast
```

Collections hold objects: use `Integer`, `Long` for numbers.

## Lookup by key

```java
for (Account a : accounts) if (a.getId() == id) return a;   // scans everything

Account a = byId.get(id);        // null if missing
byId.containsKey(id);
byId.putIfAbsent(id, account);   // adds only if missing
byId.remove(id);                 // returns the removed value or null
```

`LinkedHashMap` gives fast lookup and keeps insertion order.

## Do not leak the internal list

```java
public List<Account> getAccounts() {
    return accounts;   // the caller can clear() it
}
```

`private` hides the field, not the object it points to. Return a read-only view or copy:

```java
return List.copyOf(accounts);
return Collections.unmodifiableList(accounts);
```

## Common mistakes

- Returning the internal collection from a getter.
- `HashMap` when insertion order matters.
- Removing inside a for-each loop (`ConcurrentModificationException`); use `removeIf`.
- Not checking for `null` after `map.get`.
