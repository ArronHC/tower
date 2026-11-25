package com.jzb.tower.android.di

import android.content.Context
import android.content.Intent
import com.jzb.tower.android.platform.AndroidResourceProvider
import com.jzb.tower.android.platform.AndroidTailnetSecretStore
import com.jzb.tower.android.platform.LanSharingForegroundService
import com.jzb.tower.core.contract.LanServiceHooks
import com.jzb.tower.core.contract.TowerRepository
import com.jzb.tower.core.engine.TowerRepositoryImpl
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections

class TowerAppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val repository: TowerRepository = TowerRepositoryImpl(
        AndroidResourceProvider(appContext.assets),
        AndroidTailnetSecretStore(appContext),
        appContext.filesDir,
        object : LanServiceHooks {
            override fun onLanSharingStarted(primaryUrl: String, port: Int) {
                val request = Intent(appContext, LanSharingForegroundService::class.java)
                    .setAction(LanSharingForegroundService.ACTION_START)
                    .putExtra(LanSharingForegroundService.EXTRA_URL, primaryUrl)
                    .putExtra(LanSharingForegroundService.EXTRA_PORT, port)
                appContext.startForegroundService(request)
            }

            override fun onLanSharingStopped() {
                appContext.stopService(Intent(appContext, LanSharingForegroundService::class.java))
            }

            override fun getLocalLanAddresses(): List<String> {
                val interfaces = try {
                    NetworkInterface.getNetworkInterfaces()?.let(Collections::list).orEmpty()
                } catch (_: SocketException) {
                    emptyList()
                }
                return interfaces.filter { network ->
                    val name = network.name.lowercase()
                    (name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("eth") ||
                        name.startsWith("en") || name.startsWith("ap") || name.startsWith("swlan") ||
                        name.startsWith("usb") || name.startsWith("rndis")) &&
                        runCatching { network.isUp && !network.isLoopback && !network.isPointToPoint }.getOrDefault(false)
                }.sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("wifi")) 0 else 1 }
                    .flatMap { Collections.list(it.inetAddresses) }
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isAnyLocalAddress || it.isMulticastAddress }
                    .mapNotNull { it.hostAddress }.distinct()
            }
        },
    )

    fun shutdown() {
        repository.shutdown()
    }
}
