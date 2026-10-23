/*
 * The Compukters Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package propulsion.thrusters

import compukter.peripheral.Peripheral
import compukter.peripheral.TypedPeripheralProvider

/** Native server sample for an independent standard liquid engine. Thrust is in kN, fuel in mB.
 * Nominal thrust excludes startup/throttle/obstruction; current thrust remains native authority.
 */
public value class LiquidThrusterState(
    public val gameTick: Long,
    public val throttle: Double,
    public val effectiveThrottle: Double,
    public val nominalThrustKn: Double,
    public val currentThrustKn: Double,
    public val fuel: String,
    public val fuelAmountMb: Int,
    public val fuelCapacityMb: Int,
    public val usableFuel: Boolean,
    public val startupProgress: Double,
    public val active: Boolean,
    public val unobstructedBlocks: Int,
)

/** Neutral thrust axis and block offset from the computer, in construction coordinates. */
public value class LiquidThrusterMount(
    public val constructionId: String,
    public val offsetX: Int,
    public val offsetY: Int,
    public val offsetZ: Int,
    public val forceX: Int,
    public val forceY: Int,
    public val forceZ: Int,
)

/** Standard single liquid thruster; no synthetic fuel, thrust override or host flight controller. */
public value class LiquidThruster internal constructor(private val handle: Int) : Peripheral {
    public companion object : TypedPeripheralProvider<LiquidThruster>("propulsion:liquid_thruster") {
        override fun wrap(handle: Int): LiquidThruster = LiquidThruster(handle)
    }

    public fun mount(): LiquidThrusterMount = LiquidThrusterBindings.liquidMount(handle)
    public fun state(): LiquidThrusterState = LiquidThrusterBindings.liquidState(handle)

    /** Exclusive program lease; Propulsion retains its native startup/fuel/update cadence. */
    public fun setThrottle(throttle: Double) {
        if (!(throttle >= 0.0 && throttle <= 1.0)) throw IllegalArgumentException("Throttle must be finite and between 0 and 1")
        LiquidThrusterBindings.liquidSetThrottle(handle, throttle)
    }

    public fun close() { LiquidThrusterBindings.liquidClose(handle) }
}

internal object LiquidThrusterBindings {
    external fun liquidAcquire(name: String): Int
    external fun liquidMount(handle: Int): LiquidThrusterMount
    external fun liquidState(handle: Int): LiquidThrusterState
    external fun liquidSetThrottle(handle: Int, throttle: Double)
    external fun liquidClose(handle: Int)
}
