package com.linuxhost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinInject

@Composable
fun ManagerScreen() {
    val engine = koinInject<ProotEngine>()
    val status by engine.status.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { engine.checkStatus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoCard(status = status)

        Text(
            "Actions",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )

        ActionGrid(
            status = status,
            onInstall = { scope.launch {
                engine.downloadProotBinary()
                val tarball = engine.downloadRootfs()
                engine.installRootfs(tarball)
            }},
            onLaunch = { scope.launch { engine.launch() } },
            onStop = { engine.stop() },
            onUpdate = { scope.launch { engine.updatePackages() } },
            onRepair = { scope.launch { engine.repair() } },
            onRemove = { scope.launch { engine.remove() } },
        )
    }
}

@Composable
private fun InfoCard(status: InstanceStatus) {
    val (statusText, statusColor) = when (status) {
        InstanceStatus.RUNNING -> "Running" to LinuxGreen
        InstanceStatus.STOPPED -> "Stopped" to LinuxWarning
        InstanceStatus.INSTALLED -> "Installed" to LinuxInfo
        InstanceStatus.INSTALLING -> "Installing..." to LinuxWarning
        InstanceStatus.ERROR -> "Error" to LinuxDanger
        InstanceStatus.NOT_INSTALLED -> "Not Installed" to Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("Status", statusText, valueColor = statusColor)
            HorizontalDivider(color = Color(0xFF21262D))
            InfoRow("Version", "Ubuntu 26.04 LTS")
            HorizontalDivider(color = Color(0xFF21262D))
            InfoRow("Architecture", "aarch64")
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
private fun ActionGrid(
    status: InstanceStatus,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onUpdate: () -> Unit,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
) {
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
                accentColor = Color(0xFF1F6FEB),
                onClick = onInstall,
                enabled = status == InstanceStatus.NOT_INSTALLED,
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u25B6",
                name = "Launch",
                desc = "Start Ubuntu session",
                accentColor = LinuxGreen,
                onClick = onLaunch,
                enabled = status == InstanceStatus.INSTALLED || status == InstanceStatus.STOPPED,
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
                onClick = onStop,
                enabled = status == InstanceStatus.RUNNING,
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u21BB",
                name = "Update",
                desc = "apt update & upgrade",
                accentColor = LinuxPurple,
                onClick = onUpdate,
                enabled = status == InstanceStatus.RUNNING,
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
                onClick = onRepair,
                enabled = status == InstanceStatus.RUNNING || status == InstanceStatus.ERROR,
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = "\u2715",
                name = "Remove",
                desc = "Wipe Ubuntu entirely",
                accentColor = LinuxDanger,
                onClick = onRemove,
                enabled = status != InstanceStatus.NOT_INSTALLED && status != InstanceStatus.INSTALLING,
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
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else Color(0xFF0D1117),
        ),
        shape = RoundedCornerShape(14.dp),
        onClick = { if (enabled) onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else Color(0xFF484F58),
            )
            Text(
                desc,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF484F58),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
