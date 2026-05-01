package com.helios.crisispin.utils

import kotlin.math.max
import kotlin.math.min

/**
 * Decentralized confidence scoring for emergency alerts.
 * Factors in social proof (ACKs), mesh density, distance (hops), and time.
 */
object ConfidenceEngine {

    /**
     * Computes a confidence score (0-100) for an incoming alert.
     */
    fun computeScore(
        uniqueAcks: Int,
        uniqueOrigins: Int,
        hop: Int,
        firstSeenTime: Long,
        duplicateAcks: Int
    ): Int {
        val now = System.currentTimeMillis()
        val minutesSince = ((now - firstSeenTime) / 60000L).toInt()

        // effectiveAcks weights confirmations higher if they come from close-by nodes
        val effectiveAcks = uniqueAcks / (1.0 + hop * 0.5)
        
        var score = 10.0 // Baseline confidence
        
        score += effectiveAcks * 5.0
        score += min(uniqueOrigins, 5) * 2.0
        
        score -= duplicateAcks * 3.0
        score -= max(0, hop - 3) * 2.0
        score -= minutesSince.toDouble()

        return score.toInt().coerceIn(0, 100)
    }

    /**
     * Maps numeric score to human-readable trust labels.
     * Step 8F: DO NOT imply absolute truth.
     */
    fun getConfidenceLabel(score: Int): String = when {
        score >= 70 -> "Widely confirmed nearby"
        score >= 30 -> "Some confirmations nearby"
        else -> "Unverified alert"
    }
}
