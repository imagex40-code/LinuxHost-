package com.linuxhost

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsGroup {
            SettingsToggle(
                title = "Dark Theme",
                subtitle = "Use dark color scheme",
                initialOn = true,
            )
            SettingsClick(
                title = "Terminal Font Size",
                subtitle = "Current: 13px",
                value = "13",
            )
            SettingsToggle(
                title = "Keep Awake",
                subtitle = "Prevent screen sleep while running",
                initialOn = true,
            )
        }

        SettingsGroup {
            SettingsClick(
                title = "Storage Location",
                subtitle = "Internal storage",
                value = "Internal",
            )
            SettingsToggle(
                title = "Auto-Update",
                subtitle = "Daily apt update check",
                initialOn = false,
            )
            SettingsClick(
                title = "Backup Schedule",
                subtitle = "Weekly automatic backups",
                value = "Weekly",
            )
        }

        SettingsGroup {
            SettingsClick(
                title = "About LinuxHost",
                subtitle = "Version 0.1.0",
                value = "",
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "LinuxHost v0.1 \u00B7 Built for Termux + PRoot\nMade for the Android Linux community",
            color = Color(0xFF484F58),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(14.dp)),
    ) {
        content()
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, initialOn: Boolean) {
    var isOn by remember { mutableStateOf(initialOn) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        ToggleSwitch(isOn = isOn) { isOn = !isOn }
    }
}

@Composable
private fun SettingsClick(title: String, subtitle: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        if (value.isNotEmpty()) {
            Text(
                "\u276F $value",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        } else {
            Text(
                "\u276F",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ToggleSwitch(isOn: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOn) LinuxGreenDark else Color(0xFF21262D))
            .clickable { onToggle() }
            .padding(2.dp),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6EDF3))
        )
    }
}
