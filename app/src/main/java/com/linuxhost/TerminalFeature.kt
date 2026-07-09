package com.linuxhost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val TerminalBg = Color(0xFF0A0E14)
private val TerminalGreen = Color(0xFF98C379)
private val TerminalBlue = Color(0xFF61AFEF)
private val TerminalYellow = Color(0xFFE5C07B)
private val TerminalText = Color(0xFFABB2BF)

@Composable
fun TerminalScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Color(0xFF0D1117))
                .border(
                    width = 1.dp,
                    color = Color(0xFF30363D),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "ubuntu@localhost",
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(TerminalBg)
                .border(
                    width = 1.dp,
                    color = Color(0xFF30363D),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TerminalLine(prompt = "root@localhost", path = "~", cmd = "$", output = "neofetch")
                TerminalOutput("OS: Ubuntu 26.04 LTS aarch64")
                TerminalOutput("Kernel: 6.17.0-PRoot-Distro")
                TerminalOutput("Packages: 1847 (dpkg)")
                TerminalOutput("Shell: bash 5.2.37")
                TerminalOutput("Uptime: 2 hours, 14 mins")
                Spacer(Modifier.height(8.dp))
                TerminalLine(prompt = "root@localhost", path = "~", cmd = "$", output = "apt update")
                TerminalOutput("Hit:1 http://archive.ubuntu.com noble InRelease")
                TerminalOutput("Reading package lists... Done")
                TerminalOutput("12 packages can be upgraded.")
                Spacer(Modifier.height(8.dp))
                CursorLine()
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "root@localhost:~$",
                color = TerminalGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Type a command...",
                color = Color(0xFF484F58),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TerminalLine(prompt: String, path: String, cmd: String, output: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$prompt:", color = TerminalGreen, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(path, color = TerminalBlue, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(1.dp))
        Text("$cmd ", color = TerminalYellow, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(output, color = TerminalText, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TerminalOutput(text: String) {
    Text(
        text,
        color = TerminalText,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun CursorLine() {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            visible = !visible
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("root@localhost:", color = TerminalGreen, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text("~", color = TerminalBlue, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text("$ ", color = TerminalYellow, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        if (visible) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 16.dp)
                    .background(Color(0xFF528BFF))
            )
        }
    }
}
