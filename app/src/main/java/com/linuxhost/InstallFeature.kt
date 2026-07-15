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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

sealed interface InstallUiState {
    data object Idle : InstallUiState
    data class Downloading(val progress: Int, val message: String, val bytesDownloaded: Long, val totalBytes: Long) : InstallUiState
    data class Extracting(val progress: Int, val message: String) : InstallUiState
    data object Configuring : InstallUiState
    data object Complete : InstallUiState
    data class Error(val message: String) : InstallUiState
}

class InstallViewModel(private val engine: ProotEngine) : ViewModel() {
    private val _state = MutableStateFlow<InstallUiState>(InstallUiState.Idle)
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    val engineStatus = engine.status

    private var downloadStartTime = 0L

    fun startInstallation() {
        viewModelScope.launch {
            try {
                downloadStartTime = System.currentTimeMillis()

                val collector = launch {
                    engine.progress.collect { p ->
                        when (val s = _state.value) {
                            is InstallUiState.Idle, is InstallUiState.Downloading -> {
                                _state.value = InstallUiState.Downloading(
                                    progress = p.percent,
                                    message = p.message,
                                    bytesDownloaded = p.bytesDownloaded,
                                    totalBytes = p.totalBytes,
                                )
                            }
                            is InstallUiState.Extracting -> {
                                _state.value = InstallUiState.Extracting(
                                    progress = p.percent,
                                    message = p.message,
                                )
                            }
                            else -> {}
                        }
                    }
                }

                val tarballPath = engine.downloadRootfs()

                _state.value = InstallUiState.Extracting(0, "Extracting rootfs...")
                engine.installRootfs(tarballPath)

                collector.cancel()
                _state.value = InstallUiState.Configuring
                _state.value = InstallUiState.Complete
            } catch (e: Exception) {
                _state.value = InstallUiState.Error(
                    e.message ?: "Installation failed. Please check your connection and try again.",
                )
            }
        }
    }

    fun reset() {
        _state.value = InstallUiState.Idle
    }

    fun retry() {
        reset()
        startInstallation()
    }

    fun cleanupAndRetry() {
        viewModelScope.launch {
            try {
                engine.cleanupInterrupted()
                _state.value = InstallUiState.Idle
            } catch (e: Exception) {
                _state.value = InstallUiState.Error(
                    e.message ?: "Cleanup failed. Please try again."
                )
            }
        }
    }
}

private val steps = listOf("Download", "Extract", "Configure", "Done")

private fun stepIndex(state: InstallUiState): Int = when (state) {
    is InstallUiState.Idle -> -1
    is InstallUiState.Downloading -> 0
    is InstallUiState.Extracting -> 1
    is InstallUiState.Configuring -> 2
    is InstallUiState.Complete -> 3
    is InstallUiState.Error -> -1
}

@Composable
fun InstallScreen(
    onLaunchDashboard: () -> Unit = {},
    viewModel: InstallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    val currentStep = remember(state) { stepIndex(state) }

    val isInterrupted = engineStatus == InstanceStatus.INTERRUPTED && state is InstallUiState.Idle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Terminal,
                contentDescription = "LinuxHost",
                modifier = Modifier.size(48.dp),
                tint = LinuxGreen,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "LinuxHost Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Set up your Ubuntu environment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            if (state !is InstallUiState.Error && !isInterrupted) {
                StepIndicator(currentStep = currentStep)
                Spacer(Modifier.height(24.dp))
            }

            when {
                isInterrupted -> InterruptedContent(
                    onCleanup = viewModel::cleanupAndRetry,
                )
                state is InstallUiState.Idle -> IdleContent(onGetStarted = viewModel::startInstallation)
                state is InstallUiState.Downloading -> DownloadingContent(state as InstallUiState.Downloading)
                state is InstallUiState.Extracting -> ExtractingContent(state as InstallUiState.Extracting)
                state is InstallUiState.Configuring -> ConfiguringContent()
                state is InstallUiState.Complete -> CompleteContent(onLaunchDashboard = onLaunchDashboard)
                state is InstallUiState.Error -> ErrorContent(
                    message = (state as InstallUiState.Error).message,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, label ->
            val isActive = index <= currentStep
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isActive) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive && index == currentStep -> LinuxGreen
                                isActive -> LinuxGreen.copy(alpha = 0.5f)
                                else -> LinuxBorder
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (isActive) LinuxGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(24.dp)
                        .background(
                            if (index < currentStep) LinuxGreen else LinuxBorder,
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun InterruptedContent(onCleanup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "Interrupted",
            modifier = Modifier.size(64.dp),
            tint = LinuxWarning,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Previous Install Interrupted",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A previous installation didn't finish.\nPartial files are still on disk.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCleanup,
            colors = ButtonDefaults.buttonColors(containerColor = LinuxDanger),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                "Clean Up & Start Fresh",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun IdleContent(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Welcome!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This wizard will download and install Ubuntu\non your device. Make sure you have a stable\ninternet connection and at least 2 GB of free space.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(containerColor = LinuxGreenDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                "Get Started",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun DownloadingContent(state: InstallUiState.Downloading) {
    val startTime = remember { System.currentTimeMillis() }
    val elapsed = (System.currentTimeMillis() - startTime) / 1000
    val speed = if (elapsed > 0) state.bytesDownloaded / elapsed else 0L
    val remaining = state.totalBytes - state.bytesDownloaded
    val eta = if (speed > 0) remaining / speed else 0L

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.message,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = LinuxGreen,
            trackColor = LinuxBorder,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${state.progress}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatSpeed(speed),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (eta > 0) {
                Text(
                    "ETA: ${formatDuration(eta)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtractingContent(state: InstallUiState.Extracting) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.message,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = LinuxGreen,
            trackColor = LinuxBorder,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.progress}%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfiguringContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = LinuxGreen,
            strokeWidth = 4.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Setting up your Ubuntu...",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Applying configurations",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompleteContent(onLaunchDashboard: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Complete",
            modifier = Modifier.size(72.dp),
            tint = LinuxGreen,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ubuntu is ready!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your Ubuntu environment has been installed\nand configured successfully.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLaunchDashboard,
            colors = ButtonDefaults.buttonColors(containerColor = LinuxGreenDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                "Launch Dashboard",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = LinuxDanger,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Installation Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = LinuxGreenDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Retry",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes > 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    bytes > 1024L * 1024 -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
    bytes > 1024L -> "%.2f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec > 1024L * 1024 -> "%.2f MB/s".format(bytesPerSec.toDouble() / (1024 * 1024))
    bytesPerSec > 1024L -> "%.2f KB/s".format(bytesPerSec.toDouble() / 1024)
    else -> "$bytesPerSec B/s"
}

private fun formatDuration(seconds: Long): String = when {
    seconds > 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds > 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}
