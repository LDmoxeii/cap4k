package com.only4.cap4k.ddd.application

/** Private Runtime outcome for an explicit operator redrive request. */
enum class JpaRedriveResult {
    /** This request performed the durable state reset and may trigger a wake-up. */
    REDRIVEN,

    /** The same record/request token was already accepted; no new reset was written. */
    ALREADY_APPLIED,

    /** The durable state, version, service, expiry, or lease fence rejected the request. */
    REJECTED
}
