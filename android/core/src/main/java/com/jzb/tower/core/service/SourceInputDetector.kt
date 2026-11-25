package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SourceInputDetector {
    fun detect(input: String): SourceDetectionResult {
        val text = SubscriptionParser.bodyText(input)
        val single = text.takeUnless { it.any(Char::isWhitespace) }
        val url = single?.toHttpUrlOrNull()
        if (url != null && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            (!hasExplicitPort(single) || url.encodedPath != "/" || url.query != null)) {
            return SourceDetectionResult(DetectedSourceKind.SUBSCRIPTION_URL, "订阅链接", normalizedURL = url.toString())
        }
        val sections = iniSections(text)
        val parsed = runCatching { SubscriptionParser.parse(text) }.getOrNull()
        val count = parsed?.nodes?.size ?: 0
        val kind = when {
            Regex("(?m)^\\s*proxies\\s*:").containsMatchIn(text) -> DetectedSourceKind.CLASH_YAML
            "proxy" in sections -> DetectedSourceKind.SURGE_CONFIG
            "custom" in sections || "rule" in sections || "proxy group" in sections ||
                Regex("(?m)^\\s*(?:rules|rule-providers|proxy-groups)\\s*:|^\\s*(?:ruleset|custom_proxy_group)\\s*=").containsMatchIn(text) ->
                DetectedSourceKind.RULE_SCHEME
            count > 0 -> DetectedSourceKind.PROXY_NODES
            else -> DetectedSourceKind.UNKNOWN
        }
        val summary = when (kind) {
            DetectedSourceKind.CLASH_YAML -> "Clash YAML"
            DetectedSourceKind.SURGE_CONFIG -> "Surge 配置"
            DetectedSourceKind.RULE_SCHEME -> "规则方案"
            DetectedSourceKind.PROXY_NODES -> "代理节点"
            else -> "无法识别输入"
        }
        return SourceDetectionResult(kind, summary, count)
    }

    private fun hasExplicitPort(value: String) = explicitPort(value) != -1
    private fun explicitPort(value: String) = runCatching { java.net.URI(value).port }.getOrDefault(-1)
}
