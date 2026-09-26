package com.samielmadani.orbit.model

data class DiscoveredDevice(
    val endpointId: String,
    val deviceName: String,
    val rssi: Int = -55,
    val trackIndex: Int = 1, // 0: inner, 1: mid, 2: outer orbit
    val initialAngle: Float = (Math.random() * Math.PI * 2).toFloat(),
    val orbitalSpeed: Float = (if (Math.random() > 0.5) 1 else -1) * (0.003f + (Math.random() * 0.004f).toFloat()),
    val isConnecting: Boolean = false,
    val isRecent: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
