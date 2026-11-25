package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TowerResourceProvider
import java.math.BigInteger
import java.net.InetAddress
import java.net.Inet6Address
import java.util.Locale

internal fun literalIP(text: String): ByteArray? {
    val value = text.trim().removeSurrounding("[", "]").substringBefore('%')
    if (':' in value) {
        if (!value.matches(Regex("[A-Fa-f0-9:.]+"))) return null
        val bytes = runCatching { InetAddress.getByName(value).address }.getOrNull() ?: return null
        if (bytes.size == 16) return bytes
        return ByteArray(16).apply {
            this[10] = -1
            this[11] = -1
            bytes.copyInto(this, 12)
        }
    }
    val parts = value.split('.')
    if (parts.size != 4) return null
    return ByteArray(4) { index ->
        val part = parts[index]
        if (!part.matches(Regex("[0-9]{1,3}"))) return null
        (part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null).toByte()
    }
}

internal fun addressForBytes(bytes: ByteArray): InetAddress =
    if (bytes.size == 16) Inet6Address.getByAddress(null, bytes, -1) else InetAddress.getByAddress(bytes)

internal class IPRangeIndex(
    private val bytes: ByteArray,
    private val addressWidth: Int,
    metadataWidth: Int,
) {
    private val stride = 2 * addressWidth + metadataWidth
    private val count = if (bytes.size % stride == 0) bytes.size / stride else 0

    private fun compare(address: ByteArray, offset: Int): Int {
        for (index in address.indices) {
            val delta = (address[index].toInt() and 255) - (bytes[offset + index].toInt() and 255)
            if (delta != 0) return delta
        }
        return 0
    }

    fun find(address: ByteArray): Int? {
        var low = 0
        var high = count
        while (low < high) {
            val mid = low + (high - low) / 2
            val start = mid * stride
            when {
                compare(address, start) < 0 -> high = mid
                compare(address, start + addressWidth) > 0 -> low = mid + 1
                else -> return start + 2 * addressWidth
            }
        }
        return null
    }

    fun unsigned(offset: Int, width: Int): Long {
        var number = 0L
        for (index in offset until offset + width) number = (number shl 8) or (bytes[index].toLong() and 255)
        return number
    }

    fun cidrs(matches: (Int) -> Boolean): List<String> = buildList {
        var first: BigInteger? = null
        var last = BigInteger.ZERO
        fun emit() {
            var start = first ?: return
            val bits = addressWidth * 8
            while (start <= last) {
                val available = last.subtract(start).add(BigInteger.ONE).bitLength() - 1
                val alignment = if (start.signum() == 0) bits else start.lowestSetBit
                val power = minOf(available, alignment)
                val address = start.toByteArray()
                val fixed = ByteArray(addressWidth)
                val copied = minOf(address.size, addressWidth)
                address.copyInto(fixed, addressWidth - copied, address.size - copied)
                add(addressForBytes(fixed).hostAddress + "/" + (bits - power))
                start = start.add(BigInteger.ONE.shiftLeft(power))
            }
        }
        for (index in 0 until count) {
            val offset = index * stride
            if (!matches(offset + 2 * addressWidth)) continue
            val start = BigInteger(1, bytes.copyOfRange(offset, offset + addressWidth))
            val end = BigInteger(1, bytes.copyOfRange(offset + addressWidth, offset + 2 * addressWidth))
            if (first == null || last.add(BigInteger.ONE) != start) {
                emit()
                first = start
            }
            last = end
        }
        emit()
    }
}

class IPCountryDatabase(private val resources: TowerResourceProvider) {
    private val ipv4 by lazy { IPRangeIndex(resources.readBytes("IPCountry/IPCountryIPv4.bin") ?: byteArrayOf(), 4, 2) }
    private val ipv6 by lazy { IPRangeIndex(resources.readBytes("IPCountry/IPCountryIPv6.bin") ?: byteArrayOf(), 16, 2) }
    private val cache = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 16
    }
    val version by lazy { resources.readText("IPCountry/IPCountryVersion.txt")?.trim().orEmpty().ifEmpty { "bundled" } }

    fun countryCode(address: String): String? = literalIP(address)?.let(::countryCode)
    internal fun countryCode(address: ByteArray): String? {
        val index = if (address.size == 4) ipv4 else ipv6
        val offset = index.find(address) ?: return null
        val code = index.unsigned(offset, 2).toInt()
        return "${(code shr 8).toChar()}${(code and 255).toChar()}".takeIf { it.all { char -> char in 'A'..'Z' } }
    }

    @Synchronized
    fun cidrsForCountry(country: String): List<String> {
        val code = country.trim().uppercase(Locale.ROOT)
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return emptyList()
        val encoded = ((code[0].code shl 8) or code[1].code).toLong()
        return cache.getOrPut(code) {
            ipv4.cidrs { ipv4.unsigned(it, 2) == encoded } + ipv6.cidrs { ipv6.unsigned(it, 2) == encoded }
        }
    }
}
