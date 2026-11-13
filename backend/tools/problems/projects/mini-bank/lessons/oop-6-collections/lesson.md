### Task

- **`Book`**: `private final` `id` and `title`, getters, `toString()` returns `<id>: <title>`.
- **`Library`** keeps books in a private collection (suggested: `LinkedHashMap<String, Book>`); all fields private:
  - `public boolean add(Book book)`: `false` if the id exists.
  - `public Book find(String id)`: `null` if missing.
  - `public boolean remove(String id)`.
  - `public List<Book> all()`: insertion order, not modifiable by callers.
  - `public int size()`.

`Main` reads one command per line until end of input:

| Command | Output |
|---------|--------|
| `ADD <id> <title...>` | `Added <id>` or `Duplicate id <id>` |
| `FIND <id>` | `<id>: <title>` or `Not found: <id>` |
| `REMOVE <id>` | `Removed <id>` or `Not found: <id>` |
| `LIST` | one `<id>: <title>` per line in insertion order; `(empty)` if none |
| `COUNT` | `Books: <n>` |

### Example

Input:

```
ADD B1 Clean Code
ADD B2 Effective Java
ADD B1 Refactoring
FIND B2
REMOVE B9
LIST
COUNT
```

Output:

```
Added B1
Added B2
Duplicate id B1
B2: Effective Java
Not found: B9
B1: Clean Code
B2: Effective Java
Books: 2
```

### Grading

- Structure: `Library` fields are all private; `add(Book)`, `find(String)`, `all()` returning `List`.
- Stdout compared line by line, extra spaces ignored.
