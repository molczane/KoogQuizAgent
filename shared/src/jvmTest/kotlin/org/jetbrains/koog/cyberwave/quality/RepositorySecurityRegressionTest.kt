package org.jetbrains.koog.cyberwave.quality

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RepositorySecurityRegressionTest {
    @Test
    fun repositoryDoesNotContainCommittedOpenAiKeyLikeSecrets() {
        val root = repositoryRoot()
        val suspiciousFiles =
            Files
                .walk(root)
                .use { paths ->
                    paths
                        .filter { path -> path.isRegularFile() }
                        .filter(::shouldScanForSecrets)
                        .map { path ->
                            val content = path.readText()
                            if (OPENAI_KEY_PATTERNS.any { pattern -> pattern.containsMatchIn(content) }) {
                                path.relativeTo(root).invariantSeparatorsPathString
                            } else {
                                null
                            }
                        }.filter { relativePath -> relativePath != null }
                        .map { relativePath -> relativePath!! }
                        .collect(Collectors.toList())
                }

        assertTrue(
            suspiciousFiles.isEmpty(),
            "Potential committed OpenAI key-like secrets found in: ${suspiciousFiles.joinToString()}",
        )
    }

    @Test
    fun readmeDocumentsTheLocalOnlyWasmModeAndSetup() {
        val readme = repositoryRoot().resolve("README.md").readText()

        assertContains(readme, "local-only")
        assertContains(readme, "insecure for deployment")
        assertContains(readme, "cyberwave.openai.mode")
        assertContains(readme, "local_direct")
        assertContains(readme, "cyberwave.openai.apiKey")
    }

    @Test
    fun securityDocReinforcesThatDirectWebModeIsNotDeployable() {
        val securityDoc = repositoryRoot().resolve("ai-docs/SECURITY.md").readText()

        assertContains(securityDoc, "local development only")
        assertContains(securityDoc, "acceptable production or public deployment security model for the web")
        assertContains(securityDoc, "the key must never be committed")
    }

    private fun repositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()

        while (true) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }

            val parent = current.parent ?: error("Could not locate repository root from $current")
            current = parent
        }
    }

    private fun shouldScanForSecrets(path: Path): Boolean {
        val normalizedPath = path.invariantSeparatorsPathString

        if (IGNORED_PATH_SEGMENTS.any { ignored -> normalizedPath.contains("/$ignored/") || normalizedPath.endsWith("/$ignored") }) {
            return false
        }

        if (path.fileSize() > MAX_SCANNED_FILE_SIZE_BYTES) {
            return false
        }

        return path.name in SCANNED_FILENAMES || path.extension.lowercase() in SCANNED_EXTENSIONS
    }

    private companion object {
        private const val MAX_SCANNED_FILE_SIZE_BYTES: Long = 1_000_000

        private val OPENAI_KEY_PATTERNS =
            listOf(
                Regex("""\bsk-proj-[A-Za-z0-9_-]{20,}\b"""),
                Regex("""\bsk-[A-Za-z0-9]{20,}\b"""),
            )

        private val IGNORED_PATH_SEGMENTS =
            setOf(
                ".git",
                ".gradle",
                ".gradle-user-home",
                ".idea",
                "build",
                "node_modules",
                "kotlin-js-store",
            )

        private val SCANNED_FILENAMES =
            setOf(
                ".gitignore",
                "AGENTS.md",
                "README.md",
            )

        private val SCANNED_EXTENSIONS =
            setOf(
                "env",
                "gradle",
                "html",
                "js",
                "json",
                "kts",
                "kt",
                "md",
                "properties",
                "toml",
                "txt",
                "xml",
                "yaml",
                "yml",
            )
    }
}
