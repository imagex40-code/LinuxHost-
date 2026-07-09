package com.linuxhost

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DashboardScreen() {
    val engine = koinInject<ProotEngine>()
    val status by engine.status.collectAsState()
    val scope = rememberCoroutineScope()
    var breakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
    var pkgCount by remember { mutableStateOf(0) }
    var pendingUpdates by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        engine.checkStatus()
        breakdown = engine.getStorageBreakdown()
        pkgCount = engine.getPackageCount()
        pendingUpdates = engine.getPendingUpdates()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Good morning, root",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        StatusCard(status = status)
        StorageCard(breakdown = breakdown)
        StatsGrid(pkgCount = pkgCount, pendingUpdates = pendingUpdates)
        HealthCard(status = status)
        QuickActions(
            status = status,
            onLaunch = { scope.launch { engine.launch() } },
            onStop = { engine.stop() },
            onUpdate = { scope.launch { engine.updatePackages() } },
            onRepair = { scope.launch { engine.repair() } },
        )
    }
}

@Composable
private fun StatusCard(status: InstanceStatus) {
    val (label, color) = when (status) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Ubuntu 26.04",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Resolute Raccoon \u00B7 aarch64",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D5332))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StorageCard(breakdown: StorageBreakdown?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Storage", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    "${formatBytes(breakdown?.totalBytes ?: 0)} used",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            val pct = if ((breakdown?.totalBytes ?: 0) > 0) {
                (breakdown?.totalBytes ?: 0).toFloat() / 8_000_000_000f
            } else 0f
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = LinuxGreen,
                trackColor = Color(0xFF21262D),
            )
            Spacer(Modifier.height(12.dp))
            breakdown?.let { b ->
                StorageBar("RootFS", formatBytes(b.rootfsBytes), b.rootfsBytes.toFloat() / b.totalBytes.toFloat().coerceAtLeast(1f), Color(0xFF1F6FEB))
                Spacer(Modifier.height(6.dp))
                StorageBar("APT Cache", formatBytes(b.aptCacheBytes), b.aptCacheBytes.toFloat() / b.totalBytes.toFloat().coerceAtLeast(1f), Color(0xFFD29922))
                Spacer(Modifier.height(6.dp))
                StorageBar("Logs", formatBytes(b.logsBytes), b.logsBytes.toFloat() / b.totalBytes.toFloat().coerceAtLeast(1f), Color(0xFF8B949E))
                Spacer(Modifier.height(6.dp))
                StorageBar("Temp", formatBytes(b.tempBytes), b.tempBytes.toFloat() / b.totalBytes.toFloat().coerceAtLeast(1f), LinuxDanger)
                Spacer(Modifier.height(6.dp))
                StorageBar("Python", formatBytes(b.pythonPackagesBytes), b.pythonPackagesBytes.toFloat() / b.totalBytes.toFloat().coerceAtLeast(1f), LinuxPurple)
            }
        }
    }
}

@Composable
private fun StorageBar(name: String, size: String, fraction: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(size, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color(0xFF21262D),
        )
    }
}

@Composable
private fun StatsGrid(pkgCount: Int = 0, pendingUpdates: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            number = if (pkgCount > 0) "$pkgCount" else "--",
            label = "Packages",
            sub = if (pendingUpdates > 0) "$pendingUpdates updates available" else "Up to date",
            subColor = if (pendingUpdates > 0) LinuxGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatCard(
            modifier = Modifier.weight(1f),
            number = "3",
            label = "Active Sessions",
            sub = "PID 842, 921, 1043",
            subColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    number: String,
    label: String,
    sub: String,
    subColor: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(number, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(sub, color = subColor, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HealthCard(status: InstanceStatus) {
    val (text, color) = when (status) {
        InstanceStatus.RUNNING -> "All systems healthy" to LinuxGreen
        InstanceStatus.STOPPED -> "Ubuntu is stopped" to LinuxWarning
        InstanceStatus.ERROR -> "Issues detected" to LinuxDanger
        else -> "Waiting..." to Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(text, color = color, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Last check: just now",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun QuickActions(
    status: InstanceStatus,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onUpdate: () -> Unit,
    onRepair: () -> Unit,
) {
    Column {
        Text(
            "Quick Actions",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onLaunch,
                modifier = Modifier.weight(1f),
                enabled = status == InstanceStatus.INSTALLED || status == InstanceStatus.STOPPED,
                colors = ButtonDefaults.buttonColors(containerColor = LinuxGreenDark),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Launch") }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                enabled = status == InstanceStatus.RUNNING,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Stop") }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onUpdate,
                modifier = Modifier.weight(1f),
                enabled = status == InstanceStatus.RUNNING,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Update") }
            Button(
                onClick = onRepair,
                modifier = Modifier.weight(1f),
                enabled = status == InstanceStatus.RUNNING || status == InstanceStatus.ERROR,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Repair") }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes > 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    bytes > 1024L * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    bytes > 1024L -> "%.1f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}
