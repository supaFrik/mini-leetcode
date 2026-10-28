# Ephemeral Docker Sandbox Runners

This directory contains the Dockerfiles and execution documentation for the **Mini LeetCode Sandbox Runner Images** (Phase 2, Role 2).

The runner images provide an isolated, ephemeral, and non-root execution sandbox for evaluating untrusted user submissions in **Java 17** and **Python 3**.

---

## 1. Runner Images Specification

| Language | Base Image | Runner Image Tag | Security User | Working Directory |
|---|---|---|---|---|
| **Java 17** | `eclipse-temurin:17-alpine` | `mini-leetcode-runner-java:17` | `judgeuser` (UID: 1000, GID: 1000) | `/workspace` |
| **Python 3** | `python:3.10-alpine` | `mini-leetcode-runner-python:3` | `judgeuser` (UID: 1000, GID: 1000) | `/workspace` |

### Security Model: Non-Root Execution
Untrusted user code is executed strictly under a non-root user (`judgeuser`, UID 1000, GID 1000). The user is provisioned during image build with no sudo or elevated permissions. The `/workspace` directory is prepared with ownership `judgeuser:judgeuser` (permissions `0755`) to facilitate read-only volume mounts during evaluation.

---

## 2. Image Build Instructions

Run the following build commands from the project root (`c:\Users\aDMIN\Documents\JSD\Mini Leetcode`):

### Build Java 17 Runner
```bash
docker build -t mini-leetcode-runner-java:17 backend/mini-leetcode-judge/docker/java
```

### Build Python 3 Runner
```bash
docker build -t mini-leetcode-runner-python:3 backend/mini-leetcode-judge/docker/python
```

---

## 3. Sandbox Security Flags & Architecture

During evaluation, each testcase runs inside a newly spawned ephemeral container with hardened kernel and cgroup constraints:

| Flag | Value | Purpose & Security Rationale |
|---|---|---|
| `--rm` | (flag) | Ephemeral lifecycle: automatically removes container state and file systems upon process termination. |
| `--network` | `none` | Network isolation: disables loopback and external network interfaces, preventing SSRF, reverse shells, cryptocurrency mining, or exfiltration to internal services (MySQL, Redis, MinIO). |
| `--memory` | `256m` | Memory hard limit: restricts total RAM to 256MB to prevent host memory exhaustion. |
| `--memory-swap` | `256m` | Disables swap: when swap equals memory limit, available swap space is 0MB. The Linux kernel OOM killer triggers immediately when 256MB is breached (exit code 137), mapping directly to `MLE`. |
| `--pids-limit` | `64` | Process ceiling: limits container process count to 64, completely mitigating fork bombs (`:(){ :\|:& };:`). |
| `-v` | `<temp_dir>:/workspace:ro` | Read-only volume mount: mounts the staged host submission directory as read-only. Untrusted code cannot mutate, delete, or inject files into `/workspace`. |

---

## 4. Execution Commands

### 4.1 Java 17

#### Step 1: Single Compilation (`javac`)
Java source code (`Solution.java`) is compiled **once** per submission in the staging directory:
```bash
docker run --rm --network none --memory 256m --memory-swap 256m --pids-limit 64 \
  -v <temp_dir>:/workspace \
  mini-leetcode-runner-java:17 \
  javac -encoding UTF-8 /workspace/Solution.java
```
*Note: The compile step uses read-write mount to write `.class` files to the staging directory. If compilation exits with non-zero code, execution halts immediately with `CE` (Compile Error).*

#### Step 2: Testcase Execution Loop (Read-Only)
For each testcase, input is provided via `/workspace/input.txt`:
```bash
docker run --rm --network none --memory 256m --memory-swap 256m --pids-limit 64 \
  -v <temp_dir>:/workspace:ro \
  mini-leetcode-runner-java:17 \
  java -Xmx256m Solution < /workspace/input.txt
```

### 4.2 Python 3

#### Testcase Execution Loop (Read-Only)
Python source code (`solution.py`) is executed directly with input piped from `/workspace/input.txt`:
```bash
docker run --rm --network none --memory 256m --memory-swap 256m --pids-limit 64 \
  -v <temp_dir>:/workspace:ro \
  mini-leetcode-runner-python:3 \
  python3 solution.py < /workspace/input.txt
```

---

## 5. Fail-Fast Execution Policy & Verdict Mapping

The Judge Worker executes testcases sequentially ordered by `orderIndex`. If any testcase fails, execution terminates immediately (**Fail-Fast Policy**) without running subsequent testcases.

| Result / Exit Code | Mapped Verdict | Description |
|---|---|---|
| Exit code 0 & output matches expected | `AC` (Accepted) | All testcases passed successfully. |
| Exit code 0 & output mismatches expected | `WA` (Wrong Answer) | Output differs from expected after whitespace normalization. Halts immediately. |
| Execution time exceeds `timeLimitMs` | `TLE` (Time Limit Exceeded) | Worker enforces timeout via process monitoring and terminates container. Halts immediately. |
| Exit code 137 (SIGKILL by OOM killer) | `MLE` (Memory Limit Exceeded) | Process exceeded the 256MB memory boundary. Halts immediately. |
| Compilation failure (`javac` exit != 0) | `CE` (Compile Error) | Source code could not be compiled. Halts before any testcase execution. |
| Non-zero exit code / Unhandled exception | `RE` (Runtime Error) | Exception or crash during execution (e.g. `ZeroDivisionError`, `NullPointerException`). Halts immediately. |
| Security violation / Permission denied | `SV` (Security Violation) | Blocked syscall or unauthorized resource access attempt. Halts immediately. |
