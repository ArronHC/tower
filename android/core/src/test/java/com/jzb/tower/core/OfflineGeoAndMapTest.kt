package com.jzb.tower.core

import com.jzb.tower.core.model.*
import com.jzb.tower.core.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.net.InetAddress

class OfflineGeoAndMapTest {
    private val country = IPCountryDatabase(TestResources)
    private val asn = IPASNDatabase(TestResources)

    @Test fun readsRealIPv4IPv6AndAsnNamePool() {
        assertTrue(country.version != "bundled")
        assertEquals("US", country.countryCode("8.8.8.8"))
        assertEquals("CN", country.countryCode("2400:3200::1"))
        assertEquals("US", country.countryCode("2001:4860:4860::8888"))
        assertEquals(NetworkOrganization(15169, "Google LLC"), asn.organization("8.8.8.8"))
        assertEquals(NetworkOrganization(13335, "Cloudflare, Inc."), asn.organization("2606:4700:4700::1111"))
        assertNull(country.countryCode("localhost"))
        assertNull(asn.organization("::1"))
    }

    @Test fun expandsRealRangesIntoAlignedCidrsWithoutCoveringOtherAddresses() {
        val cn = country.cidrsForCountry("cn")
        val google = asn.cidrsForAsn(15169)
        assertTrue(cn.any { contains(it, "223.5.5.5") })
        assertTrue(cn.any { contains(it, "2400:3200::1") })
        assertFalse(cn.any { contains(it, "8.8.8.8") })
        assertTrue(google.any { contains(it, "8.8.8.8") })
        assertTrue(google.any { contains(it, "2001:4860:4860::8888") })
        assertFalse(google.any { contains(it, "1.1.1.1") })
        for (cidr in cn + google) {
            val bytes = literalIP(cidr.substringBefore('/'))!!
            val prefix = cidr.substringAfter('/').toInt()
            assertEquals(cidr, BigInteger.ZERO, BigInteger(1, bytes).and(BigInteger.ONE.shiftLeft(bytes.size * 8 - prefix).subtract(BigInteger.ONE)))
        }
        assertTrue(asn.cidrsForAsn(0).isEmpty())
        assertTrue(country.cidrsForCountry("???").isEmpty())
    }

    @Test fun regionPrecedenceAndAsnNeverGuessFromHostname() = runBlocking {
        val address = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        val resolver = NodeRegionResolver(country, asn) { host ->
            if (host.startsWith("unlisted")) listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) else listOf(address)
        }
        val known = ss("🇸🇬 Tokyo", "france.example.jp")
        val resolved = resolver.resolve(listOf(
            known.copy(id = "override", countryOverride = "CA"),
            known.copy(id = "flag"),
            known.copy(id = "city", name = "Johannesburg | 01"),
            known.copy(id = "code", name = "TW-01"),
            known.copy(id = "ip", name = "Alpha"),
            known.copy(id = "unknown", name = "Alpha", server = "unlisted.tokyo.jp"),
            known.copy(id = "unlabeled", name = "unlisted.tokyo.jp", server = "unlisted.tokyo.jp"),
        ))
        assertEquals("CA", resolved.getValue("override").countryCode)
        assertEquals(RegionSource.OVERRIDE, resolved.getValue("override").regionSource)
        assertEquals("SG", resolved.getValue("flag").countryCode)
        assertEquals(15169L, resolved.getValue("flag").asn)
        assertEquals("ZA", resolved.getValue("city").countryCode)
        assertEquals("TW", resolved.getValue("code").countryCode)
        assertEquals("US", resolved.getValue("ip").countryCode)
        assertEquals(RegionSource.IP, resolved.getValue("ip").regionSource)
        assertEquals(RegionSource.UNKNOWN, resolved.getValue("unknown").regionSource)
        assertEquals(RegionSource.UNKNOWN, resolved.getValue("unlabeled").regionSource)
        assertNull(CountryTable.codeFromName("ws 15 GB"))
        assertTrue(CountryTable.countries.size >= 187)
    }

    @Test fun loadsRealWorldMapLayers() {
        val map = WorldDotMapLoader(TestResources).load()
        assertEquals(220, map.columns)
        assertEquals(149, map.rows)
        assertTrue(map.landRows.all { it.length == 220 })
        assertTrue(map.countryCells.getValue("CN").size > 100)
        assertTrue(map.countryCells.getValue("JP").all { it in 0 until 220 * 149 })
    }

    @Test fun udpOnlyNodesNeverGetTcpProbe() = runBlocking {
        val result = NodeLatencyService(20).probe(listOf(ss().copy(kind = ProxyKind.WIREGUARD)), NodeLatencyTestMode.TCP)
        assertFalse(result.getValue("ss").isApplicable)
        assertNull(result.getValue("ss").method)
    }

    private fun contains(cidr: String, address: String): Boolean {
        val left = literalIP(cidr.substringBefore('/')) ?: return false
        val right = literalIP(address) ?: return false
        if (left.size != right.size) return false
        val bits = left.size * 8 - cidr.substringAfter('/').toInt()
        return BigInteger(1, left).shiftRight(bits) == BigInteger(1, right).shiftRight(bits)
    }
}
