package com.jzb.tower.core.store

import com.jzb.tower.core.model.ClientTarget
import com.jzb.tower.core.model.ExportContentMode
import com.jzb.tower.core.model.GeneratedConfiguration

internal class ConfigurationCache(private val limit: Int = 12, private val charLimit: Int = 8 * 1024 * 1024) {
    data class Key(val revision: Long, val target: ClientTarget, val mode: ExportContentMode)
    private val values = LinkedHashMap<Key, GeneratedConfiguration>(limit, 0.75f, true)
    private var chars = 0
    private fun GeneratedConfiguration.weight() = content.length + (anywhereAJSON?.length ?: 0)

    @Synchronized
    fun get(key: Key): GeneratedConfiguration? = values[key]

    @Synchronized
    fun put(key: Key, value: GeneratedConfiguration) {
        if (value.weight() > charLimit) return
        chars -= values.put(key, value)?.weight() ?: 0
        chars += value.weight()
        val iterator = values.entries.iterator()
        while (values.size > limit || chars > charLimit) {
            chars -= iterator.next().value.weight()
            iterator.remove()
        }
    }

    @Synchronized
    fun clear() {
        values.clear()
        chars = 0
    }
}
