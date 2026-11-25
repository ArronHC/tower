package com.jzb.tower.android.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jzb.tower.core.contract.TailnetSecretStore
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidTailnetSecretStore(context: Context) : TailnetSecretStore {
    private val context = context.applicationContext
    private val keyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    private fun fileName(id: String): String = PREFIX + MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun key(): SecretKey? = keyStore.getKey(KEY_ALIAS, null) as? SecretKey

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        generateKey()
    }

    @Synchronized
    override fun getAuthKey(tailnetId: String): String? {
        val content = try {
            context.openFileInput(fileName(tailnetId)).use { stream ->
                val result = ByteArray(4096)
                var size = 0
                while (size < result.size) {
                    val count = stream.read(result, size, result.size - size)
                    if (count < 0) break
                    size += count
                }
                check(size < result.size) { "Tailscale 凭据文件过大" }
                result.copyOf(size)
            }
        } catch (_: FileNotFoundException) {
            return null
        }
        check(content.size > IV_BYTES + 16 && content[0] == VERSION) { "无法读取 Tailscale 凭据" }
        val master = key() ?: throw IllegalStateException("无法读取本机 Tailscale 加密密钥")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, content, 1, IV_BYTES))
        cipher.updateAAD(tailnetId.toByteArray())
        val clear = cipher.doFinal(content, 1 + IV_BYTES, content.size - 1 - IV_BYTES)
        return try { String(clear, Charsets.UTF_8) } finally { clear.fill(0) }
    }

    @Synchronized
    override fun setAuthKey(tailnetId: String, authKey: String) {
        require(authKey.toByteArray().size in 1..4000) { "无效的 Tailscale 凭据长度" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key() ?: createKey())
        cipher.updateAAD(tailnetId.toByteArray())
        val clear = authKey.toByteArray()
        val encrypted = try { cipher.doFinal(clear) } finally { clear.fill(0) }
        check(cipher.iv.size == IV_BYTES)
        val name = fileName(tailnetId)
        val temporary = "$name-${UUID.randomUUID()}.tmp"
        val temp = context.getFileStreamPath(temporary).toPath()
        val target = context.getFileStreamPath(name).toPath()
        try {
            context.openFileOutput(temporary, Context.MODE_PRIVATE).use {
                it.write(byteArrayOf(VERSION))
                it.write(cipher.iv)
                it.write(encrypted)
                it.fd.sync()
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    @Synchronized
    override fun deleteAuthKey(tailnetId: String) {
        Files.deleteIfExists(context.getFileStreamPath(fileName(tailnetId)).toPath())
    }

    @Synchronized
    override fun clearAll() {
        context.filesDir.listFiles { file: File -> file.name.startsWith(PREFIX) }.orEmpty().forEach {
            Files.deleteIfExists(it.toPath())
        }
    }

    private companion object {
        const val KEY_ALIAS = "tower_tailnet_master_key"
        const val PREFIX = "tower-tailnet-secret-"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
    }
}
