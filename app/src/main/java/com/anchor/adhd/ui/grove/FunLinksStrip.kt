package com.anchor.adhd.ui.grove

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.data.model.FunLinkEntity
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun FunLinksStrip(
    vm: AnchorViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null
) {
    val links by vm.activeFunLinks.collectAsState()
    val credits by vm.funCredits.collectAsState()
    val isUnlocked = credits > 0
    val context = LocalContext.current

    if (links.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.width(112.dp)
            )
        }
    }
}

@Composable
private fun FunLinkChip(
    link: FunLinkEntity,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = 14.dp
    val auraColor = when {
        link.name.contains("wordle", ignoreCase = true) || link.url.contains("wordle", ignoreCase = true) -> Color(0xFF10B981)
        link.name.contains("connect", ignoreCase = true) || link.url.contains("connections", ignoreCase = true) -> Color(0xFF8B5CF6)
        link.name.contains("chess", ignoreCase = true) || link.url.contains("chess", ignoreCase = true) -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8)
    }

    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (isUnlocked) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF16233B),
                            Color(0xFF0E1626)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0x990D1420),
                            Color(0x66080D15)
                        )
                    )
                }
            )
            .border(
                1.dp,
                if (isUnlocked) {
                    Brush.verticalGradient(
                        listOf(
                            AnchorColors.HarborAction.copy(alpha = 0.45f),
                            Color(0xFF223552)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0x33FFFFFF),
                            Color(0x10FFFFFF)
                        )
                    )
                },
                RoundedCornerShape(cornerRadius)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Colored icon halo aura
                if (isUnlocked) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(auraColor.copy(alpha = 0.35f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                }
                Text(
                    text = link.emoji,
                    fontSize = 18.sp,
                    modifier = Modifier.alpha(if (isUnlocked) 1f else 0.4f)
                )
                if (!isUnlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomEnd)
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
                color = if (isUnlocked) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun launchFunLinkInCustomTab(context: Context, url: String) {
    val darkToolbar = 0xFF0A0E18.toInt()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(darkToolbar)
                .setNavigationBarColor(darkToolbar)
                .build()
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
        } catch (ignored: Exception) {
        }
    }
}
