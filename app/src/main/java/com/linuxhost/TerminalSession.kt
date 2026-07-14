package com.linuxhost

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*

private const val MAX_LINES = 500

data class TerminalLine(
    val text: String,
    val isInput: Boolean = false,
)

class TerminalSession(private val context: Context) {
    private var process: Process? = null
    private var writer: OutputStream? = null
    private var reader: InputStream? = null
    private var readerScope: CoroutineScope? = null
    private var readerJob: Job? = null

    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun startSession(prootCommand: List<String>): Boolean {
        return try {
            val pb = ProcessBuilder(prootCommand)
                .redirectErrorStream(true)
            val p = pb.start()
            process = p
            writer = p.outputStream
            reader = p.inputStream

            readerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            readerJob = readerScope!!.launch {
                try {
                    reader?.bufferedReader()?.use { bufferedReader ->
                        var line: String?
                        while (bufferedReader.readLine().also { line = it } != null) {
                            _lines.value = _lines.value + TerminalLine(text = line!!)
                            if (_lines.value.size > MAX_LINES) {
                                _lines.value = _lines.value.takeLast(MAX_LINES)
                            }
                        }
                    }
                } catch (_: IOException) {
                } catch (_: CancellationException) {
                }
            }

            _isRunning.value = true
            true
        } catch (_: IOException) {
            false
        }
    }

    fun writeCommand(command: String) {
        if (!_isRunning.value) return
        _lines.value = _lines.value + TerminalLine(text = command, isInput = true)
        if (_lines.value.size > MAX_LINES) {
            _lines.value = _lines.value.takeLast(MAX_LINES)
        }
        try {
            writer?.let {
                it.write((command + "\n").toByteArray(Charsets.UTF_8))
                it.flush()
            }
        } catch (_: IOException) {
        }
    }

    fun stopSession() {
        readerJob?.cancel()
        readerScope?.cancel()
        readerScope = null
        readerJob = null
        try {
            writer?.close()
            reader?.close()
        } catch (_: IOException) {
        }
        process?.destroy()
        process?.waitFor()
        _isRunning.value = false
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
