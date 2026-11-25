package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class NodeLatencyService(private val timeoutMs: Int = 1500) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(24)

    suspend fun probe(nodes: List<ProxyNode>, mode: NodeLatencyTestMode): Map<String, NodeLatencyMeasurement> =
        coroutineScope {
            nodes.map { node -> async(dispatcher) { node.id to probe(node, mode) } }.awaitAll().toMap()
        }

    private fun probe(node: ProxyNode, mode: NodeLatencyTestMode): NodeLatencyMeasurement {
        val now = System.currentTimeMillis()
        if (mode == NodeLatencyTestMode.TCP && node.kind.isUdpOnly) {
            return NodeLatencyMeasurement(testedAtEpochMs = now, isApplicable = false)
        }
        val method = if (mode == NodeLatencyTestMode.TCP) NodeProbeMethod.TCP else NodeProbeMethod.ICMP
        val address = try {
            InetAddress.getByName(node.server)
        } catch (_: IOException) {
            return NodeLatencyMeasurement(method = method, testedAtEpochMs = now, errorMessage = "无法解析服务器")
        }
        if (mode != NodeLatencyTestMode.TCP) {
            val result = measure { address.isReachable(timeoutMs) } ?: ping(address)
            if (result != null) return NodeLatencyMeasurement(result, NodeProbeMethod.ICMP, now)
            if (mode == NodeLatencyTestMode.ICMP || node.kind.isUdpOnly) {
                return NodeLatencyMeasurement(method = NodeProbeMethod.ICMP, testedAtEpochMs = now, errorMessage = "ICMP 无响应")
            }
        }
        val result = measure { Socket().use { it.connect(InetSocketAddress(address, node.port), timeoutMs) }; true }
        return NodeLatencyMeasurement(result, NodeProbeMethod.TCP, now, if (result == null) "TCP 无响应" else null)
    }

    private fun ping(address: InetAddress): Int? = measure {
        val ipv6 = address.address.size == 16
        val process = ProcessBuilder("ping", *if (ipv6) arrayOf("-6", "-n", "-c", "1", address.hostAddress)
            else arrayOf("-n", "-c", "1", address.hostAddress)).redirectErrorStream(true).start()
        try {
            process.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS) && process.exitValue() == 0
        } finally {
            process.destroyForcibly()
            process.inputStream.close()
        }
    }

    private inline fun measure(action: () -> Boolean): Int? {
        val start = System.nanoTime()
        return try {
            if (action()) ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1) else null
        } catch (_: IOException) {
            null
        }
    }
}
