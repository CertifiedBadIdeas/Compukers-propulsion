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

import ru.lazyhat.compukters.api.contracts.DoubleRange
import ru.lazyhat.compukters.api.contracts.GuestCall
import ru.lazyhat.compukters.api.contracts.GuestCommand
import ru.lazyhat.compukters.api.contracts.GuestDevice
import ru.lazyhat.compukters.api.contracts.GuestRead
import ru.lazyhat.compukters.api.contracts.GuestRecord

/** Native server sample for an independent standard liquid engine. Thrust is in kN, fuel in mB.
 * Nominal thrust excludes startup/throttle/obstruction; current thrust remains native authority.
 */
@GuestRecord
value class LiquidThrusterState(
    val gameTick: Long,
    val throttle: Double,
    val effectiveThrottle: Double,
    val nominalThrustKn: Double,
    val currentThrustKn: Double,
    val fuel: String,
    val fuelAmountMb: Int,
    val fuelCapacityMb: Int,
    val usableFuel: Boolean,
    val startupProgress: Double,
    val active: Boolean,
    val unobstructedBlocks: Int,
)

/** Neutral thrust axis and block offset from the computer, in construction coordinates. */
@GuestRecord
value class LiquidThrusterMount(
    val constructionId: String,
    val offsetX: Int,
    val offsetY: Int,
    val offsetZ: Int,
    val forceX: Int,
    val forceY: Int,
    val forceZ: Int,
)

/** Standard single liquid thruster; no synthetic fuel, thrust override or host flight controller. */
@GuestDevice("propulsion:liquid_thruster", bindings = "LiquidThrusterBindings")
interface LiquidThruster {
    @GuestRead(binding = "liquidMount")
    fun mount(): LiquidThrusterMount

    @GuestRead(binding = "liquidState")
    fun state(): LiquidThrusterState

    /** Exclusive program lease; Propulsion retains its native startup/fuel/update cadence. */
    @GuestCommand(binding = "liquidSetThrottle")
    fun setThrottle(
        @DoubleRange(min = 0.0, max = 1.0) throttle: Double,
    ): Unit

    @GuestCall(binding = "liquidClose")
    fun close(): Unit
}
