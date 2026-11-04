/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */

package propulsion.thrusters

internal fun validateThrottle(throttle: Double) {
    if (!(throttle >= 0.0 && throttle <= 1.0)) throw IllegalArgumentException("Throttle must be finite and between 0 and 1")
}

internal fun validateThrustPercent(percent: Int) {
    if (percent < 1 || percent > 100) throw IllegalArgumentException("Thrust percentage must be between 1 and 100")
}

internal fun validateVector(x: Double, y: Double) {
    if (!(x >= -1.0 && x <= 1.0 && y >= -1.0 && y <= 1.0)) throw IllegalArgumentException("Vector coordinates must be finite and between -1 and 1")
}

internal fun validateThrust(thrust: Double) {
    if (!(thrust >= 0.0 && thrust <= Double.MAX_VALUE)) throw IllegalArgumentException("Thrust must be finite and nonnegative")
}
