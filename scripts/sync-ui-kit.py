#!/usr/bin/env python3
"""Regenerates the UI kit manifest or copies the kit from a template checkout.

The kit path list is mirrored in src/test/kotlin/com/example/template/ui/UiKitManifest.kt.
"""
import argparse
import hashlib
import os
from pathlib import Path
import sys

KIT_FILES = ["package.json", "pnpm-lock.yaml"]
KIT_DIRS = [
    "config/ui",
    "src/main/resources/static/ui",
    "src/main/resources/templates/ui",
    "src/main/resources/templates/error",
    "src/test/ui",
]
MANIFEST = "config/ui/kit.sha256"
# Dependency installs are not kit content.
IGNORED_DIRS = {"node_modules"}
# Git ignores this file, so listing it would pass locally and fail as missing on a clean checkout.
IGNORED_FILES = {".DS_Store"}


def kit_paths(root: Path) -> list[str]:
    """Lists kit files as sorted POSIX paths. Symlinks are never listed or followed."""
    found = set()
    for name in KIT_FILES:
        path = root / name
        if path.is_file() and not path.is_symlink():
            found.add(name)
    for directory in KIT_DIRS:
        start = root / directory
        if start.is_symlink() or not start.is_dir():
            continue
        for current, dirs, files in os.walk(start, followlinks=False):
            dirs[:] = [d for d in dirs if d not in IGNORED_DIRS and not (Path(current) / d).is_symlink()]
            for file in files:
                path = Path(current) / file
                if file not in IGNORED_FILES and not path.is_symlink():
                    found.add(path.relative_to(root).as_posix())
    found.discard(MANIFEST)
    return sorted(found)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def render_manifest(root: Path) -> str:
    return "".join(f"{digest(root / p)}  {p}\n" for p in kit_paths(root))


def read_manifest(root: Path) -> dict[str, str] | None:
    path = root / MANIFEST
    if not path.is_file() or path.is_symlink():
        return None
    entries = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            value, _, name = line.partition("  ")
            entries[name] = value
    return entries


def verify_manifest(root: Path) -> list[str]:
    expected = read_manifest(root)
    if expected is None:
        return [f"missing: {MANIFEST}"]
    if not expected:
        return [f"empty: {MANIFEST}"]
    actual = kit_paths(root)
    problems = []
    for name, value in sorted(expected.items()):
        path = root / name
        if name not in actual:
            problems.append(f"missing: {name}")
        elif digest(path) != value:
            problems.append(f"changed: {name}")
    problems += [f"unlisted: {name}" for name in actual if name not in expected]
    return problems


def write_target(target: Path, name: str, content: bytes) -> None:
    destination = target / name
    root = target.resolve()
    if root not in destination.parent.resolve().parents and destination.parent.resolve() != root:
        raise SystemExit(f"refusing to write outside the target: {name}")
    if destination.is_symlink():
        destination.unlink()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(content)


def stale_kit_files(target: Path, names: list[str]) -> list[str]:
    """Lists what sync may delete. Only the target's own manifest proves that a file came from the kit."""
    listed = read_manifest(target) or {}
    return sorted((set(kit_paths(target)) & set(listed)) - set(names))


def sync(source: Path, target: Path) -> None:
    if source.resolve() == target.resolve():
        raise SystemExit("source and target are the same directory")
    problems = verify_manifest(source)
    if problems:
        raise SystemExit("source kit does not match its manifest:\n" + "\n".join(problems))
    names = kit_paths(source) + [MANIFEST]
    contents = {name: (source / name).read_bytes() for name in names}
    for name in stale_kit_files(target, names):
        (target / name).unlink()
        print(f"removed: {name}")
    for name, content in contents.items():
        write_target(target, name, content)
    problems = verify_manifest(target)
    if problems:
        raise SystemExit("target kit does not match the manifest after sync:\n" + "\n".join(problems))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write-manifest", action="store_true", help="regenerate config/ui/kit.sha256 from this repository")
    group.add_argument("--source", type=Path, help="template checkout to copy the kit from")
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    if args.write_manifest:
        (root / MANIFEST).write_text(render_manifest(root), encoding="utf-8")
    else:
        sync(args.source, root)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
