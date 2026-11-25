package com.jzb.tower.core.store

import com.jzb.tower.core.model.AppSnapshot
import com.jzb.tower.core.service.RuleMatchingReplay
import com.jzb.tower.core.service.TailnetConfigurationWriter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

class PersistenceStore(private val directory: File) {
    private val snapshotFile get() = File(directory, "state.json")

    fun read(): AppSnapshot? = readDocument()?.snapshot

    internal fun readDocument(): Document? {
        if (!snapshotFile.isFile) return null
        privatePermissions(snapshotFile.toPath())
        return decodeDocument(snapshotFile.readText(Charsets.UTF_8))
    }

    fun write(snapshot: AppSnapshot) = writeDocument(snapshot, emptyList())

    internal fun writeDocument(snapshot: AppSnapshot, replays: List<RuleMatchingReplay>) {
        Files.createDirectories(directory.toPath())
        val path = directory.toPath()
        val temp = try {
            Files.createTempFile(path, ".tower-state-", ".tmp", PosixFilePermissions.asFileAttribute(ownerPermissions))
        } catch (_: UnsupportedOperationException) {
            Files.createTempFile(path, ".tower-state-", ".tmp")
        }
        try {
            privatePermissions(temp)
            FileOutputStream(temp.toFile()).use { stream ->
                stream.write(encodeDocument(snapshot, replays).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(temp, snapshotFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun privatePermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(path, ownerPermissions)
        } catch (_: UnsupportedOperationException) {
            val file = path.toFile()
            check(file.setReadable(false, false) && file.setWritable(false, false) &&
                file.setReadable(true, true) && file.setWritable(true, true)) { "无法保护本地配置文件" }
        }
    }

    internal data class Document(val snapshot: AppSnapshot, val replays: List<RuleMatchingReplay>)

    companion object {
        private val ownerPermissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val keyPattern = Regex("tskey-auth-[A-Za-z0-9_-]+")
        private val keyFields = setOf("authkey", "auth-key", "auth_key", "tailscaleauthkey", "tailnetauthkey")

        private const val REPLAY_KEY = "ruleMatchingReplays"
        private val replaySerializer = ListSerializer(RuleMatchingReplay.serializer())

        fun encode(snapshot: AppSnapshot): String = encodeDocument(snapshot, emptyList())

        internal fun encodeDocument(snapshot: AppSnapshot, replays: List<RuleMatchingReplay>): String {
            val value = codec.encodeToJsonElement(AppSnapshot.serializer(), sanitized(snapshot)).jsonObject
            val document = if (replays.isEmpty()) value else
                JsonObject(value + (REPLAY_KEY to codec.encodeToJsonElement(replaySerializer, replays)))
            return codec.encodeToString(JsonElement.serializer(), scrub(document))
        }

        fun decode(text: String): AppSnapshot = decodeDocument(text).snapshot

        internal fun decodeDocument(text: String): Document {
            val value = scrub(codec.parseToJsonElement(text)).jsonObject
            val decoded = codec.decodeFromJsonElement(AppSnapshot.serializer(), value)
            require(decoded.version == 1) { "不支持此备份版本" }
            val replays = value[REPLAY_KEY]?.let { codec.decodeFromJsonElement(replaySerializer, it) }.orEmpty()
            return Document(sanitized(decoded), replays)
        }

        private fun sanitized(snapshot: AppSnapshot) = snapshot.copy(tailnets = snapshot.tailnets.map {
            TailnetConfigurationWriter.normalize(it.copy(hasAuthKey = false))
        })

        private fun scrub(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.entries.filterNot { it.key.lowercase() in keyFields }.associate { it.key to scrub(it.value) })
            is JsonArray -> JsonArray(value.map(::scrub))
            is JsonPrimitive -> if (value.isString) JsonPrimitive(value.content.replace(keyPattern, TailnetConfigurationWriter.MASKED_KEY)) else value
        }
    }
}
