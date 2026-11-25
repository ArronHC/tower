package com.jzb.tower.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jzb.tower.core.model.GeneratedConfiguration
import java.io.File

object AndroidPlatformActions {

    fun readClipboardText(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareConfigurationFile(
        context: Context,
        configuration: GeneratedConfiguration,
        contentOverride: String? = null,
    ): Result<Unit> = runCatching {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        cleanupOldFiles(exportsDir)
        val safeName = configuration.fileName.replace(Regex("""[/\\:*?"<>|]"""), "_").ifBlank {
            "Tower.${configuration.fileExtension}"
        }
        val outFile = File(exportsDir, safeName)
        outFile.writeText(contentOverride ?: configuration.content, Charsets.UTF_8)

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, outFile)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = configuration.mimeType.ifBlank { "text/plain" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, safeName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "分享配置文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareText(context: Context, title: String, text: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Tries each scheme URL in order; returns true if at least one Android client app handled it.
     */
    fun launchClientSchemeUrls(context: Context, schemeUrls: List<String>): Boolean {
        for (raw in schemeUrls) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
                // On Android 11+ some clients might still launch without resolveActivity matching if query fails
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try next scheme URL template
            }
        }
        return false
    }

    fun openExternalUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanupOldFiles(dir: File, maxAgeMs: Long = 10 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }
}
