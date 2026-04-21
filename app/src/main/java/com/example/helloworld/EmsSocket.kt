package com.example.helloworld

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * EMS TCP 소켓 클라이언트.
 *
 * - [connect] : IO 스레드에서 소켓 연결 수행
 * - [send]    : 패킷 문자열을 서버로 전송
 * - [packets] : 수신된 완전한 패킷 스트림 (SharedFlow)
 * - [disconnect] : 연결 종료
 */
class EmsSocket(private val scope: CoroutineScope) {

    companion object {
        private const val SOCKET_READ_TIMEOUT_MS = 5_000
    }

    private val _packets = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val packets = _packets.asSharedFlow()

    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var readerJob: Job? = null

    var isConnected: Boolean = false
        private set

    /** 지정된 host:port 로 TCP 연결. 성공 시 수신 루프 시작. */
    suspend fun connect(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        runCatching {
            val s = Socket(host, port).also {
                it.soTimeout = SOCKET_READ_TIMEOUT_MS
            }
            socket = s
            writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
            isConnected = true
            startReading(s)
        }
    }

    /** 연결 종료 및 자원 해제. */
    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        socket = null
        writer = null
        isConnected = false
    }

    /** 패킷 문자열을 서버로 전송. */
    fun send(packet: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val w = writer ?: return@launch
                w.write(packet)
                w.flush()
            }
        }
    }

    // ── 내부: 수신 루프 ────────────────────────────────────────────────────
    private fun startReading(s: Socket) {
        readerJob = scope.launch(Dispatchers.IO) {
            val reader = InputStreamReader(s.getInputStream(), Charsets.UTF_8)
            val buf = StringBuilder()
            val cbuf = CharArray(4096)
            try {
                while (isActive) {
                    val n = try {
                        reader.read(cbuf)
                    } catch (_: SocketTimeoutException) {
                        continue  // 타임아웃이면 계속 대기
                    }
                    if (n < 0) break  // 서버 측 연결 종료
                    buf.append(cbuf, 0, n)

                    // 완성된 패킷 단위로 추출
                    while (buf.length >= 4) {
                        val lenStr = buf.substring(0, 4)
                        val contentLen = lenStr.toIntOrNull() ?: run {
                            buf.clear()  // 파싱 불가 — 버퍼 초기화
                            break
                        }
                        val totalLen = 4 + contentLen
                        if (buf.length < totalLen) break
                        val packet = buf.substring(0, totalLen)
                        buf.delete(0, totalLen)
                        _packets.emit(packet)
                    }
                }
            } catch (_: Exception) {
                // 소켓 오류 — 조용히 종료
            } finally {
                isConnected = false
            }
        }
    }
}
