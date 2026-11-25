package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TowerResourceProvider

data class NetworkOrganization(val asn: Long, val name: String)

class IPASNDatabase(private val resources: TowerResourceProvider) {
    private val ipv4 by lazy { IPRangeIndex(resources.readBytes("IPCountry/IPASNIPv4.bin") ?: byteArrayOf(), 4, 10) }
    private val ipv6 by lazy { IPRangeIndex(resources.readBytes("IPCountry/IPASNIPv6.bin") ?: byteArrayOf(), 16, 10) }
    private val names by lazy { resources.readBytes("IPCountry/IPASNNames.bin") ?: byteArrayOf() }
    private val cache = object : LinkedHashMap<Long, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<String>>?) = size > 16
    }

    fun organization(address: String): NetworkOrganization? = literalIP(address)?.let(::organization)
    internal fun organization(address: ByteArray): NetworkOrganization? {
        val index = if (address.size == 4) ipv4 else ipv6
        val record = index.find(address) ?: return null
        val asn = index.unsigned(record, 4)
        val offset = index.unsigned(record + 4, 4)
        val length = index.unsigned(record + 8, 2).toInt()
        if (asn == 0L || length == 0 || offset + length > names.size) return null
        return NetworkOrganization(asn, String(names, offset.toInt(), length, Charsets.UTF_8))
    }

    @Synchronized
    fun cidrsForAsn(asn: Long): List<String> {
        if (asn !in 1..0xffffffffL) return emptyList()
        return cache.getOrPut(asn) {
            ipv4.cidrs { ipv4.unsigned(it, 4) == asn } + ipv6.cidrs { ipv6.unsigned(it, 4) == asn }
        }
    }
}
