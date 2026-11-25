package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class NodeRegionResolver(
    private val countries: IPCountryDatabase,
    private val asns: IPASNDatabase,
    private val dns: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(16)

    fun fromName(node: ProxyNode): NodeRuntimeInfo = describe(node, null)

    private fun describe(node: ProxyNode, address: InetAddress?): NodeRuntimeInfo {
        val override = node.countryOverride?.let(CountryTable::get)
        val named = if (override == null) CountryTable.codeFromNodeName(node)?.let(CountryTable::get) else null
        val offline = if (override == null && named == null) address?.address?.let(countries::countryCode)?.let(CountryTable::get) else null
        val country = override ?: named ?: offline
        val origin = when {
            override != null -> RegionSource.OVERRIDE
            named != null -> RegionSource.NAME
            offline != null -> RegionSource.IP
            else -> RegionSource.UNKNOWN
        }
        val org = address?.address?.let(asns::organization)
        return NodeRuntimeInfo(node.id, country?.code, country?.chineseName, country?.englishName,
            country?.flag, country?.latitude, country?.longitude, origin, address?.hostAddress, org?.asn, org?.name)
    }

    suspend fun resolve(nodes: List<ProxyNode>): Map<String, NodeRuntimeInfo> = coroutineScope {
        val hosts = nodes.map { it.server }.distinct().associateWith { host ->
            async(dispatcher) {
                literalIP(host)?.let(::addressForBytes) ?: try {
                    val addresses = dns(host)
                    addresses.firstOrNull { countries.countryCode(it.address) != null } ?: addresses.firstOrNull()
                } catch (_: UnknownHostException) {
                    null
                }
            }
        }
        nodes.map { node -> async(dispatcher) { node.id to describe(node, hosts.getValue(node.server).await()) } }
            .awaitAll().toMap()
    }
}
