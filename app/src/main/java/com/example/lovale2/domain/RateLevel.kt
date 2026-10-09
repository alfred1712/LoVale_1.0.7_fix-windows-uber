package com.example.lovale2.domain

enum class RateLevel { GREEN, YELLOW, RED, UNSET }

fun rateLevel(rate: Double, minimum: Double): RateLevel = when {
    !rate.isFinite() || !minimum.isFinite() || minimum <= 0.0 -> RateLevel.UNSET
    rate >= minimum -> RateLevel.GREEN
    rate >= minimum * 0.85 -> RateLevel.YELLOW
    else -> RateLevel.RED
}
