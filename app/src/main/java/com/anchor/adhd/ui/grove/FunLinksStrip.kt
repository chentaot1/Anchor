package com.anchor.adhd.ui.grove

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.data.model.FunLinkEntity
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun FunLinksStrip(
    vm: AnchorViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val links by vm.activeFunLinks.collectAsState()
    val credits by vm.funCredits.collectAsState()
    val isUnlocked = credits > 0
    val context = LocalContext.current

    if (links.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(links, key = { it.id }) { link ->
            FunLinkChip(
                link = link,
                isUnlocked = isUnlocked,
                onClick = {
                    vm.launchFunLink(link) {
                        launchFunLinkInCustomTab(context, link.url)
                    }
                },
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

@Composable
private fun FunLinkChip(
    link: FunLinkEntity,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val shape = RoundedCornerShape(14.dp)
    val containerColor = if (isUnlocked) tokens.surface else tokens.paper2.copy(alpha = 0.7f)
    val borderColor = if (isUnlocked) tokens.rule else tokens.rule.copy(alpha = 0.6f)
    val labelColor = if (isUnlocked) tokens.ink else tokens.muted

    Surface(
        modifier = modifier.height(80.dp),
        onClick = onClick,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link.emoji,
                    fontSize = 18.sp,
                    modifier = Modifier.alpha(if (isUnlocked) 1f else 0.4f),
                )
                if (!isUnlocked) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = tokens.muted,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = link.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = labelColor,
            )
        }
    }
}

private fun launchFunLinkInCustomTab(context: Context, url: String) {
    val toolbarArgb = 0xFF0E1624.toInt()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(toolbarArgb)
                .setNavigationBarColor(toolbarArgb)
                .build(),
        )
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build()

    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
        }
    }
}