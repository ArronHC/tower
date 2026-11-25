package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import java.util.Locale

object PolicyGroupEmoji {
    private data class Words(val emoji: String, val tokens: List<String>, val phrases: List<String>, val exact: List<String> = emptyList())
    private val table = listOf(
        Words("🍃", emptyList(), listOf("应用净化")),
        Words("🛑", listOf("reject", "adblock", "ad block", "ads", "advertising", "advertisement", "blocklist"), listOf("广告拦截", "全球拦截", "拦截", "广告", "去广告"), listOf("block")),
        Words("🐟", listOf("final", "match", "fallback rule"), listOf("漏网之鱼", "兜底", "其他流量", "其它流量", "剩余流量"), listOf("others")),
        Words("🛡️", listOf("privacy", "tracking", "tracker", "antitracking"), listOf("隐私", "追踪", "跟踪", "泄漏", "泄露")),
        Words("🌏", listOf("china media", "asia media"), listOf("国内媒体", "国内视频", "国内影视", "港台媒体", "日韩媒体", "港澳台媒体", "亚洲媒体")),
        Words("🌍", listOf("global media", "streaming", "foreign media", "international media"), listOf("国外媒体", "国际媒体", "海外媒体", "流媒体")),
        Words("🏠", listOf("lan", "local area network", "private network", "intranet"), listOf("局域网", "内网", "私有网络")),
        Words("🌐", listOf("dns", "httpdns", "doh"), listOf("域名解析")),
        Words("📶", listOf("speedtest", "speed test", "network test"), listOf("测速工具", "测速网站", "网络测试")),
        Words("🔀", listOf("non standard port"), listOf("非标端口", "非标准端口")),
        Words("📢", listOf("fcm", "google fcm"), listOf("谷歌fcm", "谷歌 fcm", "谷歌推送")),
        Words("📹", listOf("youtube", "youtube music"), listOf("油管")),
        Words("🎥", listOf("netflix"), listOf("奈飞", "网飞")),
        Words("🧸", listOf("disney", "disney plus", "disneyplus"), listOf("迪士尼")),
        Words("🎬", listOf("hbo", "hbo max", "prime video", "amazon prime", "hulu", "apple tv", "appletv", "paramount", "peacock"), listOf("亚马逊视频"), listOf("max")),
        Words("🍿", listOf("emby", "jellyfin", "plex"), emptyList()),
        Words("⚽", listOf("dazn", "espn"), emptyList()),
        Words("📺", listOf("bilibili", "bahamut", "abema", "abematv", "iqiyi", "youku", "tvb", "anime", "twitch", "niconico", "viu", "mytvsuper"), listOf("哔哩", "巴哈姆特", "爱奇艺", "优酷", "动漫", "番剧", "动画", "直播")),
        Words("🎵", listOf("tiktok", "douyin", "bytedance"), listOf("抖音", "字节跳动", "抖快")),
        Words("🎧", listOf("spotify", "apple music", "deezer", "tidal", "soundcloud", "qobuz"), emptyList()),
        Words("🎶", listOf("netease music", "music", "kkbox", "joox"), listOf("网易音乐", "网易云音乐", "音乐")),
        Words("📲", listOf("telegram"), listOf("电报")),
        Words("🤖", listOf("ai", "aigc", "openai", "chatgpt", "claude", "anthropic", "copilot", "perplexity", "grok", "deepseek", "midjourney"), listOf("ai平台", "ai 平台", "ai服务", "人工智能")),
        Words("🔷", listOf("gemini", "bard"), emptyList()),
        Words("🐦", listOf("twitter"), listOf("推特"), listOf("x", "twitter x")),
        Words("♾️", listOf("facebook", "instagram", "meta", "whatsapp", "threads"), listOf("脸书")),
        Words("📣", listOf("weibo"), listOf("微博")),
        Words("🎙️", listOf("discord"), emptyList()),
        Words("👽", listOf("reddit"), emptyList()),
        Words("📕", listOf("xiaohongshu", "rednote"), listOf("小红书")),
        Words("💬", listOf("wechat", "line", "signal", "kakaotalk", "messenger", "im"), listOf("微信", "即时通讯", "通讯", "聊天")),
        Words("📞", listOf("talkatone", "skype", "zoom", "voip"), listOf("通话", "电话")),
        Words("🐙", listOf("github", "gitlab"), emptyList()),
        Words("🐳", listOf("docker"), emptyList()),
        Words("📝", listOf("notion", "evernote", "obsidian"), emptyList()),
        Words("📱", listOf("xiaomi", "samsung"), listOf("小米", "三星")),
        Words("🔞", listOf("pornhub", "ehentai", "nsfw"), emptyList()),
        Words("🎨", listOf("pixiv", "adobe", "figma", "canva"), emptyList()),
        Words("💳", listOf("paypal", "stripe", "visa", "wise"), listOf("支付")),
        Words("🪙", listOf("binance", "okx", "crypto", "coinbase", "bybit"), listOf("加密货币", "货币", "币安")),
        Words("🛒", listOf("amazon", "ebay", "shopee", "shopping", "shop"), listOf("购物", "电商", "亚马逊", "淘宝", "京东", "阿里巴巴")),
        Words("☁️", listOf("google cloud", "azure"), emptyList()),
        Words("Ⓜ️", listOf("microsoft", "bing", "onedrive", "office", "outlook", "office365", "windows", "teams"), listOf("微软")),
        Words("🍎", listOf("apple", "icloud", "app store", "appstore", "testflight", "itunes"), listOf("苹果")),
        Words("🔍", listOf("google", "gmail"), listOf("谷歌")),
        Words("☁️", listOf("cloudflare", "aws", "azure", "google cloud", "oracle cloud", "pikpak", "dropbox", "mega"), listOf("网盘", "云盘")),
        Words("🎮", listOf("game", "games", "gaming", "steam", "epic", "playstation", "xbox", "nintendo", "blizzard", "videogame"), listOf("游戏")),
        Words("🎓", listOf("scholar", "academic", "education"), listOf("学术", "教育", "论文")),
        Words("📰", listOf("news"), listOf("新闻", "资讯")),
        Words("📧", listOf("mail", "email"), listOf("邮件", "邮箱")),
        Words("🏦", listOf("bank", "banking", "finance"), listOf("银行", "金融")),
        Words("⚽", listOf("sport", "sports"), listOf("体育")),
        Words("👥", listOf("social"), listOf("社交")),
        Words("📥", listOf("download", "downloads", "p2p", "bt", "torrent"), listOf("下载", "pt站")),
        Words("🎬", listOf("video", "videos", "movie", "movies", "film", "films"), listOf("影视", "视频", "电影", "剧集", "追剧")),
        Words("🎯", listOf("direct", "global direct", "domestic"), listOf("全球直连", "直连", "直接连接", "国内", "大陆"), listOf("mainland")),
    )

    private val roles = listOf(
        Words("🔁", listOf("fallback", "failover", "backup"), listOf("故障转移", "故障切换", "故转", "备用", "容灾")),
        Words("⚖️", listOf("load balance", "loadbalance", "balance", "round robin"), listOf("负载均衡", "均衡", "轮询")),
        Words("♻️", listOf("auto", "auto select", "url test", "automatic", "best", "lowest latency"), listOf("自动", "优选", "延迟最低", "最低延迟", "低延迟")),
        Words("📶", emptyList(), listOf("测速")),
        Words("🎛️", listOf("manual", "manual select", "manual switch"), listOf("手动", "手选", "自选", "策略选择", "选择地区"), listOf("select", "selector")),
        Words("🏡", listOf("residential", "home broadband"), listOf("家宽", "家庭宽带", "原生")),
        Words("💰", listOf("cheap", "budget"), listOf("便宜", "低价", "低倍率", "省流")),
        Words("⭐", listOf("premium", "vip", "iplc", "iepl"), listOf("高级", "优质", "精品", "高速", "专线")),
        Words("🛠️", listOf("self hosted", "selfhosted"), listOf("自建")),
        Words("🌍", listOf("foreign", "overseas", "international", "outside"), listOf("国外", "国际", "海外", "境外")),
        Words("🗺️", listOf("other", "others", "other regions"), listOf("其他", "其它")),
        Words("🌐", listOf("all", "proxies"), listOf("全部", "所有")),
        Words("🚀", listOf("proxy", "proxy select", "node select", "node selection", "nodes", "node", "default"), listOf("节点选择", "代理", "节点", "加速", "默认")),
        Words("🌐", listOf("global"), listOf("全球")),
    )

    private val ruleLists = listOf(
        Words("🎯", listOf("china domain", "china ip", "china company", "china media", "local area network"), emptyList(), listOf("china", "cn", "chinadomain", "chinaip", "chinamax", "direct", "gfw direct")),
        Words("🚀", listOf("gfw", "gfwlist", "proxy gfwlist", "proxy lite", "proxy media", "global"), emptyList(), listOf("proxy", "proxylite")),
        Words("🛑", listOf("ban ad", "ban program ad", "ban easylist", "reject"), emptyList()),
    )

    private val emojiTable = listOf(
        Words("🤖", listOf("openai", "chatgpt"), emptyList()),
        Words("🔷", listOf("gemini", "bard"), emptyList()),
        Words("📹", listOf("youtube"), listOf("油管")),
        Words("🎞️", listOf("netflix"), listOf("奈飞")),
        Words("🧸", listOf("disney"), emptyList()),
        Words("🎥", listOf("primevideo", "amazonprime", "amazon video"), emptyList()),
        Words("🎦", listOf("hbo"), emptyList()),
        Words("✈️", listOf("telegram"), emptyList()),
        Words("🕊️", listOf("twitter"), emptyList()),
        Words("♾️", listOf("facebook", "instagram", "meta"), emptyList()),
        Words("🎵", listOf("tiktok"), emptyList()),
        Words("🟢", listOf("spotify"), emptyList()),
        Words("🍎", listOf("apple", "icloud", "testflight"), emptyList()),
        Words("🪟", listOf("microsoft", "onedrive", "office365"), emptyList()),
        Words("🔎", listOf("google", "firebase"), emptyList()),
        Words("🎮", listOf("steam", "epic", "playstation", "xbox", "game", "games", "gaming"), emptyList()),
        Words("🛑", listOf("advert", "advertising", "advertisement", "adblock", "reject"), listOf("广告")),
        Words("🏠", listOf("lan", "private", "local"), listOf("局域网")),
    )

    private val leading = Regex("^(?:[\\x{1F1E6}-\\x{1F1FF}]{2}|[0-9#*]\\uFE0F?\\u20E3|[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2194}-\\x{21AA}Ⓜ©®‼⁉™ℹ⭐⭕〰〽][\\uFE0E\\uFE0F\\x{1F3FB}-\\x{1F3FF}]*(?:\\u200D[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}][\\uFE0E\\uFE0F\\x{1F3FB}-\\x{1F3FF}]*)*)")
    private val flag = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")
    fun leading(name: String): String? = leading.find(name.trim())?.value
    fun withoutLeading(name: String): String = name.trim().drop(leading(name)?.length ?: 0).trim()
    fun isFlag(value: String) = flag.matches(value)
    private fun tokens(name: String): List<String> {
        val spaced = name.replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2").replace(Regex("([A-Za-z])(\\d)"), "$1 $2")
        return Regex("[a-z0-9]+").findAll(spaced.lowercase(Locale.ROOT)).map { it.value }.toList()
    }

    private fun match(name: String, entries: List<Words>): String? {
        val text = name.lowercase(Locale.ROOT)
        val words = tokens(name)
        val combined = words.joinToString(" ")
        val variants = words.toMutableSet()
        for (index in words.indices) for (width in 2..3) {
            if (index + width <= words.size) variants.add(words.subList(index, index + width).joinToString(""))
        }
        return entries.firstOrNull { entry -> entry.phrases.any(text::contains) ||
            (combined in entry.exact && text.none { it.code > 127 && it.isLetter() }) ||
            entry.tokens.any { it.replace(" ", "") in variants || " $combined ".contains(" $it ") } }?.emoji
    }

    private fun recognized(name: String, regions: Boolean): String? {
        match(name, table)?.let { return it }
        if (regions) {
            val code = when {
                name.contains("加坡") -> "SG"
                Regex("欧洲|欧盟|\\b(?:Europe|EU)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "EU"
                else -> CountryTable.codeFromName(name)
            }
            if (code != null) return CountryTable.flag(code)
        }
        return match(name, roles)
    }

    fun ruleList(name: String): String? = match(name, ruleLists) ?: recognized(name, false)
    fun catalog(name: String, fallback: String): String = leading(name) ?: match(name, emojiTable) ?: fallback

    fun resolve(group: RuleSchemeGroup, rules: List<RuleSchemeRuleset> = emptyList(), fallback: Boolean = true): String? {
        leading(group.name)?.let { return it }
        group.iconEmoji?.let { return it }
        recognized(group.name, true)?.let { return it }
        val ruleEmojis = rules.asSequence().filter { it.groupName == group.name }.mapNotNull { entry ->
            when (val resource = entry.resource) {
                is RuleSchemeResource.Inline -> when (resource.rule.uppercase(Locale.ROOT)) {
                    "FINAL", "MATCH" -> "🐟"
                    "GEOIP,CN" -> "🎯"
                    else -> null
                }
                is RuleSchemeResource.Bundled -> ruleList(resource.file.removePrefix("ACL4SSR_").substringBeforeLast('.'))
                is RuleSchemeResource.Remote -> ruleList(resource.url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.'))
            }
        }.groupingBy { it }.eachCount()
        if (ruleEmojis.size > 1 && ruleEmojis.keys.all { it in setOf("🎥", "📹", "🧸", "🎬", "📺", "🍿", "🎵") }) return "🎬"
        ruleEmojis.maxByOrNull { it.value }?.key?.let { return it }
        val references = group.members.filterIsInstance<RuleSchemeGroupMember.Reference>()
        val patterns = group.members.filterIsInstance<RuleSchemeGroupMember.NodePattern>()
        if (patterns.isEmpty() && references.isNotEmpty()) {
            if (references.all { it.name.equals("DIRECT", true) }) return "🎯"
            if (references.all { it.name.startsWith("REJECT", true) }) return "🛑"
        }
        if (references.isEmpty() && patterns.isNotEmpty()) {
            val codes = patterns.map { CountryTable.codeFromName(it.pattern) }.distinct()
            codes.singleOrNull()?.let { return CountryTable.flag(it) }
        }
        return if (fallback) when (group.kind) {
            RuleSchemeGroupKind.SELECT -> "🌐"
            RuleSchemeGroupKind.URL_TEST -> "⚡️"
            RuleSchemeGroupKind.FALLBACK -> "🔁"
            RuleSchemeGroupKind.LOAD_BALANCE -> "⚖️"
            else -> null
        } else null
    }
}
