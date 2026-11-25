package com.jzb.tower.android.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerSelectionCheckmark
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ClientTargetMeta
import com.jzb.tower.core.model.ProxyKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolFilterSheet(
    meta: ClientTargetMeta,
    excludedKinds: Set<ProxyKind>,
    onToggleKind: (ProxyKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val supportedKinds = ClientCatalog.ALL_PROXY_KINDS.filter { meta.supportedKinds.contains(it) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = palette.groupedBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "协议筛选",
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "仅作用于 ${meta.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                TowerFloatingPillButton(
                    text = "完成",
                    onClick = onDismiss,
                )
            }

            TowerCard(modifier = Modifier.fillMaxWidth()) {
                supportedKinds.forEachIndexed { idx, kind ->
                    val included = !excludedKinds.contains(kind)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = FormatUtils.proxyKindLabel(kind),
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = kind.rawValue.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textTertiary,
                            )
                        }
                        TowerSelectionCheckmark(
                            selected = included,
                            onToggle = { onToggleKind(kind) },
                            contentDescription = FormatUtils.proxyKindLabel(kind),
                        )
                    }
                    if (idx < supportedKinds.lastIndex) {
                        HorizontalDivider(
                            color = palette.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
