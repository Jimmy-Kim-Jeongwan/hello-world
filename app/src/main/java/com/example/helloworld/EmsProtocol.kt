package com.example.helloworld

// ─── 커맨드 상수 ───────────────────────────────────────────────────────────────
object EmsCmd {
    const val STATUS_REQUEST       = 10
    const val STATUS_RESPONSE      = 11
    const val CONTROL_REQUEST      = 20
    const val CONTROL_RESPONSE     = 21
    const val CALL_STATUS_REQUEST  = 30
    const val CALL_STATUS_RESPONSE = 31
}

// ─── 제어 Param 상수 ─────────────────────────────────────────────────────────
object EmsParam {
    const val HALL_FRONT_UP   = 1
    const val HALL_REAR_UP    = 2
    const val HALL_FRONT_DOWN = 3
    const val HALL_REAR_DOWN  = 4
    const val CAR_FRONT       = 5
    const val CAR_REAR        = 6
    const val DOOR_OPEN       = 7
    const val DOOR_CLOSE      = 8
}

// ─── 호출 상태 Param 이름 ────────────────────────────────────────────────────
object EmsCallParam {
    const val CAR_FRONT      = "Car_f"
    const val CAR_REAR       = "Car_r"
    const val HALL_UP_FRONT  = "HallUp_f"
    const val HALL_UP_REAR   = "HallUp_r"
    const val HALL_DN_FRONT  = "HallDn_f"
    const val HALL_DN_REAR   = "HallDn_r"
}

// ─── 방향 ────────────────────────────────────────────────────────────────────
enum class ElevatorDir(val code: Int, val label: String) {
    UP(1, "상향 ↑"),
    DOWN(2, "하향 ↓"),
    IDLE(3, "대기중");

    companion object {
        fun from(v: Int) = entries.firstOrNull { it.code == v } ?: IDLE
    }
}

// ─── 도어 상태 ───────────────────────────────────────────────────────────────
enum class ElevatorDoor(val code: Int, val label: String) {
    FRONT_OPEN(1, "정면 열림"),
    REAR_OPEN(2, "후면 열림"),
    CLOSED(3, "닫힘"),
    OTHER(0, "기타");

    companion object {
        fun from(v: Int) = entries.firstOrNull { it.code == v } ?: OTHER
    }
}

// ─── 운전 상태 ───────────────────────────────────────────────────────────────
enum class ElevatorStatus(val code: Int, val label: String) {
    FAULT(0, "고장"),
    NORMAL(1, "표준운전"),
    EXCLUSIVE(3, "전용운전"),
    MAINTENANCE(4, "보수운전"),
    RESCUE(5, "구출운전"),
    FIRE(6, "화재운전"),
    EARTHQUAKE(7, "지진운전"),
    SECURITY(8, "방범운전"),
    FIREFIGHTER(9, "소방운전"),
    FIRE_DONE(10, "화재완료"),
    EARTHQUAKE_DONE(11, "지진완료"),
    FIREFIGHTER2(12, "소방운전2"),
    PARKING(13, "파킹운전"),
    COMM_ERROR(14, "통신이상");

    companion object {
        fun from(v: Int) = entries.firstOrNull { it.code == v } ?: FAULT
    }
}

// ─── 승강기 상태 데이터 ──────────────────────────────────────────────────────
data class ElevatorState(
    val did: String = "--",
    val floor: Int = 0,
    val dir: ElevatorDir = ElevatorDir.IDLE,
    val door: ElevatorDoor = ElevatorDoor.CLOSED,
    val status: ElevatorStatus = ElevatorStatus.NORMAL
)

// ─── 제어 응답 결과 ──────────────────────────────────────────────────────────
data class ControlResult(
    val did: String,
    val param: Int,
    val data: String,
    val success: Boolean
)

// ─── 패킷 빌더 / 파서 ────────────────────────────────────────────────────────
object EmsPacket {

    /** 상태 요청 (Cmd=10) */
    fun buildStatusRequest(did: String): String {
        val body = "Cmd=${EmsCmd.STATUS_REQUEST}&Did=$did"
        return frame(body)
    }

    /** 제어 요청 (Cmd=20) */
    fun buildControlRequest(did: String, param: Int, data: String = ""): String {
        val body = buildString {
            append("Cmd=${EmsCmd.CONTROL_REQUEST}&Did=$did&Param=$param")
            if (data.isNotEmpty()) append("&Data=$data")
        }
        return frame(body)
    }

    /** 호출 상태 요청 (Cmd=30) */
    fun buildCallStatusRequest(did: String, param: String): String {
        val body = "Cmd=${EmsCmd.CALL_STATUS_REQUEST}&Did=$did&Param=$param"
        return frame(body)
    }

    /** 4-byte 길이 헤더 추가 */
    private fun frame(body: String): String {
        val len = body.length.toString().padStart(4, '0')
        return "$len$body"
    }

    /**
     * 완성된 패킷 문자열을 파싱해 key=value 맵으로 반환.
     * 패킷이 불완전하거나 형식 오류이면 null.
     */
    fun parse(packet: String): Map<String, String>? {
        if (packet.length < 4) return null
        return runCatching {
            val contentLen = packet.substring(0, 4).toInt()
            val body = packet.substring(4, 4 + contentLen)
            body.split("&").associate { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) pair to "" else pair.substring(0, idx) to pair.substring(idx + 1)
            }
        }.getOrNull()
    }

    /** Cmd=11 응답 → ElevatorState */
    fun parseElevatorState(params: Map<String, String>): ElevatorState? {
        val did   = params["Did"]    ?: return null
        val floor = params["Floor"]  ?.toIntOrNull() ?: 0
        val dir   = ElevatorDir.from(params["Dir"]   ?.toIntOrNull() ?: 3)
        val door  = ElevatorDoor.from(params["Door"] ?.toIntOrNull() ?: 0)
        val stat  = ElevatorStatus.from(params["Status"]?.toIntOrNull() ?: 0)
        return ElevatorState(did, floor, dir, door, stat)
    }

    /** Cmd=21 응답 → ControlResult */
    fun parseControlResult(params: Map<String, String>): ControlResult? {
        // 서버 응답에서 Did/DId 혼용 가능
        val did   = params["DId"] ?: params["Did"] ?: return null
        val param = params["Param"]?.toIntOrNull()  ?: return null
        val data  = params["Data"] ?: ""
        val ok    = params["Result"]?.lowercase() == "ok"
        return ControlResult(did, param, data, ok)
    }
}
