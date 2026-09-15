package app.quieta.feature.privilege

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quieta.R
import app.quieta.core.settings.PreferredAuthorizer
import app.quieta.ui.component.cardPressScale

/**
 * 可用特权 — InstallerX PrivPage: radio list of authorizers + tip cards.
 */
@Composable
fun PrivilegeScreen(
    selected: PreferredAuthorizer,
    rootAvailable: Boolean,
    rootLabel: String,
    shizukuAvailable: Boolean,
    shizukuAuthorized: Boolean,
    dhizukuAvailable: Boolean,
    onBack: () -> Unit,
    onSelect: (PreferredAuthorizer) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        Text(
            text = stringResource(R.string.home_stat_authorizers),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        TipCard(
            text = "本板块会检测设备上所有可供本应用使用的授权方式，并允许你选择一种作为全局授权，用于执行需要提权的操作。这里仅检测其可用性；所需权限会在应用实际使用时再申请。",
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "授权方式",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AuthorizerRow(
                    title = "无特权",
                    description = "不使用任何特殊权限，仅浏览本地缓存",
                    selected = selected == PreferredAuthorizer.NONE,
                    onClick = { onSelect(PreferredAuthorizer.NONE) },
                )
                AuthorizerRow(
                    title = "ROOT",
                    description = if (rootAvailable) "可用 ($rootLabel)" else "不可用",
                    selected = selected == PreferredAuthorizer.ROOT,
                    onClick = { onSelect(PreferredAuthorizer.ROOT) },
                )
                AuthorizerRow(
                    title = "Shizuku",
                    description = when {
                        shizukuAuthorized -> "已激活"
                        shizukuAvailable -> "请激活 Shizuku"
                        else -> "不可用"
                    },
                    selected = selected == PreferredAuthorizer.SHIZUKU,
                    onClick = { onSelect(PreferredAuthorizer.SHIZUKU) },
                )
                AuthorizerRow(
                    title = "Dhizuku",
                    description = if (dhizukuAvailable) "已激活" else "请激活 Dhizuku",
                    selected = selected == PreferredAuthorizer.DHIZUKU,
                    onClick = { onSelect(PreferredAuthorizer.DHIZUKU) },
                )
                AuthorizerRow(
                    title = "自动选择",
                    description = "优先 Shizuku → Dhizuku → ROOT",
                    selected = selected == PreferredAuthorizer.AUTO,
                    onClick = { onSelect(PreferredAuthorizer.AUTO) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TipCard(
            text = "这里的设置用于决定本应用自身执行提权操作时使用的授权方式。选择后主页状态卡会立即刷新。",
        )
    }
}

@Composable
private fun AuthorizerRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardPressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TipCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF1A56A8),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1A56A8),
            )
        }
    }
}
