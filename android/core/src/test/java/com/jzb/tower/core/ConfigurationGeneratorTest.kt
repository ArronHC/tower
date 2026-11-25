package com.jzb.tower.core

import com.jzb.tower.core.model.*
import com.jzb.tower.core.service.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ConfigurationGeneratorTest {
    private val secrets = TestSecrets()
    private val generator = ConfigurationGenerator(TestResources, secrets)

    @Test fun rendersAllNineteenClientsAndTheirSupportedModes() {
        val schemes = RuleSchemeService(TestResources)
        val simple = RuleSchemeParser.parse("[Proxy Group]\nNodes = select, include-all-proxies=true\n[Rule]\nFINAL,Nodes", "Testing")
        val snapshot = AppSnapshot(nodes = listOf(ss()), importedSchemes = listOf(simple), selectedPresetID = simple.id)
        assertEquals(19, ClientTarget.entries.size)
        assertEquals(3, schemes.bundledSchemes.size)
        for (target in ClientTarget.entries) {
            for (mode in ExportContentMode.entries) {
                val result = generator.generate(snapshot, target, mode)
                val meta = ClientCatalog.meta(target)
                assertEquals(target.name, 1, result.supportedCount)
                assertEquals(target.name, 0, result.skippedCount)
                assertTrue(target.name, result.content.isNotBlank())
                assertTrue(result.hasExportableProxies)
                assertEquals(if (!meta.supportsFullConfiguration) ExportContentMode.NODES_ONLY
                    else if (!meta.supportsNodesOnly) ExportContentMode.FULL_CONFIGURATION else mode, result.contentMode)
                when {
                    result.fileExtension == "yaml" -> assertEquals(target.name, 1, (yamlObject(result.content)["proxies"] as List<*>).size)
                    result.fileExtension == "json" -> assertTrue(Json.parseToJsonElement(result.content).jsonObject.getValue("outbounds").jsonArray.isNotEmpty())
                    target == ClientTarget.V2BOX || (target == ClientTarget.SHADOWROCKET && mode == ExportContentMode.NODES_ONLY) ->
                        assertTrue(String(Base64.getDecoder().decode(result.content)).startsWith("ss://"))
                    result.contentMode == ExportContentMode.FULL_CONFIGURATION ->
                        assertTrue(target.name, result.content.contains(if (target == ClientTarget.QUANX) "[server_local]" else "[Proxy]"))
                }
            }
        }
    }

    @Test fun confNamesAreSanitizedAndYamlNamesAreQuoted() {
        val original = "HK // Backup\n[Proxy] a=b,c#d;e \"quote\""
        val snapshot = AppSnapshot(nodes = listOf(ss(original)))
        val surge = generator.generate(snapshot, ClientTarget.SURGE, ExportContentMode.NODES_ONLY)
        assertTrue(surge.content.startsWith("HK ／／ Backup ［Proxy］ a-b，c＃d；e"))
        assertEquals(1, surge.content.lineSequence().count(String::isNotBlank))
        val clash = generator.generate(snapshot, ClientTarget.FLCLASH, ExportContentMode.NODES_ONLY)
        val proxy = (yamlObject(clash.content)["proxies"] as List<*>).single().fields()
        assertEquals(cleanLine(original), proxy["name"])
    }

    @Test fun surfboardRejectsUnrepresentableNodesAndQuanxEnumeratesProxies() {
        val good = ss("Tokyo")
        val wg = good.copy(id = "wg", kind = ProxyKind.WIREGUARD, wireGuardPrivateKey = "private",
            wireGuardPublicKey = "public", wireGuardIPv4 = "10.0.0.2/32", wireGuardDNS = "1.1.1.1", wireGuardMTU = 1280)
        val list = listOf(good, wg,
            wg.copy(id = "missing-dns", wireGuardDNS = null),
            wg.copy(id = "reserved", wireGuardReserved = "1,2,3"),
            good.copy(id = "ssh", kind = ProxyKind.SSH, username = "user"),
            good.copy(id = "trust", kind = ProxyKind.TRUSTTUNNEL, username = "user"),
            good.copy(id = "masque", kind = ProxyKind.MASQUE, username = "user", masque = MasqueOptions(mode = "basicConnectIP")),
            good.copy(id = "vmess", kind = ProxyKind.VMESS, uuid = sampleUuid, transport = "httpupgrade"),
            good.copy(id = "trojan", kind = ProxyKind.TROJAN, transport = "httpupgrade", tls = true),
            good.copy(id = "shadow", shadowTLS = ShadowTlsOptions(3, "front.example.net", "shadow-password")),
            good.copy(id = "snell", kind = ProxyKind.SNELL, version = 6))
        val snapshot = AppSnapshot(nodes = list)
        val surf = generator.generate(snapshot, ClientTarget.SURFBOARD, ExportContentMode.FULL_CONFIGURATION)
        assertEquals(surf.skippedReasons.toString(), 2, surf.supportedCount)
        assertEquals(9, surf.skippedCount)
        assertTrue(surf.content.contains("[WireGuard"))
        val quanx = generator.generate(AppSnapshot(nodes = listOf(good, ss("Paris", id = "other"))), ClientTarget.QUANX,
            ExportContentMode.FULL_CONFIGURATION)
        val automatic = quanx.content.lineSequence().filter { it.startsWith("url-latency-benchmark=") && it.contains("Tokyo") && it.contains("Paris") }.single()
        assertFalse(automatic.contains("direct", true))
        assertFalse(quanx.content.contains("server-tag-regex=.*"))
        assertNull(quanx.directImportURL)
    }

    @Test fun tailnetKeysAreMaskedOrOmittedAndRoutesArePinned() {
        val entry = TailnetConnection("123e4567-e89b-12d3-a456-426614174000", "Home", subnets = listOf("10.0.0.0/24"),
            magicDNSSuffix = "home.ts.net", hasAuthKey = true)
        val testKey = "tskey-auth-testOnly99"
        secrets.setAuthKey(entry.id, testKey)
        val snapshot = AppSnapshot(nodes = listOf(ss("Tokyo")), tailnets = listOf(entry))
        assertEquals("tower-tailnet-123e4567", TailnetConfigurationWriter.stableSlug(entry.id))
        for (target in listOf(ClientTarget.SURGE, ClientTarget.SURGE_MAC, ClientTarget.SURFBOARD, ClientTarget.CLASH, ClientTarget.FLCLASH, ClientTarget.SING_BOX)) {
            val masked = generator.generate(snapshot, target, ExportContentMode.FULL_CONFIGURATION, true)
            val live = generator.generate(snapshot, target, ExportContentMode.FULL_CONFIGURATION, false)
            assertEquals(target.name, 1, masked.supportedCount)
            assertTrue(target.name, masked.content.contains("home.ts.net") && masked.content.contains("10.0.0.0/24"))
            assertTrue(target.name, live.content.contains("100.64.0.0/10") && live.content.contains("fd7a:115c:a1e0::/48"))
            assertFalse(target.name, masked.content.contains(testKey))
            if (target in surgeTargets) {
                assertTrue(live.content.contains("interactive-login = true"))
                assertFalse(live.content.contains("auth-key"))
            } else {
                assertTrue(target.name, masked.content.contains(TailnetConfigurationWriter.MASKED_KEY))
                assertTrue(target.name, live.content.contains(testKey))
            }
        }
        val hiddify = generator.generate(snapshot, ClientTarget.HIDDIFY, ExportContentMode.FULL_CONFIGURATION, false)
        assertFalse(hiddify.content.contains(testKey))
        assertTrue(hiddify.warnings.any { it.contains("Tailscale") })
    }

    @Test fun anywhereAjsonRetainsInsecureNodesAndSafeUriList() {
        val vless = ss("Insecure").copy(id = "vless", kind = ProxyKind.VLESS, tls = true, uuid = sampleUuid,
            transport = "httpupgrade", path = "/vless", skipCertificateVerification = true)
        val snapshot = AppSnapshot(nodes = listOf(vless, ss("Safe")))
        val config = generator.generate(snapshot, ClientTarget.ANYWHERE, ExportContentMode.NODES_ONLY)
        val ajson = Json.parseToJsonElement(config.anywhereAJSON!!).jsonObject
        assertEquals(1, ajson["anywhere"]?.jsonPrimitive?.int)
        val rows = ajson["proxies"]!!.jsonArray
        assertEquals(true, rows[0].jsonObject["security"]?.jsonObject?.get("insecure")?.jsonPrimitive?.boolean)
        assertEquals("httpupgrade", rows[0].jsonObject["transport"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertTrue(rows[1].jsonPrimitive.content.startsWith("ss://"))
        assertFalse(config.content.contains("Insecure"))
        assertTrue(config.warnings.any { it.contains("AJSON") })
    }

    @Test fun singBoxUsesOfflineGeoAsnAndPutsBlockWarningsFirst() {
        val scheme = RuleSchemeParser.parse("""
            [Proxy Group]
            Nodes = select, include-all-proxies=true
            Ads = select, REJECT
            [Rule]
            USER-AGENT,Example,Ads
            GEOIP,CN,DIRECT
            IP-ASN,15169,Nodes
            FINAL,Nodes
        """.trimIndent())
        val result = generator.generate(AppSnapshot(nodes = listOf(ss()), selectedPresetID = scheme.id, importedSchemes = listOf(scheme)),
            ClientTarget.SING_BOX, ExportContentMode.FULL_CONFIGURATION)
        val document = Json.parseToJsonElement(result.content).jsonObject
        val route = document.getValue("route").jsonObject
        val definitions = route.getValue("rule_set").jsonArray.map { it.jsonObject }.associateBy { it.getValue("tag").jsonPrimitive.content }
        val tags = listOf("tower-geoip-cn", "tower-asn-15169")
        assertTrue(tags.all { it in definitions })
        fun ranges(tag: String) = definitions.getValue(tag).getValue("rules").jsonArray.single().jsonObject.getValue("ip_cidr").jsonArray
        assertTrue(ranges(tags[0]).size > 1000)
        assertTrue(ranges(tags[1]).any { it.jsonPrimitive.content.startsWith("8.8.8.") })
        assertTrue(route.getValue("rules").jsonArray.any { it.jsonObject["rule_set"]?.jsonArray?.any { value -> value.jsonPrimitive.content == tags[0] } == true })
        assertTrue(result.warnings.first().startsWith("这些请求不会被拦截"))
        assertTrue(document.containsKey("experimental"))
    }

    @Test fun iconClientsOnlyKeepFlagEmojiInRegionNames() {
        val scheme = RuleSchemeParser.parse("""
            [Proxy Group]
            🎥 Netflix = select, include-all-proxies=true
            🇯🇵 Tokyo = url-test, include-all-proxies=true
            [Rule]
            FINAL,🎥 Netflix
        """.trimIndent())
        val snapshot = AppSnapshot(nodes = listOf(ss()), importedSchemes = listOf(scheme), selectedPresetID = scheme.id)
        val stash = generator.generate(snapshot, ClientTarget.CLASH, ExportContentMode.FULL_CONFIGURATION)
        val groups = yamlObject(stash.content)["proxy-groups"] as List<*>
        assertEquals("Netflix", groups[0].fields()["name"])
        assertTrue(groups[0].fields().string("icon")!!.contains("FluentCircle"))
        assertEquals("🇯🇵 Tokyo", groups[1].fields()["name"])
        assertTrue(groups[1].fields().string("icon")!!.contains("FluentCircle"))
        val surge = generator.generate(snapshot, ClientTarget.SURGE, ExportContentMode.FULL_CONFIGURATION)
        assertFalse(surge.content.lineSequence().first { it.startsWith("🇯🇵 Tokyo =") }.contains("icon-url"))
    }
}
