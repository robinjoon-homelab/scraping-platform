package com.example.template.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

class UiKitManifestTest {
    private fun write(
        root: Path,
        name: String,
        content: String,
    ) {
        val path = root.resolve(name)
        path.parent.createDirectories()
        path.writeText(content)
    }

    private fun writeKit(root: Path) {
        write(root, "package.json", "{}\n")
        write(root, "config/ui/prettier.json", "{}\n")
        write(root, "src/main/resources/static/ui/tokens.css", ":root {}\n")
        write(root, "src/main/resources/templates/error/404.html", "<p>404</p>\n")
        write(root, "src/main/resources/static/app/theme.css", ":root {}\n")
        write(root, UiKitManifest.MANIFEST, UiKitManifest.render(root))
    }

    @Test
    fun `reports no differences when the manifest matches`(
        @TempDir root: Path,
    ) {
        writeKit(root)

        assertThat(UiKitManifest.differences(root)).isEmpty()
    }

    @Test
    fun `reports a changed kit file`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        write(root, "package.json", "{ }\n")

        assertThat(UiKitManifest.differences(root)).containsExactly("changed: package.json")
    }

    @Test
    fun `reports a deleted kit file`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        root.resolve("config/ui/prettier.json").deleteExisting()

        assertThat(UiKitManifest.differences(root)).containsExactly("missing: config/ui/prettier.json")
    }

    @Test
    fun `reports a kit file the manifest does not list`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        write(root, "src/test/ui/new.test.mjs", "// new\n")

        assertThat(UiKitManifest.differences(root)).containsExactly("unlisted: src/test/ui/new.test.mjs")
    }

    @Test
    fun `ignores app files`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        write(root, "src/main/resources/static/app/theme.css", ":root { color-scheme: dark; }\n")

        assertThat(UiKitManifest.differences(root)).isEmpty()
    }

    @Test
    fun `ignores finder metadata in kit directories`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        write(root, "config/ui/.DS_Store", "finder\n")
        write(root, "src/main/resources/templates/error/.DS_Store", "finder\n")

        assertThat(UiKitManifest.differences(root)).isEmpty()
        assertThat(UiKitManifest.render(root)).doesNotContain(".DS_Store")
    }

    @Test
    fun `fails when the manifest is missing or empty`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        root.resolve(UiKitManifest.MANIFEST).deleteExisting()
        assertThat(UiKitManifest.differences(root)).containsExactly("missing: ${UiKitManifest.MANIFEST}")

        write(root, UiKitManifest.MANIFEST, "")
        assertThat(UiKitManifest.differences(root)).containsExactly("empty: ${UiKitManifest.MANIFEST}")
    }

    @Test
    fun `does not follow symlinks outside the kit`(
        @TempDir root: Path,
    ) {
        writeKit(root)
        val outside = Files.createTempFile("outside", ".txt")
        try {
            Files.createSymbolicLink(root.resolve("config/ui/link.txt"), outside)

            assertThat(UiKitManifest.differences(root)).isEmpty()
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `the repository kit matches its manifest`() {
        val differences = UiKitManifest.differences(Path.of("").toAbsolutePath())

        assertThat(differences)
            .describedAs(
                "In the template repository, regenerate with scripts/sync-ui-kit.py --write-manifest. " +
                    "In an app, restore the kit with scripts/sync-ui-kit.py --source <template checkout>. $differences",
            ).isEmpty()
    }
}
