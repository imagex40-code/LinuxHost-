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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject

private val TerminalBg = Color(0xFF0A0E14)
private val TerminalGreen = Color(0xFF98C379)
private val TerminalBlue = Color(0xFF61AFEF)
private val TerminalYellow = Color(0xFFE5C07B)
private val TerminalText = Color(0xFFABB2BF)

@Composable
fun TerminalScreen() {
    val session = koinInject<TerminalSession>()
    val lines by session.lines.collectAsState()
    val isRunning by session.isRunning.collectAsState()
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val engine = koinInject<ProotEngine>()

    LaunchedEffect(isRunning) {
        if (isRunning) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ubuntu@localhost",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                )
                if (isRunning) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LinuxGreen)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(TerminalBg)
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        "Type a command to start.\nUse 'bash' to enter the Ubuntu shell.",
                        color = Color(0xFF484F58),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    lines.forEach { line ->
                        Text(
                            line.text,
                            color = if (line.isInput) TerminalYellow else TerminalText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            placeholder = {
                Text(
                    "Type a command...",
                    color = Color(0xFF484F58),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
            textStyle = TextStyle(
                color = TerminalYellow,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161B22),
                unfocusedContainerColor = Color(0xFF161B22),
                cursorColor = LinuxGreen,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (input.isNotBlank()) {
                        if (!isRunning) {
                            val cmd = listOf(
                                engine.prootBin.absolutePath, "-0", "--link2symlink",
                                "-b", "/proc:/proc",
                                "-b", "/sys:/sys",
                                "-b", "/dev:/dev",
                                "-b", "/sdcard:/sdcard",
                                "-r", engine.rootfsDir.absolutePath,
                                "/usr/bin/env", "-i",
                                "HOME=/root", "USER=root", "TERM=xterm-256color",
                                "/bin/bash", "--login",
                            )
                            session.startSession(cmd)
                        }
                        session.writeCommand(input)
                        input = ""
                    }
                }
            ),
        )
    }
}
