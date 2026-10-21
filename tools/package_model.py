"""Package verified model files only. Never include example or user recordings."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "dist/Cyrene-Voice-Base.cyvoice")
    args = parser.parse_args()
    expected = json.loads((ROOT / "docs/model-files.sha256.json").read_text(encoding="utf-8"))
    hashes = {}
    sources = {}
    for name, digest in expected.items():
        source = ROOT / "third_party/ZipVoice-LICENSE" if name == "LICENSE" else args.model_dir / name
        if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != digest:
            raise SystemExit(f"Missing or changed model file: {name}")
        hashes["model/" + name] = digest
        sources[name] = source
    if args.output.exists():
        raise SystemExit("Output already exists; choose a new path to avoid overwriting a voice pack.")
    manifest = {
        "formatVersion": 2,
        "engine": "zipvoice-distill-int8",
        "name": "Cyrene Voice 离线基础模型",
        "roles": [],
        "sha256": hashes,
        "sources": [
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos_24khz.onnx",
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(".part")
    try:
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=4) as archive:
            archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
            for name in sorted(expected):
                archive.write(sources[name], "model/" + name)
        with zipfile.ZipFile(temporary) as archive:
            if archive.testzip() is not None:
                raise ValueError("Archive integrity check failed")
            for name, digest in hashes.items():
                if hashlib.sha256(archive.read(name)).hexdigest() != digest:
                    raise ValueError(f"Archive checksum failed: {name}")
        temporary.replace(args.output)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Created {args.output.name}; {len(hashes)} verified model files; no roles or recordings")
    print("SHA256", hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
