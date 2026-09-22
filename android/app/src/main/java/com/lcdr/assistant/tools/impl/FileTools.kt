package com.lcdr.assistant.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTools @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun listFiles(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val path = spec.arguments["path"] as? String
            ?: return@withContext ToolResult.Failure("'path' required")
        try {
            val dir = resolveFile(path)
            if (!dir.exists()) return@withContext ToolResult.Failure("Path not found: $path")
            if (!dir.isDirectory) return@withContext ToolResult.Failure("Not a directory: $path")

            val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?: emptyList()
            val result = entries.joinToString("\n") { f ->
                val type = if (f.isDirectory) "D" else "F"
                "[$type] ${f.name} (${humanSize(f.length())})"
            }
            if (result.isEmpty()) ToolResult.Success("Empty directory.")
            else ToolResult.Success(result)
        } catch (e: Exception) {
            ToolResult.Failure("List files failed: ${e.message}")
        }
    }

    suspend fun readFile(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val path = spec.arguments["path"] as? String
            ?: return@withContext ToolResult.Failure("'path' required")
        try {
            val file = resolveFile(path)
            if (!file.exists()) return@withContext ToolResult.Failure("File not found: $path")
            if (file.length() > 512_000) return@withContext ToolResult.Failure("File too large (>512KB)")
            ToolResult.Success(file.readText())
        } catch (e: Exception) {
            ToolResult.Failure("Read file failed: ${e.message}")
        }
    }

    suspend fun writeFile(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val path = spec.arguments["path"] as? String
            ?: return@withContext ToolResult.Failure("'path' required")
        val content = spec.arguments["content"] as? String
            ?: return@withContext ToolResult.Failure("'content' required")
        try {
            val file = resolveFile(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult.Success("Written ${content.length} chars to $path")
        } catch (e: Exception) {
            ToolResult.Failure("Write file failed: ${e.message}")
        }
    }

    suspend fun openFile(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.Main) {
        val path = spec.arguments["path"] as? String
            ?: return@withContext ToolResult.Failure("'path' required")
        try {
            val file = resolveFile(path)
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened $path")
        } catch (e: Exception) {
            ToolResult.Failure("Open file failed: ${e.message}")
        }
    }

    private fun resolveFile(path: String): File {
        return if (path.startsWith("/")) File(path)
        else File(Environment.getExternalStorageDirectory(), path)
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1024} KB"
        else -> "${bytes / 1_048_576} MB"
    }
}
