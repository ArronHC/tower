package com.jzb.tower.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.DnsProtectionMode
import com.jzb.tower.core.model.NodeLatencyMeasurement
import com.jzb.tower.core.model.NodeLatencyTestMode
import com.jzb.tower.core.model.NodeProbeMethod
import com.jzb.tower.core.model.ProxyKind
import com.jzb.tower.core.model.RuleSchemeGroupKind
import java.net.URI
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        val value = bytes.toDouble()
        return when {
            value >= tb -> String.format(Locale.US, "%.1f TB", value / tb)
            value >= gb -> String.format(Locale.US, "%.0f GB", value / gb)
            value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
            value >= kb -> String.format(Locale.US, "%.1f KB", value / kb)
            else -> "$bytes B"
        }
    }

    fun formatBytesDetailed(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        val value = bytes.toDouble()
        return when {
            value >= tb -> String.format(Locale.US, "%.2f TiB", value / tb)
            value >= gb -> String.format(Locale.US, "%.1f GB", value / gb)
            value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
            value >= kb -> String.format(Locale.US, "%.1f KB", value / kb)
            else -> "$bytes B"
        }
    }

    fun formatCount(count: Int): String =
        NumberFormat.getIntegerInstance(Locale.US).format(count)

    fun formatRelativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = nowMs - epochMs
        if (diff < 0) return "刚刚"
        val seconds = diff / 1000L
        val minutes = seconds / 60L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            seconds < 45 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 30 -> "${days}天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))
        }
    }

    fun formatExpiryCountdown(expiresAtEpochMs: Long, nowMs: Long = System.currentTimeMillis()): Pair<String, Boolean> {
        val diffMs = expiresAtEpochMs - nowMs
        val days = (diffMs / (1000L * 60L * 60L * 24L)).toInt()
        return when {
            diffMs <= 0L -> "已过期 ${abs(days)} 天" to true
            days == 0 -> "今日到期" to true
            days <= 3 -> "${days} 天后到期" to true
            else -> "${days} 天到期" to false
        }
    }

    fun formatDate(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

    fun safeHost(urlString: String): String {
        return try {
            URI(urlString).host?.takeIf { it.isNotBlank() } ?: urlString
        } catch (_: Exception) {
            urlString
        }
    }

    fun proxyKindLabel(kind: ProxyKind): String =
        ClientCatalog.PROXY_KIND_LABELS[kind] ?: kind.rawValue.uppercase()

    fun ruleGroupKindLabel(kind: RuleSchemeGroupKind): String = when (kind) {
        RuleSchemeGroupKind.SELECT -> "手动选择"
        RuleSchemeGroupKind.URL_TEST -> "延迟优选"
        RuleSchemeGroupKind.FALLBACK -> "故障转移"
        RuleSchemeGroupKind.LOAD_BALANCE -> "负载均衡"
        RuleSchemeGroupKind.SMART -> "Smart 智能"
        RuleSchemeGroupKind.SSID -> "SSID 策略"
        RuleSchemeGroupKind.RELAY -> "链式代理"
        RuleSchemeGroupKind.UNSUPPORTED -> "自定义"
    }

    fun dnsProtectionTitle(mode: DnsProtectionMode): String = when (mode) {
        DnsProtectionMode.STANDARD -> "标准保护"
        DnsProtectionMode.STRICT -> "严格模式"
        DnsProtectionMode.FOLLOW_SCHEME -> "跟随方案"
    }

    fun latencyModeTitle(mode: NodeLatencyTestMode): String = when (mode) {
        NodeLatencyTestMode.AUTOMATIC -> "自动（ICMP 优先）"
        NodeLatencyTestMode.ICMP -> "ICMP Ping"
        NodeLatencyTestMode.TCP -> "TCP 握手"
    }

    /** Exact MapLatencyBand colors matching WorldDotMapView.swift */
    fun mapBandColor(milliseconds: Int?, isApplicable: Boolean = true, isDark: Boolean = false, selected: Boolean = false): Color {
        val (r, g, b) = when {
            !isApplicable -> Triple(0.68, 0.28, 0.48)
            milliseconds == null -> Triple(0.48, 0.59, 0.69)
            milliseconds <= 100 -> Triple(0.06, 0.65, 0.49)
            milliseconds <= 200 -> Triple(0.20, 0.53, 0.84)
            milliseconds <= 350 -> Triple(0.83, 0.58, 0.14)
            else -> Triple(0.89, 0.36, 0.24)
        }
        fun component(value: Double): Float {
            val base = if (isDark) value + (1.0 - value) * 0.24 else value
            val adjusted = if (selected) base * (if (isDark) 0.82 else 0.68) else base
            return adjusted.toFloat().coerceIn(0f, 1f)
        }
        return Color(red = component(r), green = component(g), blue = component(b))
    }

    @Composable
    fun latencyColor(milliseconds: Int?, isApplicable: Boolean = true): Color {
        val palette = TowerTheme.palette
        return when {
            !isApplicable -> palette.unreachableRose
            milliseconds == null -> palette.unreachableRose
            milliseconds <= 100 -> palette.emerald
            milliseconds <= 200 -> palette.lakeBlue
            milliseconds <= 350 -> palette.amber
            else -> palette.coral
        }
    }

    fun latencyBadgeText(measurement: NodeLatencyMeasurement?): String {
        if (measurement == null) return "未测速"
        if (!measurement.isApplicable) return "不支持"
        val ms = measurement.milliseconds
        val tag = when (measurement.method) {
            NodeProbeMethod.ICMP -> "ICMP"
            NodeProbeMethod.TCP -> "TCP"
            null -> ""
        }
        return if (ms != null) {
            if (tag.isNotEmpty()) "$ms ms · $tag" else "$ms ms"
        } else {
            if (tag.isNotEmpty()) "超时 · $tag" else "超时"
        }
    }
}
