package com.jzb.tower.core

import com.jzb.tower.core.contract.TailnetSecretStore
import com.jzb.tower.core.contract.TowerResourceProvider
import com.jzb.tower.core.model.ProxyKind
import com.jzb.tower.core.model.ProxyNode
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object TestResources : TowerResourceProvider {
    private val root = File(requireNotNull(System.getProperty("tower.repoRoot")), "Tower/Resources")
    override fun openResource(relativePath: String) = root.resolve(relativePath).takeIf(File::isFile)?.inputStream()
    override fun listFiles(relativeDir: String) = root.resolve(relativeDir).list()?.toList().orEmpty()
}

internal class TestSecrets : TailnetSecretStore {
    private val keys = ConcurrentHashMap<String, String>()
    override fun getAuthKey(tailnetId: String): String? = keys[tailnetId]
    override fun setAuthKey(tailnetId: String, authKey: String) { keys[tailnetId] = authKey }
    override fun deleteAuthKey(tailnetId: String) { keys.remove(tailnetId) }
    override fun clearAll() { keys.clear() }
}

internal fun ss(name: String = "东京 01", server: String = "192.0.2.5", id: String = "ss") =
    ProxyNode(id, kind = ProxyKind.SS, name = name, server = server, port = 443, cipher = "aes-128-gcm", password = "example-password")

internal const val sampleUuid = "123e4567-e89b-12d3-a456-426614174000"
