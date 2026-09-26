package com.samielmadani.orbit.model

enum class TransferStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    WAITING_CONFIRMATION,
    TRANSFERRING,
    PAUSED,
    RECONNECTING,
    COMPLETED,
    FAILED,
    CANCELLED,
    STORAGE_ERROR
}

