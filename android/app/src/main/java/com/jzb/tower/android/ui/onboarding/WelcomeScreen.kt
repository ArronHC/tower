package com.jzb.tower.android.ui.onboarding

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.ClientIcon
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.TowerAppIcon
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerPageBackground
import com.jzb.tower.android.ui.components.TowerSquircleIconBadge
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog

private data class PrivacyPillar(
    val icon: ImageVector,
    val tint: @Composable () -> Color,
    val title: String,
    val detail: String,
)

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val pillars = listOf(
        PrivacyPillar(
            icon = Icons.Rounded.Lock,
            tint = { palette.primary },
            title = "本机解析与转换",
            detail = "订阅解析、分流匹配和配置生成全部在设备本地完成，不经过任何第三方转换服务器。",
        ),
        PrivacyPillar(
            icon = Icons.Rounded.Public,
            tint = { palette.teal },
            title = "内置离线 IP 国家库",
            detail = "节点地区与 ASN 信息优先按名称识别，缺省时查询随 App 打包的离线二进制数据库，不联网泄露查询。",
        ),
        PrivacyPillar(
            icon = Icons.Rounded.VisibilityOff,
            tint = { palette.purple },
            title = "零云端上传",
            detail = "机场地址、节点密码和 Tailscale Auth Key 只保存在本机，敏感密钥使用系统硬件加密保管。",
        ),
        PrivacyPillar(
            icon = Icons.Rounded.Bolt,
            tint = { palette.orange },
            title = "一键导入主流客户端",
            detail = "支持 FlClash、Clash Meta、sing-box、Hiddify、Surfboard、v2rayNG 等客户端即点即导。",
        ),
    )

    val androidClients = ClientCatalog.DEFAULT_CLIENT_ORDER
        .map { ClientCatalog.meta(it) }
        .filter { it.isAndroidNative }
        .take(6)

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
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(6.dp))

                TowerAppIcon(size = 82.dp)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "塔台 · Tower",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "本机优先的订阅管理与多端配置中心",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                PrivacyBadge(text = "本机解析 · 离线 IP 库 · 零云端上传")

                // Floating client icons preview strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    androidClients.forEach { client ->
                        ClientIcon(
                            iconFile = client.iconFile,
                            name = client.name,
                            brandColorHex = client.brandColorHex,
                            size = 44.dp,
                            withShadow = true,
                        )
                    }
                }

                TowerCard(
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        pillars.forEachIndexed { index, pillar ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                TowerSquircleIconBadge(
                                    icon = pillar.icon,
                                    backgroundColor = pillar.tint(),
                                    size = 36.dp,
                                )
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
                            if (index < pillars.lastIndex) {
                                HorizontalDivider(
                                    color = palette.divider,
                                    modifier = Modifier.padding(start = 50.dp),
                                )
                            }
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
