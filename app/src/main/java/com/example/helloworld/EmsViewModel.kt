package com.example.helloworld

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class EmsViewModel : ViewModel() {

    companion object {
        private const val POLLING_INTERVAL_MS = 500L
        private const val MAX_LOG_LINES       = 100
    }

    private val socket = EmsSocket(viewModelScope)

    // ── 연결 상태 ──────────────────────────────────────────────────────────
    private val _connState = MutableLiveData(ConnectionState.DISCONNECTED)
    val connState: LiveData<ConnectionState> = _connState

    // ── 승강기 상태 ────────────────────────────────────────────────────────
    private val _elevatorState = MutableLiveData<ElevatorState?>()
    val elevatorState: LiveData<ElevatorState?> = _elevatorState

    // ── 마지막 제어 결과 ───────────────────────────────────────────────────
    private val _lastResult = MutableLiveData<String>()
    val lastResult: LiveData<String> = _lastResult

    // ── 통신 로그 ─────────────────────────────────────────────────────────
    private val _log = MutableLiveData("")
    val log: LiveData<String> = _log

    private var pollJob: Job? = null
    private var currentDid: String = "01"

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        socket.packets
            .onEach { handlePacket(it) }
            .launchIn(viewModelScope)
    }

    // ── 공개 API ──────────────────────────────────────────────────────────

    /** EMS 서버에 연결하고 상태 폴링을 시작한다. */
    fun connect(host: String, port: Int, did: String) {
        currentDid = did.padStart(2, '0')
        _connState.value = ConnectionState.CONNECTING
        viewModelScope.launch {
            val result = socket.connect(host, port)
            if (result.isSuccess) {
                _connState.postValue(ConnectionState.CONNECTED)
                appendLog("✅ 연결됨 → $host:$port (호기 $currentDid)")
                startPolling()
            } else {
                _connState.postValue(ConnectionState.ERROR)
                appendLog("❌ 연결 실패: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 연결 종료. */
    fun disconnect() {
        stopPolling()
        socket.disconnect()
        _connState.value = ConnectionState.DISCONNECTED
        appendLog("🔌 연결 종료")
    }

    /** 상태 요청 (Cmd=10) 단발 전송. */
    fun requestStatus() {
        if (!socket.isConnected) return
        socket.send(EmsPacket.buildStatusRequest(currentDid))
    }

    /** 승강장 호출 (Param 1~4). */
    fun hallCall(param: Int, floor: Int) {
        if (!socket.isConnected) return
        val data = floor.toString().padStart(2, '0')
        socket.send(EmsPacket.buildControlRequest(currentDid, param, data))
        appendLog("→ 승강장 호출 Param=$param 층=$floor")
    }

    /** 카 호출 (Param 5~6). */
    fun carCall(param: Int, floor: Int) {
        if (!socket.isConnected) return
        val data = floor.toString().padStart(2, '0')
        socket.send(EmsPacket.buildControlRequest(currentDid, param, data))
        appendLog("→ 카 호출 Param=$param 층=$floor")
    }

    /** 도어 열림/닫힘 (Param 7/8). */
    fun doorControl(open: Boolean) {
        if (!socket.isConnected) return
        val param = if (open) EmsParam.DOOR_OPEN else EmsParam.DOOR_CLOSE
        socket.send(EmsPacket.buildControlRequest(currentDid, param))
        appendLog("→ 도어 ${if (open) "열림" else "닫힘"} 호출")
    }

    /** 호출 등록 상태 요청 (Cmd=30). */
    fun requestCallStatus(callParam: String) {
        if (!socket.isConnected) return
        socket.send(EmsPacket.buildCallStatusRequest(currentDid, callParam))
        appendLog("→ 호출상태 요청 Param=$callParam")
    }

    // ── 내부 ──────────────────────────────────────────────────────────────

    private fun startPolling() {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (isActive && socket.isConnected) {
                requestStatus()
                delay(POLLING_INTERVAL_MS)
            }
            // 폴링이 끊기면 연결 오류로 표시
            if (_connState.value == ConnectionState.CONNECTED) {
                _connState.postValue(ConnectionState.ERROR)
                appendLog("⚠️ 서버 연결이 끊어졌습니다")
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun handlePacket(packet: String) {
        val params = EmsPacket.parse(packet) ?: return
        val cmd = params["Cmd"]?.toIntOrNull() ?: return
        when (cmd) {
            EmsCmd.STATUS_RESPONSE -> {
                val state = EmsPacket.parseElevatorState(params)
                _elevatorState.postValue(state)
            }
            EmsCmd.CONTROL_RESPONSE -> {
                val cr = EmsPacket.parseControlResult(params)
                if (cr != null) {
                    val msg = if (cr.success) "✅ 제어 성공 (Param=${cr.param})" else "❌ 제어 실패 (Param=${cr.param})"
                    _lastResult.postValue(msg)
                    appendLog("← $msg")
                }
            }
            EmsCmd.CALL_STATUS_RESPONSE -> {
                val entries = params.filterKeys { it != "Cmd" && it != "Did" }
                appendLog("← 호출상태 $entries")
            }
        }
    }

    private fun appendLog(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val ts = timeFmt.format(Date())
            val prev = _log.value ?: ""
            val lines = prev.lines().takeLast(MAX_LOG_LINES - 1)
            _log.value = (lines + "[$ts] $msg").joinToString("\n")
        }
    }

    override fun onCleared() {
        super.onCleared()
        socket.disconnect()
    }
}
