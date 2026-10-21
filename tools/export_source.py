"""Export an explicit source allowlist; exclude all local voices, binaries and credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOP = {
    ".gitignore", ".gitattributes", "LICENSE", "NOTICE", "README.md", "THIRD_PARTY_NOTICES.md", "build.gradle.kts",
    "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    "app/build.gradle.kts", "app/proguard-rules.pro",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    ".github/workflows/build.yml",
}
TREES = ("app/src/main", "shared/src/main", "app/src/test", "docs", "tools", "third_party")
EXTENSIONS = {".kt", ".xml", ".md", ".py", ".json"}


def selected_sources():
    selected = {ROOT / name for name in TOP}
    for directory in TREES:
        for path in (ROOT / directory).rglob("*"):
            if not path.is_file() or "__pycache__" in path.parts:
                continue
            if path.suffix in EXTENSIONS or directory == "third_party":
                selected.add(path)
    return sorted(selected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "outputs/Cyrene-Voice-Studio-0.2.0-github-source.zip")
    args = parser.parse_args()
    selected = selected_sources()
    if args.output.exists():
        raise SystemExit("Output already exists; choose a new filename.")
    manifest = {}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(".part")
    try:
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(selected):
                name = path.relative_to(ROOT).as_posix()
                data = path.read_bytes()
                manifest[name] = hashlib.sha256(data).hexdigest()
                info = zipfile.ZipInfo("Cyrene-Voice-Studio/" + name)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (0o100755 if name == "gradlew" else 0o100644) << 16
                archive.writestr(info, data)
            archive.writestr("SOURCE_SHA256.json", json.dumps(manifest, indent=2) + "\n")
        with zipfile.ZipFile(temporary) as archive:
            if archive.testzip() is not None:
                raise ValueError("Source ZIP integrity failed")
            for name, digest in manifest.items():
                if hashlib.sha256(archive.read("Cyrene-Voice-Studio/" + name)).hexdigest() != digest:
                    raise ValueError(f"Source ZIP checksum failed: {name}")
        temporary.replace(args.output)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Exported {len(manifest)} allowlisted source files")
    print("SHA256", hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
