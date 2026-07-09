package com.linuxhost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ManagerScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoCard()

        Text(
            "Actions",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )

        ActionGrid()
    }
}

@Composable
private fun InfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("Status", "Running", valueColor = LinuxGreen)
            HorizontalDivider(color = Color(0xFF21262D))
            InfoRow("Version", "Ubuntu 26.04 LTS")
            HorizontalDivider(color = Color(0xFF21262D))
            InfoRow("Architecture", "aarch64")
            HorizontalDivider(color = Color(0xFF21262D))
            InfoRow("Uptime", "2h 14m")
            HorizontalDivider(color = Color(0xFF21262D))
            InfoRow("Rootfs Location", "/data/data/.../ubuntu/", valueSize = 11.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null, valueSize: androidx.compose.ui.unit.TextUnit = 13.sp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            value,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = valueSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActionGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\uD83D\uDCD6",
                name = "Install",
                desc = "Fresh Ubuntu 26.04",
                badge = "Installed",
                badgeColor = LinuxGreen,
                badgeBg = Color(0xFF0D5332),
                accentColor = Color(0xFF1F6FEB),
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u25B6",
                name = "Launch",
                desc = "Start Ubuntu session",
                accentColor = LinuxGreen,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u23F9",
                name = "Stop",
                desc = "Shutdown all sessions",
                accentColor = LinuxWarning,
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u21BB",
                name = "Update",
                desc = "apt update & upgrade",
                accentColor = LinuxPurple,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\uD83D\uDD27",
                name = "Repair",
                desc = "Fix common issues",
                accentColor = LinuxOrange,
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u2715",
                name = "Remove",
                desc = "Wipe Ubuntu entirely",
                accentColor = LinuxDanger,
                badge = "Danger",
                badgeColor = LinuxDanger,
                badgeBg = Color(0xFF3D1217),
            )
        }
    }
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    icon: String,
    name: String,
    desc: String,
    accentColor: Color,
    badge: String? = null,
    badgeColor: Color = Color.Transparent,
    badgeBg: Color = Color.Transparent,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (badge != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        badge,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(18.dp))
            }
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
