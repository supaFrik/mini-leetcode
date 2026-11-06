r"""Sinh testcase stdin/stdout cho bài OOP mini-bank-console vào backend/tools/mini-bank-console/cases/.

Output kỳ vọng = chạy lời giải mẫu (oop-starters/mini-bank-console) trong ĐÚNG image sandbox của judge
(eclipse-temurin:17-jdk) để định dạng số / prompt khớp tuyệt đối. Cần Docker + javac.
Nạp: .\backend\tools\seed-two-sum-testcases.ps1 -Slug mini-bank-console -Username <instructor> -Password <pw>
"""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "oop-starters" / "mini-bank-console"
OUT = HERE.parent / "mini-bank-console" / "cases"
IMAGE = "eclipse-temurin:17-jdk"

# (tên, stdin). Case đầu là ví dụ công khai trong đề; các case sau là testcase ẩn.
CASES = [
    ("01-sample", "2\nNguyễn Văn A\nS\n100000\nTrần B\nC\n0\nWITHDRAW 1 60000\nTRANSFER 1 2 30000\nMONTH\nLIST\nEXIT\n"),
    ("02-interest-rounding", "1\nAn\nS\n123457\nMONTH\nMONTH\nMONTH\nBALANCE 1\nDEPOSIT 1 999\nMONTH\nLIST\nEXIT\n"),
    ("03-overdraft-limit", "1\nBình\nC\n0\nWITHDRAW 1 500000\nWITHDRAW 1 1\nDEPOSIT 1 1\nWITHDRAW 1 1\nMONTH\nBALANCE 1\nEXIT\n"),
    ("04-savings-minimum", "1\nChi\nS\n80000\nWITHDRAW 1 30001\nWITHDRAW 1 30000\nWITHDRAW 1 1\nLIST\nEXIT\n"),
    ("05-failed-transfer-is-atomic", "2\nDũng\nS\n60000\nEm\nC\n0\nTRANSFER 1 2 20000\nTRANSFER 2 1 400000\nTRANSFER 1 9 1\nLIST\nEXIT\n"),
    ("06-open-and-bad-input", "0\nOPEN S 70000 Lê Thị Hồng Nhung\nOPEN X 1 Nobody\nOPEN C 5000 Phạm Quang\nDEPOSIT 1 -5\nFOO\nBALANCE 3\nLIST\nEXIT\n"),
    ("07-eof-summary", "1\nGiang\nC\n250000\nDEPOSIT 1 50000\nMONTH\n"),
    ("08-polymorphic-month", "3\nH1\nS\n200000\nH2\nC\n0\nH3\nS\n50000\nMONTH\nTRANSFER 1 2 100000\nMONTH\nLIST\nREPORT 2\nEXIT\n"),
]


def main() -> None:
    work = Path(tempfile.mkdtemp(prefix="bank-cases-"))
    try:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), *map(str, SRC.glob("*.java"))], check=True)
        if OUT.exists():
            shutil.rmtree(OUT)
        OUT.mkdir(parents=True)
        env = dict(os.environ, MSYS_NO_PATHCONV="1")
        for i, (name, stdin) in enumerate(CASES):
            run = subprocess.run(
                ["docker", "run", "-i", "--rm", "--network", "none", "-v", f"{work}:/w:ro", IMAGE,
                 "java", "-cp", "/w", "Main"],
                input=stdin.encode("utf-8"), capture_output=True, env=env, timeout=60)
            if run.returncode != 0:
                raise SystemExit(f"{name}: reference exited {run.returncode}: {run.stderr.decode('utf-8', 'replace')}")
            stem = name if i == 0 else f"{name}-hidden"
            (OUT / f"{stem}.in").write_bytes(stdin.encode("utf-8"))
            (OUT / f"{stem}.out").write_bytes(run.stdout)
            print(f"{stem}: {len(run.stdout)} bytes")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
