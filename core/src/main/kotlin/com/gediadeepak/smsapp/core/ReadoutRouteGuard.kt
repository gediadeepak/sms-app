package com.gediadeepak.smsapp.core

enum class ReadoutRouteDecision { WAIT_MUTED, PLAY, STOP }

/** Once routing is lost, a private readout must stop and must never resume itself. */
class ReadoutRouteGuard(private val expectedOutput: Int) {
    private var verified = false
    private var stopped = false
    fun inspect(reportedOutputs: Set<Int>, selectedOutput: Int?): ReadoutRouteDecision {
        if (stopped) return ReadoutRouteDecision.STOP
        if (selectedOutput != expectedOutput || reportedOutputs.any { it != expectedOutput } || (verified && reportedOutputs.isEmpty())) {
            stopped = true
            return ReadoutRouteDecision.STOP
        }
        if (reportedOutputs.isEmpty()) return ReadoutRouteDecision.WAIT_MUTED
        verified = true
        return ReadoutRouteDecision.PLAY
    }
}
