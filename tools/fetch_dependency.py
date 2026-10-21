"""Fetch only the pinned official runtime, or verify an existing local copy."""
import argparse
import hashlib
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
NAME = "sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/" + NAME
SHA256 = "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    target = ROOT / "libs" / NAME
    if target.is_file():
        if digest(target) != SHA256:
            raise SystemExit("Existing AAR has the wrong hash; it was not overwritten.")
    elif args.verify_only:
        raise SystemExit("Missing AAR; run this script without --verify-only to download.")
    else:
        target.parent.mkdir(exist_ok=True)
        temporary = target.with_suffix(".part")
        try:
            with urllib.request.urlopen(URL, timeout=90) as response, temporary.open("wb") as output:
                total = 0
                while block := response.read(65536):
                    total += len(block)
                    if total > 50 * 1024 * 1024:
                        raise ValueError("Unexpected dependency size")
                    output.write(block)
            if digest(temporary) != SHA256:
                raise ValueError("Official release checksum mismatch")
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    print(f"Verified {NAME}: {SHA256}")


if __name__ == "__main__":
    main()
