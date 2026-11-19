import requests

login = requests.post('http://localhost:8088/api/v1/auth/login', json={'usernameOrEmail': 'live_admin', 'password': 'Password123!'}).json()
tok = login.get('accessToken') or login.get('token')
headers = {'Authorization': 'Bearer ' + tok, 'Content-Type': 'application/json'}

test_desc = """### Problem Statement: Cloud Storage Service - Interface Abstraction

Design a multi-provider cloud document storage architecture adhering to the **Dependency Inversion Principle**.

---

#### 1. Interface: `StorageService`
Create `StorageService.java` via the `+ Add Java file` tab:

```java
public interface StorageService {
    boolean upload(String fileId, long sizeBytes);
    boolean delete(String fileId);
    long getUsedCapacity();
    String getProviderName();
}
```

---

#### 2. Implementations
Create two storage providers implementing `StorageService`:

##### `LocalStorageService implements StorageService`:
- `private long maxCapacity;`
- `private Map<String, Long> files;` (tracks active fileId to size in bytes).
- Constructor: `public LocalStorageService(long maxCapacity)`
- `upload(fileId, sizeBytes)`: If `sizeBytes <= 0`, return `false`. If `getUsedCapacity() - existingFileSize + sizeBytes > maxCapacity`, reject and return `false`. Otherwise save file size and return `true`.
- `delete(fileId)`: Removes file if present and returns `true`; if file does not exist, return `false`.
- `getUsedCapacity()`: Total bytes across all active files.
- `getProviderName()`: returns `"Local"`.

##### `S3StorageService implements StorageService`:
- `private String bucketName;`
- `private Map<String, Long> files;`
- Constructor: `public S3StorageService(String bucketName)`
- S3 has unlimited storage capacity, but single files exceeding 5,000,000,000 bytes (5 GB) are rejected (`sizeBytes <= 0 || sizeBytes > 5000000000L` returns `false`).
- `delete(fileId)`: Removes file if present (returns `true`), else returns `false`.
- `getUsedCapacity()`: Total bytes stored in bucket.
- `getProviderName()`: returns `"S3"`.

---

#### 3. Main Driver: `Main.java`
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

#### Example
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

req_body = {
    'title': 'Cloud Storage Service: Interface Abstraction',
    'description': test_desc,
    'difficulty': 'MEDIUM',
    'timeLimitMs': 3000,
    'memoryLimitMb': 256,
    'problemType': 'OOP_GRINDING'
}

res = requests.put('http://localhost:8088/api/v1/problems/147', headers=headers, json=req_body)
print('Update status:', res.status_code)
if res.status_code != 200:
    print(res.text)
else:
    print('Updated problem 147 successfully via REST API!')
