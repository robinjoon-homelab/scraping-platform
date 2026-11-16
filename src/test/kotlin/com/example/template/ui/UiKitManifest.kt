package com.example.template.ui

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo

// This repeats the manifest logic of scripts/sync-ui-kit.py on purpose so that ./gradlew build does not need Python.
// The kit path lists below must stay in step with KIT_FILES and KIT_DIRS in that script.
object UiKitManifest {
    const val MANIFEST = "config/ui/kit.sha256"
    private val kitFiles = listOf("package.json", "pnpm-lock.yaml")
    private val kitDirs =
        listOf(
            "config/ui",
            "src/main/resources/static/ui",
            "src/main/resources/templates/ui",
            "src/main/resources/templates/error",
            "src/test/ui",
        )
    private val ignoredDirs = setOf("node_modules")

    // Git ignores this file, so listing it would pass locally and fail as missing on a clean checkout.
    private val ignoredFiles = setOf(".DS_Store")

    fun differences(root: Path): List<String> {
        val manifest = root.resolve(MANIFEST)
        if (!manifest.isRegularFile(LinkOption.NOFOLLOW_LINKS)) return listOf("missing: $MANIFEST")
        val expected = parse(manifest)
        if (expected.isEmpty()) return listOf("empty: $MANIFEST")
        val actual = kitPaths(root)
        val problems =
            expected.entries.sortedBy { it.key }.mapNotNull { (name, hash) ->
                when {
                    name !in actual -> "missing: $name"
                    sha256(root.resolve(name)) != hash -> "changed: $name"
                    else -> null
                }
            }
        return problems + actual.filter { it !in expected }.map { "unlisted: $it" }
    }

    fun render(root: Path): String = kitPaths(root).joinToString("") { "${sha256(root.resolve(it))}  $it\n" }

    private fun parse(manifest: Path): Map<String, String> =
        manifest
            .readLines()
            .filter { it.isNotBlank() }
            .associate { line -> line.substringAfter("  ") to line.substringBefore("  ") }

    private fun kitPaths(root: Path): List<String> {
        val files = kitFiles.filter { root.resolve(it).isRegularFile(LinkOption.NOFOLLOW_LINKS) }
        val nested = kitDirs.flatMap { filesUnder(root, root.resolve(it)) }
        return (files + nested).filter { it != MANIFEST }.distinct().sorted()
    }

    // Symlinks are skipped so the manifest never reads outside the kit.
    private fun filesUnder(
        root: Path,
        directory: Path,
    ): List<String> {
        if (!directory.exists(LinkOption.NOFOLLOW_LINKS) || !directory.isDirectory(LinkOption.NOFOLLOW_LINKS)) return emptyList()
        return Files.newDirectoryStream(directory).use { entries ->
            entries.toList().flatMap { entry ->
                when {
                    entry.fileName.toString() in ignoredFiles -> emptyList()
                    entry.isRegularFile(LinkOption.NOFOLLOW_LINKS) -> listOf(entry.relativeTo(root).joinToString("/"))
                    entry.isDirectory(LinkOption.NOFOLLOW_LINKS) && entry.fileName.toString() !in ignoredDirs -> filesUnder(root, entry)
                    else -> emptyList()
                }
            }
        }
    }

    private fun sha256(path: Path): String =
        MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") {
            "%02x".format(it)
        }
}
