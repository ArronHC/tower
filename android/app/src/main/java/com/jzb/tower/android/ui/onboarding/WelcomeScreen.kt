package com.jzb.tower.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jzb.tower.android.ui.components.ClientIcon
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.TowerAppIcon
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerPageBackground
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog

private data class PrivacyPillar(
    val icon: ImageVector,
    val title: String,
    val detail: String,
)

private val Pillars = listOf(
    PrivacyPillar(
        icon = Icons.Rounded.Lock,
        title = "本机解析与转换",
        detail = "订阅解析、分流匹配和配置生成全部在设备本地完成，不经过任何第三方转换服务器。",
    ),
    PrivacyPillar(
        icon = Icons.Rounded.Public,
        title = "内置离线 IP 国家库",
        detail = "节点地区与 ASN 信息优先按名称识别，缺省时查询随 App 打包的离线二进制数据库，不联网泄露查询。",
    ),
    PrivacyPillar(
        icon = Icons.Rounded.VisibilityOff,
        title = "零云端上传",
        detail = "机场地址、节点密码和 Tailscale Auth Key 只保存在本机，敏感密钥使用系统硬件加密保管。",
    ),
    PrivacyPillar(
        icon = Icons.Rounded.Bolt,
        title = "一键导入主流安卓客户端",
        detail = "支持 FlClash、Clash Meta、sing-box、Hiddify、Surfboard、v2rayNG 等客户端即点即导。",
    ),
)

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val androidClients = ClientCatalog.DEFAULT_CLIENT_ORDER
        .map { ClientCatalog.meta(it) }
        .filter { it.isAndroidNative }
        .take(7)

    TowerPageBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TowerAppIcon(size = 68.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "塔台 · Tower",
                            style = MaterialTheme.typography.headlineLarge,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "本机优先的订阅管理与多端配置中心",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    }
                }

                PrivacyBadge(text = "本机解析 · 离线 IP 库 · 零云端上传")

                TowerCard(
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Pillars.forEach { pillar ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = pillar.icon,
                                        contentDescription = null,
                                        tint = palette.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = pillar.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                    )
                                    Text(
                                        text = pillar.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                TowerCard(
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "原生支持的主流客户端",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        androidClients.forEach { client ->
                            ClientIcon(
                                iconFile = client.iconFile,
                                name = client.name,
                                brandColorHex = client.brandColorHex,
                                size = 38.dp,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TowerDimens.PagePadding, vertical = 14.dp),
            ) {
                PrimaryActionButton(
                    text = "开始使用",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
