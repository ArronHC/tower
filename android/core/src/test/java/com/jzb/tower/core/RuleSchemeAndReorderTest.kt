package com.jzb.tower.core

import com.jzb.tower.core.engine.TowerRepositoryImpl
import com.jzb.tower.core.model.*
import com.jzb.tower.core.service.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuleSchemeAndReorderTest {
    @get:Rule val temp = TemporaryFolder()
    private val service = RuleSchemeService(TestResources)

    @Test fun loadsAllBundledAclSchemesAndTheirRulesFullyOffline() {
        assertEquals(listOf("acl4ssr-default", "acl4ssr-full", "acl4ssr-mini"), service.bundledSchemes.map { it.id })
        for (scheme in service.bundledSchemes) {
            assertTrue(scheme.isBundled)
            assertTrue(scheme.groups.first().name.contains("节点选择"))
            assertTrue(scheme.rulesets.any { it.resource is RuleSchemeResource.Bundled })
            assertFalse(scheme.rulesets.any { it.resource is RuleSchemeResource.Remote })
            assertTrue(scheme.rulesets.all { service.lines(it).isNotEmpty() })
            assertTrue(scheme.rulesets.last().isFinal())
        }
        val acl = service.searchCatalog("Netflix", "acl4ssr", 20)
        val bm = service.searchCatalog("Netflix", "blackmatrix7", 20)
        assertTrue(acl.any { it.name == "Netflix" && it.emoji == "🎞️" })
        assertTrue(bm.any { it.name == "Netflix" })
        assertTrue(bm.all { it.url.startsWith("https://") && it.id.startsWith("blackmatrix7:") })
        assertEquals(1, service.searchCatalog("", "all", 1).size)
    }

    @Test fun parsesSurgeLocalSetsAndClashProviderDeclarations() {
        val surge = RuleSchemeParser.parse("""
            [General]
            dns-server = 1.1.1.1
            ipv6 = false
            [Proxy Group]
            Ads = select, REJECT, DIRECT
            [Rule]
            RULE-SET,ads-list,Ads
            FINAL,DIRECT
            [RuleSet ads-list]
            DOMAIN-SUFFIX,example.net
        """.trimIndent())
        assertEquals(false, surge.networkSettings?.ipv6)
        assertEquals(listOf("1.1.1.1"), surge.networkSettings?.dnsServers)
        assertEquals(RuleSchemeResource.Inline("DOMAIN-SUFFIX,example.net"), surge.rulesets.first().resource)
        val yaml = RuleSchemeParser.parse("""
            proxy-groups:
              - {name: HK, type: url-test, filter: 'HK|香港', interval: 210}
            rule-providers:
              sample:
                type: inline
                payload:
                  - DOMAIN-SUFFIX,example.org
            rules:
              - RULE-SET,sample,HK
              - MATCH,DIRECT
        """.trimIndent())
        assertEquals(listOf(RuleSchemeGroupMember.NodePattern("HK|香港")), yaml.groups.single().members)
        assertEquals(210, yaml.groups.single().interval)
        assertEquals(RuleSchemeResource.Inline("DOMAIN-SUFFIX,example.org"), yaml.rulesets.first().resource)
        assertThrows(IllegalArgumentException::class.java) {
            RuleSchemeParser.parse("[Rule]\nRULE-SET,http://example.net/rules,REJECT\nFINAL,DIRECT")
        }
    }

    @Test fun httpsRefreshPreservesCustomizationAndFlowsAndStripsProxyCredentials() = runBlocking {
        fun profile(domain: String) = """
            [Proxy]
            Hidden = ss, proxy.example.net, 443, encrypt-method=aes-128-gcm, password=secret-from-source
            [Proxy Group]
            Nodes = select, include-all-proxies=true
            [Rule]
            DOMAIN,$domain,Nodes
            FINAL,Nodes
        """.trimIndent()
        val content = java.util.concurrent.atomic.AtomicReference(profile("initial.example.net"))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertTrue(request.url.isHttps)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(content.get().toResponseBody("text/plain".toMediaType())).build()
        }.build()
        val repo = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder(), fetcher = SubscriptionFetcher(client))
        try {
            val original = repo.importRuleScheme("HTTPS", "https://rules.example.net/profile.conf", null)
            repo.updateRuleGroup(original.id, "Nodes", "My Nodes", null, null, null)
            val flow = repo.addCustomRuleFlow(original.id, "Supplement", "Nodes", "DOMAIN,extra.example.net", null, null, null, true)
            content.set(profile("revised.example.net"))
            val refreshed = repo.refreshRuleScheme(original.id)
            assertEquals(original.id, refreshed.id)
            assertTrue(refreshed.rulesets.any { it.resource == RuleSchemeResource.Inline("DOMAIN,revised.example.net") })
            assertEquals("My Nodes", repo.state.value.snapshot.ruleSchemeCustomizations[original.id]?.renamedGroups?.get("Nodes"))
            assertEquals(flow, repo.state.value.snapshot.customRuleFlows.single())
            assertFalse(repo.exportBackupJson().contains("secret-from-source"))
        } finally {
            repo.shutdown()
        }
    }

    @Test fun dragChangesOnlyMatchingGroupsItCrossesAndReportsUndo() = runBlocking {
        val repository = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder())
        try {
            val scheme = repository.importRuleScheme("Test", null, """
                [Proxy Group]
                Nodes = select, include-all-proxies=true
                Narrow = select, DIRECT
                Broad = select, REJECT
                Other = select, DIRECT
                [Rule]
                DOMAIN,a.example.net,Narrow
                DOMAIN,interleaved.example.org,Other
                DOMAIN-SUFFIX,example.net,Broad
                DOMAIN,b.example.net,Narrow
                GEOIP,CN,Broad,no-resolve
                FINAL,DIRECT
            """.trimIndent())
            val names = listOf("Nodes", "Broad", "Other", "Narrow")
            val result = repository.reorderRuleGroups(scheme.id, 1, 3, names)
            assertEquals(names, result.customization.groupOrder)
            assertEquals(listOf("Other", "Broad", "Narrow"), result.customization.matchingOrder)
            assertEquals(setOf("a.example.net"),
                result.conflicts.flatMap { it.sampleDomains }.filter { it.endsWith(".example.net") }.toSet())
            assertTrue(result.conflicts.all { it.direction == ConflictDirection.NOW_SHADOWED_BY_OTHER && it.movedGroup == "Narrow" })
            val edited = service.effective(repository.state.value.snapshot, scheme)
            assertEquals(listOf("Other", "Broad", "Narrow", "Narrow", "Broad", "DIRECT"), edited.rulesets.map { it.groupName })
            assertTrue(edited.rulesets[edited.rulesets.lastIndex - 1].isGeo())
            assertTrue(edited.rulesets.last().isFinal())
            repository.updateRuleSchemeCustomization(scheme.id, result.previousCustomization!!)
            assertEquals(scheme.rulesets, service.effective(repository.state.value.snapshot, scheme).rulesets)
            val movedOnlyVisually = repository.reorderRuleGroups(scheme.id, 0, 1, listOf("Narrow", "Nodes", "Broad", "Other"))
            assertTrue(movedOnlyVisually.customization.matchingOrder.isEmpty())
            assertTrue(movedOnlyVisually.conflicts.isEmpty())
        } finally {
            repository.shutdown()
        }
    }

    @Test fun adjacentDownDragRetainsItsRuleBlockAcrossRestartAndBackup() = runBlocking {
        val path = temp.newFolder()
        val text = """
            [Proxy Group]
            A = select, DIRECT
            B = select, REJECT
            [Rule]
            DOMAIN-SUFFIX,example.net,A
            DOMAIN,important.example.net,DIRECT
            DOMAIN-SUFFIX,example.net,B
            GEOIP,CN,DIRECT
            FINAL,DIRECT
        """.trimIndent()
        val original = TowerRepositoryImpl(TestResources, TestSecrets(), path)
        val backup = try {
            val source = original.importRuleScheme("Adjacent", null, text)
            val moved = original.reorderRuleGroups(source.id, 0, 1, listOf("B", "A"))
            assertEquals(listOf("B", "A"), moved.customization.matchingOrder)
            assertEquals(setOf("example.net", "www.example.net", "important.example.net"), moved.conflicts.flatMap { it.sampleDomains }.toSet())
            original.exportBackupJson()
        } finally {
            original.shutdown()
        }
        for (repo in listOf(TowerRepositoryImpl(TestResources, TestSecrets(), path),
            TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder()).also { it.importBackupJson(backup) })) {
            try {
                val generated = repo.generateConfiguration(ClientTarget.SURGE, ExportContentMode.FULL_CONFIGURATION)
                val rules = iniSections(generated.content).getValue("rule")
                assertTrue(rules[0], rules[0].startsWith("DOMAIN,important.example.net,DIRECT"))
                assertTrue(rules[1], rules[1].startsWith("DOMAIN-SUFFIX,example.net,B"))
                assertTrue(rules[2], rules[2].startsWith("DOMAIN-SUFFIX,example.net,A"))
                assertTrue(rules[3].startsWith("GEOIP,CN,"))
                assertTrue(rules[4].startsWith("FINAL,"))
            } finally {
                repo.shutdown()
            }
        }
    }

    @Test fun supplementsArePinnedAndCustomRulesInsertAtFirstOverlap() = runBlocking {
        val repository = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder())
        try {
            val scheme = repository.importRuleScheme("Test", null, """
                [Proxy Group]
                A = select, DIRECT
                B = select, REJECT
                [Rule]
                DOMAIN,unrelated.example.org,A
                DOMAIN-SUFFIX,example.net,A
                GEOIP,CN,A
                FINAL,DIRECT
            """.trimIndent())
            val local = repository.createLocalRuleSet("Local", "DOMAIN,b.example.net", null)
            repository.addCustomRuleFlow(scheme.id, "Local", "B", "", null, local.id, null, false)
            repository.updateRuleGroup(scheme.id, "A", "Alpha", null, null,
                listOf("DOMAIN-SUFFIX,test.net", "DOMAIN,sub.test.net"))
            repository.createRuleGroup(scheme.id, "New filter", RuleSchemeGroupKind.URL_TEST,
                listOf(RuleSchemeGroupMember.NodePattern("Tokyo")), true)
            val effective = service.effective(repository.state.value.snapshot, scheme)
            val output = effective.rulesets.map { (it.resource as RuleSchemeResource.Inline).rule to it.groupName }
            assertEquals(listOf("DOMAIN,sub.test.net" to "Alpha", "DOMAIN-SUFFIX,test.net" to "Alpha",
                "DOMAIN,unrelated.example.org" to "Alpha", "DOMAIN,b.example.net" to "B", "DOMAIN-SUFFIX,example.net" to "Alpha",
                "GEOIP,CN" to "Alpha", "FINAL" to "DIRECT"), output)
            assertTrue(repository.state.value.activeSchemeGroups.any { it.sourceName == "New filter" && it.isNodeFilterGroup })
        } finally {
            repository.shutdown()
        }
    }
}
