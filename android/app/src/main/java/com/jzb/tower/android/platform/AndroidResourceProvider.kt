package com.jzb.tower.android.platform

import android.content.res.AssetManager
import com.jzb.tower.core.contract.TowerResourceProvider
import java.io.FileNotFoundException
import java.io.InputStream

class AndroidResourceProvider(private val assets: AssetManager) : TowerResourceProvider {
    override fun openResource(relativePath: String): InputStream? {
        if (!valid(relativePath)) return null
        return try {
            assets.open("tower_resources/$relativePath", AssetManager.ACCESS_STREAMING)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun listFiles(relativeDir: String): List<String> {
        if (relativeDir.isNotEmpty() && !valid(relativeDir)) return emptyList()
        val path = if (relativeDir.isEmpty()) "tower_resources" else "tower_resources/$relativeDir"
        return assets.list(path)?.sorted().orEmpty()
    }

    private fun valid(path: String) = '\\' !in path && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
}
