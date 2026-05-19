package com.tmuxes.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.tmuxes.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bundles every diagnostic artefact a developer needs (breadcrumbs, crash
 * log, sanitised config, manifest) into one zip the user can share.
 *
 * Designed for debug builds — the contained `breadcrumbs.txt` is built
 * from [AppLogger.snapshotBreadcrumbs], which only fills in debug builds
 * (release builds keep only WARN/ERROR breadcrumbs). The bundle therefore
 * carries enough trace context to reconstruct the user's last ~256 events
 * before whatever they reported.
 *
 * Why not multiple separate share intents? The crash log alone is rarely
 * enough to triage a bug — you need the in-memory trace AND the user's
 * config AND the device manifest. Bundling them into one zip means one
 * share action covers everything and the developer always gets the
 * full picture.
 */
object DebugBundle {

    /**
     * Build a zip in the app's cache dir containing every diagnostic
     * artefact, then return a [FileProvider]-backed share Intent the
     * caller can pass to `Intent.createChooser` or `startActivity`.
     */
    fun export(context: Context): Intent {
        val ctx = context.applicationContext
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zipFile = File(ctx.cacheDir, "tmuxes-debug-$timestamp.zip")

        AppLogger.i(AppLogger.Category.LIFECYCLE) { "debug.bundle → ${zipFile.name}" }

        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zip ->
            zipEntry(zip, "manifest.txt", buildManifest(ctx))
            zipEntry(zip, "breadcrumbs.txt", buildBreadcrumbs())
            zipEntry(zip, "crash_log.txt",
                safeReadText(CrashLogWriter.getCrashLogFile()))
            zipEntry(zip, "global_settings.yaml",
                safeReadText(File(ctx.filesDir, "config/global_settings.yaml")))
            zipEntry(zip, "servers.yaml.redacted",
                redactServerYaml(safeReadText(File(ctx.filesDir, "config/servers.yaml"))))
        }

        AppLogger.i(AppLogger.Category.LIFECYCLE) {
            "debug.bundle ← ${zipFile.length()} bytes"
        }

        val authority = "${ctx.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(ctx, authority, zipFile)
        } catch (t: Throwable) {
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "debug.bundle FileProvider lookup failed authority=$authority cause='${t.message}'"
            }
            throw t
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "tmuxes debug bundle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildManifest(ctx: Context): String = buildString {
        appendLine("tmuxes debug bundle")
        appendLine("generated: ${Date()}")
        appendLine()
        appendLine("--- app ---")
        appendLine("packageName: ${ctx.packageName}")
        appendLine("versionName: ${BuildConfig.VERSION_NAME}")
        appendLine("versionCode: ${BuildConfig.VERSION_CODE}")
        appendLine("buildType:   ${BuildConfig.BUILD_TYPE}")
        appendLine("debug:       ${BuildConfig.DEBUG}")
        appendLine()
        appendLine("--- device ---")
        appendLine("manufacturer: ${android.os.Build.MANUFACTURER}")
        appendLine("model:        ${android.os.Build.MODEL}")
        appendLine("device:       ${android.os.Build.DEVICE}")
        appendLine("android:      ${android.os.Build.VERSION.RELEASE}")
        appendLine("sdkInt:       ${android.os.Build.VERSION.SDK_INT}")
        appendLine("supportedAbis:${android.os.Build.SUPPORTED_ABIS.joinToString(",")}")
        appendLine()
        appendLine("--- logger state ---")
        for (cat in AppLogger.Category.values()) {
            appendLine("${cat.name}: ${AppLogger.levelOf(cat)}")
        }
    }

    private fun buildBreadcrumbs(): String {
        val crumbs = try { AppLogger.snapshotBreadcrumbs() } catch (_: Throwable) { emptyList() }
        return if (crumbs.isEmpty()) "(no breadcrumbs)\n"
        else crumbs.joinToString(separator = "\n", postfix = "\n")
    }

    private fun safeReadText(file: File?): String = try {
        if (file != null && file.exists()) file.readText() else "(file not present)\n"
    } catch (t: Throwable) {
        "(read failed: ${t.javaClass.simpleName})\n"
    }

    /**
     * Strip credentials from servers.yaml before zipping. Lines whose
     * key is `password`, `passphrase`, or `private_key_data` are
     * replaced with `<redacted>` so a shared bundle never leaks SSH
     * material.
     */
    private fun redactServerYaml(text: String): String {
        if (text.isBlank()) return text
        val redactKeys = setOf("password", "passphrase", "private_key_data")
        val sb = StringBuilder()
        for (line in text.lines()) {
            val trimmed = line.trimStart()
            val matched = redactKeys.firstOrNull { trimmed.startsWith("$it:") }
            if (matched != null) {
                val indent = line.substring(0, line.length - trimmed.length)
                sb.append(indent).append(matched).append(": <redacted>\n")
            } else {
                sb.append(line).append('\n')
            }
        }
        return sb.toString()
    }

    private fun zipEntry(zip: ZipOutputStream, name: String, content: String) {
        try {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        } catch (t: Throwable) {
            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                "debug.bundle entry '$name' ✗ cause='${t.message}'"
            }
        }
    }
}
