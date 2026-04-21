package com.example.helloworld

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.helloworld.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: EmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
    }

    // ── LiveData 관찰 ──────────────────────────────────────────────────────
    private fun setupObservers() {
        vm.connState.observe(this) { state ->
            val (label, color) = when (state) {
                ConnectionState.DISCONNECTED -> getString(R.string.state_disconnected) to 0xFF757575.toInt()
                ConnectionState.CONNECTING   -> getString(R.string.state_connecting)   to 0xFFFFA000.toInt()
                ConnectionState.CONNECTED    -> getString(R.string.state_connected)    to 0xFF2E7D32.toInt()
                ConnectionState.ERROR        -> getString(R.string.state_error)        to 0xFFC62828.toInt()
            }
            binding.tvConnState.text = label
            binding.tvConnState.setTextColor(color)

            val connected = state == ConnectionState.CONNECTED
            binding.btnConnect.isEnabled    = state != ConnectionState.CONNECTING && !connected
            binding.btnDisconnect.isEnabled = connected

            setControlEnabled(connected)
        }

        vm.elevatorState.observe(this) { state ->
            if (state == null) return@observe
            binding.tvFloor.text  = state.floor.toString()
            binding.tvDir.text    = state.dir.label
            binding.tvDoor.text   = state.door.label
            binding.tvStatus.text = state.status.label

            // 운전 상태에 따라 색상 변경
            val statusColor = when (state.status) {
                ElevatorStatus.NORMAL        -> 0xFF2E7D32.toInt()   // 녹색
                ElevatorStatus.FAULT,
                ElevatorStatus.COMM_ERROR    -> 0xFFC62828.toInt()   // 빨강
                ElevatorStatus.FIRE,
                ElevatorStatus.FIRE_DONE     -> 0xFFE65100.toInt()   // 주황
                else                         -> 0xFF1565C0.toInt()   // 파랑
            }
            binding.tvStatus.setTextColor(statusColor)
        }

        vm.lastResult.observe(this) { msg ->
            binding.tvControlResult.text = msg
        }

        vm.log.observe(this) { text ->
            binding.tvLog.text = text
            // 로그 스크롤 최하단 유지
            binding.scrollLog.post { binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    // ── 버튼 이벤트 ────────────────────────────────────────────────────────
    private fun setupListeners() {
        // 연결 / 해제
        binding.btnConnect.setOnClickListener {
            hideKeyboard()
            val host = binding.editIp.text.toString().trim()
            val port = binding.editPort.text.toString().toIntOrNull() ?: 3000
            val did  = binding.editDid.text.toString().trim().ifEmpty { "01" }
            if (host.isEmpty()) {
                Toast.makeText(this, R.string.error_ip_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.connect(host, port, did)
        }

        binding.btnDisconnect.setOnClickListener { vm.disconnect() }

        // 승강장 호출
        binding.btnHallFrontUp.setOnClickListener   { vm.hallCall(EmsParam.HALL_FRONT_UP,   getFloor()) }
        binding.btnHallRearUp.setOnClickListener    { vm.hallCall(EmsParam.HALL_REAR_UP,    getFloor()) }
        binding.btnHallFrontDown.setOnClickListener { vm.hallCall(EmsParam.HALL_FRONT_DOWN, getFloor()) }
        binding.btnHallRearDown.setOnClickListener  { vm.hallCall(EmsParam.HALL_REAR_DOWN,  getFloor()) }

        // 카 호출
        binding.btnCarFront.setOnClickListener { vm.carCall(EmsParam.CAR_FRONT, getFloor()) }
        binding.btnCarRear.setOnClickListener  { vm.carCall(EmsParam.CAR_REAR,  getFloor()) }

        // 도어 제어
        binding.btnDoorOpen.setOnClickListener  { vm.doorControl(open = true)  }
        binding.btnDoorClose.setOnClickListener { vm.doorControl(open = false) }

        // 로그 지우기
        binding.btnClearLog.setOnClickListener { binding.tvLog.text = "" }
    }

    // ── 유틸 ───────────────────────────────────────────────────────────────
    private fun getFloor(): Int =
        binding.editFloor.text.toString().toIntOrNull()?.coerceIn(1, 99) ?: 1

    private fun setControlEnabled(enabled: Boolean) {
        listOf(
            binding.btnHallFrontUp, binding.btnHallRearUp,
            binding.btnHallFrontDown, binding.btnHallRearDown,
            binding.btnCarFront, binding.btnCarRear,
            binding.btnDoorOpen, binding.btnDoorClose,
            binding.editFloor
        ).forEach { it.isEnabled = enabled }
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
