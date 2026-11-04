"""Sinh testcase cho 5 bài OOP chấm bằng stdin/stdout (CHỈ DÙNG CHO DEV). Cách làm giống dsa.py.

Bài đọc một chuỗi lệnh và in kết quả từng lệnh; trình chấm chỉ so phần in ra.
Run: python backend/tools/problems/oop.py [slug ...]   (ghi vào backend/tools/<slug>/cases/)
"""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dsa import write_cases  # noqa: E402

rng = random.Random(20261105)


# ---------------------------------------------------------------- lời giải chuẩn

def solve_bank_account(text):
    lines = text.split("\n")
    acc, out = {}, []
    for i in range(int(lines[0])):
        p = lines[1 + i].split()
        cmd, who = p[0], p[1]
        amt = int(p[2]) if len(p) > 2 else 0
        if cmd == "OPEN":
            if who in acc or amt < 0:
                out.append("ERROR")
            else:
                acc[who] = amt
                out.append("OK")
        elif who not in acc or (cmd != "BALANCE" and amt <= 0) or (cmd == "WITHDRAW" and amt > acc[who]):
            out.append("ERROR")
        else:
            acc[who] += amt if cmd == "DEPOSIT" else -amt if cmd == "WITHDRAW" else 0
            out.append(str(acc[who]))
    return "\n".join(out)


def solve_shape_area(text):
    import math
    lines = text.split("\n")
    out = []
    for i in range(int(lines[0])):
        p = lines[1 + i].split()
        v = [int(x) for x in p[1:]]
        area = None
        if all(x > 0 for x in v):
            if p[0] == "CIRCLE":
                area = math.pi * v[0] * v[0]
            elif p[0] == "RECTANGLE":
                area = float(v[0] * v[1])
            else:
                a, b, c = v
                if a + b > c and a + c > b and b + c > a:
                    s = (a + b + c) / 2.0
                    area = math.sqrt(s * (s - a) * (s - b) * (s - c))
        out.append("INVALID" if area is None else f"{area:.2f}")
    return "\n".join(out)


def solve_library(text):
    lines = text.split("\n")
    books, members, out = {}, {}, []
    limit = {"STUDENT": 2, "TEACHER": 5}
    for i in range(int(lines[0])):
        p = lines[1 + i].split()
        cmd = p[0]
        if cmd == "ADD_BOOK":
            b, c = p[1], int(p[2])
            out.append("ERROR INVALID" if c <= 0 else "ERROR DUPLICATE" if b in books else "OK")
            if out[-1] == "OK":
                books[b] = c
        elif cmd == "ADD_MEMBER":
            m, t = p[1], p[2]
            out.append("ERROR INVALID" if t not in limit else "ERROR DUPLICATE" if m in members else "OK")
            if out[-1] == "OK":
                members[m] = (t, set())
        elif cmd == "AVAILABLE":
            out.append(str(books[p[1]]) if p[1] in books else "ERROR NO_BOOK")
        else:
            m, b = p[1], p[2]
            if m not in members:
                out.append("ERROR NO_MEMBER")
                continue
            if b not in books:
                out.append("ERROR NO_BOOK")
                continue
            t, held = members[m]
            if cmd == "BORROW":
                if b in held:
                    out.append("ERROR ALREADY_BORROWED")
                elif len(held) >= limit[t]:
                    out.append("ERROR LIMIT")
                elif books[b] == 0:
                    out.append("ERROR NO_COPY")
                else:
                    held.add(b)
                    books[b] -= 1
                    out.append("OK")
            elif b not in held:
                out.append("ERROR NOT_BORROWED")
            else:
                held.remove(b)
                books[b] += 1
                out.append("OK")
    return "\n".join(out)


def solve_parking_lot(text):
    lines = text.split("\n")
    free = dict(zip("SML", map(int, lines[0].split())))
    fits = {"MOTORCYCLE": "SML", "CAR": "ML", "BUS": "L"}
    parked, tickets, out, next_id = set(), {}, [], 1
    for i in range(int(lines[1])):
        p = lines[2 + i].split()
        if p[0] == "PARK":
            plate, kind = p[1], p[2]
            if kind not in fits:
                out.append("ERROR INVALID")
            elif plate in parked:
                out.append("ERROR DUPLICATE")
            else:
                size = next((s for s in fits[kind] if free[s] > 0), None)
                if size is None:
                    out.append("FULL")
                else:
                    free[size] -= 1
                    parked.add(plate)
                    tickets[f"T{next_id}"] = (plate, size)
                    out.append(f"T{next_id} {size}")
                    next_id += 1
        elif p[0] == "LEAVE":
            if p[1] not in tickets:
                out.append("ERROR UNKNOWN_TICKET")
            else:
                plate, size = tickets.pop(p[1])
                parked.discard(plate)
                free[size] += 1
                out.append("OK")
        else:
            out.append(str(free[p[1]]) if p[1] in free else "ERROR INVALID")
    return "\n".join(out)


def solve_event_bus(text):
    lines = text.split("\n")
    subs, out = {}, []
    for i in range(int(lines[0])):
        p = lines[1 + i].split(" ", 2)
        cmd = p[0]
        if cmd == "PUBLISH":
            topic, msg = p[1], p[2].strip()
            names = subs.get(topic, [])
            out.extend(f"{s} <- {topic}: {msg}" for s in names) if names else out.append("NO_SUBSCRIBERS")
        else:
            s, topic = p[1], p[2].strip()
            names = subs.setdefault(topic, [])
            if cmd == "SUBSCRIBE":
                out.append("ERROR DUPLICATE" if s in names else "OK")
                if out[-1] == "OK":
                    names.append(s)
            elif s in names:
                names.remove(s)
                out.append("OK")
            else:
                out.append("ERROR NOT_SUBSCRIBED")
    return "\n".join(out)


# ---------------------------------------------------------------- dữ liệu

def cmds(rows):
    return f"{len(rows)}\n" + "\n".join(rows)


def bank_random(q):
    ids = [f"acc{i}" for i in range(40)]
    rows = []
    for _ in range(q):
        r = rng.random()
        who = rng.choice(ids)
        if r < 0.1:
            rows.append(f"OPEN {who} {rng.choice([0, -5, rng.randint(1, 10**9)])}")
        elif r < 0.4:
            rows.append(f"DEPOSIT {who} {rng.choice([0, -1, rng.randint(1, 10**9)])}")
        elif r < 0.8:
            rows.append(f"WITHDRAW {who} {rng.randint(-1, 2 * 10**9)}")
        else:
            rows.append(f"BALANCE {who}")
    return cmds(rows)


def safe_area_line():
    """Một hình có diện tích không nằm sát mốc làm tròn .xx5, để mọi ngôn ngữ in giống nhau."""
    while True:
        kind = rng.choice(["CIRCLE", "RECTANGLE", "TRIANGLE"])
        n = {"CIRCLE": 1, "RECTANGLE": 2, "TRIANGLE": 3}[kind]
        vals = [rng.randint(-20, 1000) if rng.random() < 0.05 else rng.randint(1, 1000) for _ in range(n)]
        row = f"{kind} {' '.join(map(str, vals))}"
        if solve_shape_area(f"1\n{row}") == "INVALID" or not near_half(kind, vals):
            return row


def near_half(kind, v):
    if kind == "CIRCLE":
        a = math.pi * v[0] * v[0]
    elif kind == "RECTANGLE":
        a = v[0] * v[1]
    else:
        s = sum(v) / 2.0
        a = math.sqrt(max(0.0, s * (s - v[0]) * (s - v[1]) * (s - v[2])))
    return abs((a * 100) % 1 - 0.5) < 1e-4


def library_random(q):
    books = [f"B{i}" for i in range(30)]
    members = [f"M{i}" for i in range(25)]
    rows = []
    for _ in range(q):
        r = rng.random()
        if r < 0.08:
            rows.append(f"ADD_BOOK {rng.choice(books)} {rng.randint(-1, 4)}")
        elif r < 0.15:
            rows.append(f"ADD_MEMBER {rng.choice(members)} {rng.choice(['STUDENT', 'TEACHER', 'GUEST'])}")
        elif r < 0.6:
            rows.append(f"BORROW {rng.choice(members)} {rng.choice(books)}")
        elif r < 0.9:
            rows.append(f"RETURN {rng.choice(members)} {rng.choice(books)}")
        else:
            rows.append(f"AVAILABLE {rng.choice(books)}")
    return cmds(rows)


def parking_random(s, m, l, q):
    plates = [f"P{i:03d}" for i in range(200)]
    rows, issued = [], 0
    for _ in range(q):
        r = rng.random()
        if r < 0.55:
            rows.append(f"PARK {rng.choice(plates)} {rng.choice(['MOTORCYCLE', 'CAR', 'CAR', 'BUS', 'TRUCK'])}")
            issued += 1
        elif r < 0.9:
            rows.append(f"LEAVE T{rng.randint(1, max(1, issued))}")
        else:
            rows.append(f"FREE {rng.choice('SMLX')}")
    return f"{s} {m} {l}\n{len(rows)}\n" + "\n".join(rows)


def events_random(q):
    users = [f"user{i}" for i in range(20)]
    topics = [f"topic{i}" for i in range(6)]
    words = ["build", "passed", "deploy", "started", "order", "shipped", "price", "dropped"]
    rows = []
    for _ in range(q):
        r = rng.random()
        if r < 0.4:
            rows.append(f"SUBSCRIBE {rng.choice(users)} {rng.choice(topics)}")
        elif r < 0.6:
            rows.append(f"UNSUBSCRIBE {rng.choice(users)} {rng.choice(topics)}")
        else:
            rows.append(f"PUBLISH {rng.choice(topics)} {' '.join(rng.choices(words, k=rng.randint(1, 4)))}")
    return cmds(rows)


PROBLEMS = [
    ("bank-account-class", solve_bank_account, [
        ("example-1", cmds(["OPEN alice 100", "DEPOSIT alice 50", "WITHDRAW alice 30", "BALANCE alice"]), False),
        ("example-2", cmds(["OPEN bob 0", "WITHDRAW bob 10", "DEPOSIT bob -5", "BALANCE carol", "OPEN bob 7"]), False),
        ("negative-opening", cmds(["OPEN x -1", "BALANCE x", "OPEN x 0", "BALANCE x"]), True),
        ("exact-withdraw", cmds(["OPEN a 1000000000", "WITHDRAW a 1000000000", "WITHDRAW a 1", "DEPOSIT a 1"]), True),
        ("large-balance", cmds(["OPEN big 1000000000"] + ["DEPOSIT big 1000000000"] * 5000 + ["BALANCE big"]), True),
        ("random", bank_random(20000), True),
    ]),
    ("shape-area-hierarchy", solve_shape_area, [
        ("example-1", cmds(["CIRCLE 2", "RECTANGLE 3 4", "TRIANGLE 3 4 5"]), False),
        ("example-2", cmds(["CIRCLE 0", "TRIANGLE 1 2 3", "RECTANGLE 5 -2"]), False),
        ("degenerate-triangles", cmds(["TRIANGLE 2 2 4", "TRIANGLE 10 1 1", "TRIANGLE 7 7 7"]), True),
        ("max-values", cmds(["CIRCLE 1000", "RECTANGLE 1000 1000", "TRIANGLE 1000 1000 1000"]), True),
        ("random", cmds([safe_area_line() for _ in range(20000)]), True),
    ]),
    ("library-management-system", solve_library, [
        ("example-1", cmds(["ADD_BOOK B1 1", "ADD_MEMBER ann STUDENT", "BORROW ann B1", "AVAILABLE B1", "RETURN ann B1", "AVAILABLE B1"]), False),
        ("example-2", cmds(["ADD_BOOK B1 0", "ADD_MEMBER tom GUEST", "BORROW tom B1", "ADD_BOOK B2 1", "ADD_MEMBER tom TEACHER", "RETURN tom B2"]), False),
        ("student-limit", cmds(["ADD_BOOK A 5", "ADD_BOOK B 5", "ADD_BOOK C 5", "ADD_MEMBER s STUDENT", "BORROW s A", "BORROW s B", "BORROW s C", "BORROW s A"]), True),
        ("no-copy", cmds(["ADD_BOOK X 1", "ADD_MEMBER t1 TEACHER", "ADD_MEMBER t2 TEACHER", "BORROW t1 X", "BORROW t2 X", "AVAILABLE Y", "ADD_BOOK X 3"]), True),
        ("random", library_random(20000), True),
    ]),
    ("parking-lot-design", solve_parking_lot, [
        ("example-1", "1 1 1\n4\nPARK m1 MOTORCYCLE\nPARK c1 CAR\nPARK c2 CAR\nPARK b1 BUS", False),
        ("example-2", "0 1 0\n5\nPARK c1 CAR\nLEAVE T1\nLEAVE T1\nPARK c1 TRUCK\nFREE M", False),
        ("no-spots", "0 0 0\n3\nPARK a MOTORCYCLE\nFREE L\nLEAVE T1", True),
        ("duplicate-plate", "2 2 2\n4\nPARK p1 CAR\nPARK p1 MOTORCYCLE\nLEAVE T1\nPARK p1 BUS", True),
        ("random-small-lot", parking_random(3, 3, 2, 20000), True),
        ("random-large-lot", parking_random(1000, 1000, 1000, 20000), True),
    ]),
    ("event-bus-observer", solve_event_bus, [
        ("example-1", cmds(["SUBSCRIBE ann orders", "SUBSCRIBE bob orders", "PUBLISH orders order 42 shipped", "UNSUBSCRIBE ann orders", "PUBLISH orders order 43 shipped"]), False),
        ("example-2", cmds(["PUBLISH news hello", "SUBSCRIBE ann news", "SUBSCRIBE ann news", "UNSUBSCRIBE bob news"]), False),
        ("resubscribe-order", cmds(["SUBSCRIBE a t", "SUBSCRIBE b t", "UNSUBSCRIBE a t", "SUBSCRIBE a t", "PUBLISH t ping"]), True),
        ("topics-are-separate", cmds(["SUBSCRIBE a t1", "PUBLISH t2 hi", "PUBLISH t1 hi", "UNSUBSCRIBE a t2"]), True),
        ("random", events_random(5000), True),
    ]),
]

if __name__ == "__main__":
    write_cases(PROBLEMS, set(sys.argv[1:]))
