package com.runconnect.app.data.healthconnect

enum class HealthConnectStatus {
    CONNECTED,              // All required permissions granted
    LIMITED_PERMISSIONS,    // Some but not all permissions granted
    PERMISSION_REVOKED,     // Had permissions before, now missing one or more
    HC_UNAVAILABLE,         // Health Connect SDK not available on this device
    SYNC_IN_PROGRESS,
    SYNC_FAILED,
    NEVER_CONNECTED,        // First launch — no permissions have ever been granted
}
