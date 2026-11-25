package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TowerResourceProvider
import com.jzb.tower.core.model.ClientTarget
import kotlinx.serialization.json.*

class PolicyGroupIcons(resources: TowerResourceProvider) {
    private val circles by lazy {
        resources.readText("PolicyGroupIcons/PolicyGroupIcons_manifest.json")
            ?.let { Json.parseToJsonElement(it).jsonObject["circles"]?.jsonObject }.orEmpty()
    }

    fun url(emoji: String?, systemFallback: Boolean = false): String? {
        val key = emoji?.replace("\uFE0F", "")?.replace("\uFE0E", "")?.let { if (it == "🇺🇲") "🇺🇸" else it } ?: return null
        val base = circles["base"]?.jsonPrimitive?.contentOrNull
        val files = circles["files"]?.jsonObject.orEmpty()
        if (base != null && circles["artifactCommit"] != null && key in files) {
            return base + key.codePoints().toArray().joinToString("-") { it.toString(16) } + ".png"
        }
        return if (systemFallback) symbols[key]?.plus(".system") else null
    }

    companion object {
        val drawsIcons = setOf(ClientTarget.SURGE, ClientTarget.SURGE_MAC, ClientTarget.CLASH,
            ClientTarget.CLASH_APPLE, ClientTarget.CLASH_VERGE, ClientTarget.QUANX, ClientTarget.LOON, ClientTarget.EGERN)
        val writesIcons = drawsIcons + setOf(ClientTarget.CLASHMAC, ClientTarget.FLCLASH, ClientTarget.MIHOMO_PARTY, ClientTarget.CLASH_MI)
        private val symbols = mapOf(
            "🎛" to "hand.point.up.left.fill", "♻" to "speedometer", "🍃" to "leaf.fill",
            "🛑" to "hand.raised.fill", "🐟" to "fish.fill", "🌏" to "globe.asia.australia.fill",
            "🌍" to "globe.europe.africa.fill", "🏠" to "house.fill", "🌐" to "network",
            "📢" to "bell.badge.fill", "📹" to "play.rectangle.fill", "🎥" to "film.fill",
            "🎞" to "film.fill", "🧸" to "sparkles", "🎬" to "film.stack.fill",
            "🎦" to "film.stack.fill", "📺" to "tv.fill", "🎵" to "music.note",
            "🎧" to "headphones", "🟢" to "headphones", "🎶" to "music.note.list",
            "📲" to "paperplane.fill", "✈" to "paperplane.fill", "💬" to "bubble.left.and.bubble.right.fill",
            "🤖" to "brain.head.profile", "🔷" to "sparkle", "✨" to "sparkle",
            "🐦" to "bird.fill", "🕊" to "bird.fill", "♾" to "infinity",
            "🎙" to "bubble.left.and.bubble.right.fill", "🐙" to "chevron.left.forwardslash.chevron.right", "☁" to "cloud.fill",
            "💳" to "creditcard.fill", "🪙" to "bitcoinsign.circle.fill", "📦" to "cart.fill",
            "Ⓜ" to "square.grid.2x2.fill", "🪟" to "square.grid.2x2.fill", "🍎" to "apple.logo",
            "🔍" to "magnifyingglass", "🔎" to "magnifyingglass", "🎮" to "gamecontroller.fill",
            "🎯" to "arrow.right.circle.fill", "🚀" to "airplane", "⚡" to "bolt.fill",
            "🔁" to "arrow.triangle.2.circlepath", "⚖" to "scalemass.fill", "🧩" to "square.stack.3d.up.fill",
            "🎓" to "graduationcap.fill", "📰" to "newspaper.fill", "📧" to "envelope.fill",
            "🏦" to "building.columns.fill", "⚽" to "soccerball", "👥" to "person.2.fill",
            "📥" to "arrow.down.circle.fill", "🛡" to "lock.shield.fill", "📶" to "speedometer",
            "🍿" to "play.tv.fill", "👽" to "person.3.fill", "📕" to "book.closed.fill",
            "📞" to "phone.fill", "🐳" to "shippingbox.fill", "🎨" to "paintpalette.fill",
            "🛒" to "cart.fill", "🏡" to "house.fill", "💰" to "dollarsign.circle.fill",
            "⭐" to "star.fill", "🛠" to "wrench.and.screwdriver.fill", "🗺" to "map.fill",
            "🔀" to "arrow.triangle.branch", "📣" to "megaphone.fill", "📝" to "note.text",
            "📱" to "iphone", "🔞" to "eye.slash.fill",
        )
    }
}
