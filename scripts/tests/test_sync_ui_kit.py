import contextlib
import importlib.util
import io
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "sync-ui-kit.py"
# scripts/tests is shared by every app, including apps that never took the UI kit or removed it.
if not SCRIPT.is_file():
    raise unittest.SkipTest("scripts/sync-ui-kit.py is absent, so this app has no UI kit")

spec = importlib.util.spec_from_file_location("sync_ui_kit", SCRIPT)
sync_ui_kit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync_ui_kit)


def write(root: Path, name: str, content: str) -> None:
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def make_kit(root: Path) -> None:
    write(root, "package.json", "{}\n")
    write(root, "config/ui/prettier.json", "{}\n")
    write(root, "src/main/resources/static/ui/tokens.css", ":root {}\n")
    write(root, "src/main/resources/templates/ui/layout.html", "<html></html>\n")
    write(root, "src/main/resources/templates/error/404.html", "<p>404</p>\n")
    write(root, "src/test/ui/api.test.mjs", "// test\n")
    write(root, "src/main/resources/static/app/theme.css", ":root { --ui-accent: red; }\n")
    write(root, "src/main/resources/templates/app/nav.html", "<nav></nav>\n")
    (root / sync_ui_kit.MANIFEST).write_text(sync_ui_kit.render_manifest(root), encoding="utf-8")


class SyncUiKitTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.source = Path(directory.name) / "source"
        self.target = Path(directory.name) / "target"
        make_kit(self.source)
        make_kit(self.target)

    def test_manifest_is_sorted_and_stable(self):
        first = sync_ui_kit.render_manifest(self.source)
        self.assertEqual(first, sync_ui_kit.render_manifest(self.source))
        paths = [line.split("  ", 1)[1] for line in first.splitlines()]
        self.assertEqual(paths, sorted(paths))
        self.assertNotIn(sync_ui_kit.MANIFEST, paths)
        self.assertNotIn("src/main/resources/static/app/theme.css", paths)

    def test_verify_reports_each_difference(self):
        self.assertEqual(sync_ui_kit.verify_manifest(self.source), [])
        write(self.source, "package.json", "{ }\n")
        (self.source / "config/ui/prettier.json").unlink()
        write(self.source, "src/test/ui/new.test.mjs", "// new\n")
        self.assertEqual(
            sync_ui_kit.verify_manifest(self.source),
            ["missing: config/ui/prettier.json", "changed: package.json", "unlisted: src/test/ui/new.test.mjs"],
        )

    def test_sync_copies_kit_and_removes_stale_files(self):
        write(self.source, "src/main/resources/static/ui/tokens.css", ":root { color-scheme: dark; }\n")
        (self.source / "src/test/ui/api.test.mjs").unlink()
        (self.source / sync_ui_kit.MANIFEST).write_text(sync_ui_kit.render_manifest(self.source), encoding="utf-8")
        self.assertIn("src/test/ui/api.test.mjs", sync_ui_kit.read_manifest(self.target))
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            sync_ui_kit.sync(self.source, self.target)
        self.assertEqual((self.target / "src/main/resources/static/ui/tokens.css").read_text(), ":root { color-scheme: dark; }\n")
        self.assertFalse((self.target / "src/test/ui/api.test.mjs").exists())
        self.assertIn("removed: src/test/ui/api.test.mjs", output.getvalue())
        self.assertEqual(sync_ui_kit.verify_manifest(self.target), [])

    def test_sync_keeps_files_the_target_manifest_never_listed(self):
        names = ["src/main/resources/templates/error/503.html", "src/main/resources/static/ui/mine.css"]
        for name in names:
            write(self.target, name, "app\n")
        output = io.StringIO()
        with contextlib.redirect_stdout(output), self.assertRaises(SystemExit) as failure:
            sync_ui_kit.sync(self.source, self.target)
        for name in names:
            self.assertEqual((self.target / name).read_text(), "app\n")
            self.assertIn(f"unlisted: {name}", str(failure.exception))
        self.assertNotIn("removed:", output.getvalue())

    def test_sync_without_a_target_manifest_deletes_nothing(self):
        (self.source / "src/test/ui/api.test.mjs").unlink()
        (self.source / sync_ui_kit.MANIFEST).write_text(sync_ui_kit.render_manifest(self.source), encoding="utf-8")
        (self.target / sync_ui_kit.MANIFEST).unlink()
        output = io.StringIO()
        with contextlib.redirect_stdout(output), self.assertRaises(SystemExit):
            sync_ui_kit.sync(self.source, self.target)
        self.assertTrue((self.target / "src/test/ui/api.test.mjs").exists())
        self.assertNotIn("removed:", output.getvalue())

    def test_sync_removes_only_existing_kit_files_from_the_target_manifest(self):
        outside = self.target.parent / "outside.txt"
        outside.write_text("keep\n", encoding="utf-8")
        manifest = self.target / sync_ui_kit.MANIFEST
        fake = "0" * 64
        manifest.write_text(manifest.read_text(encoding="utf-8") + f"{fake}  ../outside.txt\n{fake}  config/ui/gone.json\n", encoding="utf-8")
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            sync_ui_kit.sync(self.source, self.target)
        self.assertEqual(outside.read_text(), "keep\n")
        self.assertNotIn("removed:", output.getvalue())
        self.assertEqual(sync_ui_kit.verify_manifest(self.target), [])

    def test_sync_leaves_app_files(self):
        sync_ui_kit.sync(self.source, self.target)
        self.assertEqual((self.target / "src/main/resources/static/app/theme.css").read_text(), ":root { --ui-accent: red; }\n")
        self.assertEqual((self.target / "src/main/resources/templates/app/nav.html").read_text(), "<nav></nav>\n")

    def test_sync_refuses_a_tampered_source(self):
        write(self.source, "package.json", "{\"tampered\": true}\n")
        write(self.target, "src/main/resources/static/ui/tokens.css", ":root { --local: 1; }\n")
        with self.assertRaises(SystemExit):
            sync_ui_kit.sync(self.source, self.target)
        self.assertEqual((self.target / "package.json").read_text(), "{}\n")
        self.assertEqual((self.target / "src/main/resources/static/ui/tokens.css").read_text(), ":root { --local: 1; }\n")

    def test_sync_refuses_the_same_directory(self):
        with self.assertRaises(SystemExit):
            sync_ui_kit.sync(self.source, self.source)

    def test_symlinks_are_neither_listed_nor_followed(self):
        outside = self.source.parent / "outside.txt"
        outside.write_text("secret\n", encoding="utf-8")
        (self.source / "src/test/ui/link.mjs").symlink_to(outside)
        (self.source / "config/ui/linked-dir").symlink_to(self.source.parent)
        self.assertNotIn("src/test/ui/link.mjs", sync_ui_kit.kit_paths(self.source))
        self.assertEqual(sync_ui_kit.verify_manifest(self.source), [])
        sync_ui_kit.sync(self.source, self.target)
        self.assertFalse((self.target / "src/test/ui/link.mjs").exists())

    def test_finder_metadata_is_neither_listed_nor_copied(self):
        write(self.source, "config/ui/.DS_Store", "finder\n")
        write(self.source, "src/main/resources/templates/error/.DS_Store", "finder\n")
        self.assertNotIn("config/ui/.DS_Store", sync_ui_kit.kit_paths(self.source))
        self.assertEqual(sync_ui_kit.verify_manifest(self.source), [])
        sync_ui_kit.sync(self.source, self.target)
        self.assertFalse((self.target / "config/ui/.DS_Store").exists())

    def test_discovery_skips_this_module_without_the_kit_script(self):
        tests = self.source.parent / "app/scripts/tests"
        tests.mkdir(parents=True)
        shutil.copy(__file__, tests)
        result = subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", str(tests)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("skipped=1", result.stderr)


if __name__ == "__main__":
    unittest.main()
