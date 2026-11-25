package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.db.DesktopFunLink
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import java.awt.Desktop
import java.net.URI

/**
 * Trojan Fun Links strip on Windows 11.
 * Quiet token-backed tiles; launches default desktop browser.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DesktopFunLinks(
    links: List<DesktopFunLink>,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    val tokens = LocalAnchorTokens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Brain breaks",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.beacon,
            )
            Text(
                text = "Healthy dopamine reset",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.muted,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (link in links) {
                Surface(
                    modifier =
                        Modifier
                            .widthIn(min = 150.dp, max = 220.dp)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .clickable(role = Role.Button, onClickLabel = "Open ${link.name}") {
                                runCatching {
                                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                        Desktop.getDesktop().browse(URI(link.url))
                                    }
                                }
                            },
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = tokens.paper2,
                    border = BorderStroke(1.dp, tokens.rule),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = tokens.paper3,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = link.emoji,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = link.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}