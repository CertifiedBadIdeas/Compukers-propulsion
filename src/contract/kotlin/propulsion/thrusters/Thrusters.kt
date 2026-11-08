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
import ru.lazyhat.compukters.api.contracts.IntRange

/** Immutable state observed on the server; thrust is expressed in kilonewtons. */
@GuestRecord
value class CreativeThrusterState(
    val gameTick: Long,
    val throttle: Double,
    val effectiveThrottle: Double,
    val thrustPercent: Int,
    val configuredThrustKn: Double,
    val currentThrustKn: Double,
    val startupProgress: Double,
    val active: Boolean,
    val startingUp: Boolean,
    val fadingOut: Boolean,
    val width: Int,
    val unobstructedBlocks: Int,
)

@GuestDevice("propulsion:creative_thruster", bindings = "ThrusterBindings")
interface CreativeThruster {
    @GuestRead(binding = "state")
    fun state(): CreativeThrusterState

    /** Claims exclusive program control. Propulsion applies its ordinary startup and obstruction rules. */
    @GuestCommand(binding = "setThrottle")
    fun setThrottle(
        @DoubleRange(min = 0.0, max = 1.0) throttle: Double,
    ): Unit

    /** Changes the saved engine setting, independently of the current throttle. */
    @GuestCommand(binding = "setThrustPercent")
    fun setThrustPercent(
        @IntRange(min = 1, max = 100) percent: Int,
    ): Unit

    /** Invalidates this handle and releases digital control back to redstone. */
    @GuestCall(binding = "close")
    fun close(): Unit
}
