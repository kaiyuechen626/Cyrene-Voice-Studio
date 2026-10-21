"""Check the source allowlist for private files and common embedded credentials."""
import argparse
import hashlib
import re
import subprocess
from export_source import ROOT, selected_sources

PATTERNS = {
    "GitHub credential": rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,})",
    "API secret": rb"sk-[A-Za-z0-9_-]{20,}",
    "Private key": rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "Personal Windows path": rb"(?:C:[/\\]Users[/\\]|D:[/\\]muse update[/\\])",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="Check exactly the Git index contents")
    args = parser.parse_args()
    allowed = {p.relative_to(ROOT).as_posix(): p for p in selected_sources()}
    if args.staged:
        result = subprocess.run(["git", "ls-files", "--cached", "-z"], cwd=ROOT, check=True, capture_output=True)
        names = [n.decode("utf-8") for n in result.stdout.split(b"\0") if n]
        if set(names) != set(allowed):
            raise SystemExit(f"Git index differs from allowlist. Extra: {sorted(set(names)-set(allowed))}; missing: {sorted(set(allowed)-set(names))}")
    else:
        names = sorted(allowed)
    for name in names:
        data = subprocess.run(["git", "show", ":" + name], cwd=ROOT, check=True, capture_output=True).stdout if args.staged else allowed[name].read_bytes()
        if len(data) > 1024 * 1024:
            raise SystemExit(f"Unexpectedly large source file: {name}")
        if name.endswith(".jar"):
            if name != "gradle/wrapper/gradle-wrapper.jar":
                raise SystemExit(f"Unexpected binary: {name}")
            if hashlib.sha256(data).hexdigest() != "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd":
                raise SystemExit("Gradle 8.5 Wrapper JAR does not match the official checksum")
            continue
        data.decode("utf-8-sig")
        for label, pattern in PATTERNS.items():
            if re.search(pattern, data, re.IGNORECASE):
                raise SystemExit(f"{label} pattern detected in {name}; review before publishing")
    print(f"Public source check passed: {len(names)} files; no runtime, models, personal media or common credential patterns")


if __name__ == "__main__":
    main()
