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

import compukter.commands.Command
import compukter.commands.CommandArgument
import compukter.peripheral.Peripheral
import compukter.peripheral.TypedPeripheralProvider

/** Immutable state observed on the server; thrust is expressed in kilonewtons. */
public value class CreativeThrusterState(
    public val gameTick: Long,
    public val throttle: Double,
    public val effectiveThrottle: Double,
    public val thrustPercent: Int,
    public val configuredThrustKn: Double,
    public val currentThrustKn: Double,
    public val startupProgress: Double,
    public val active: Boolean,
    public val startingUp: Boolean,
    public val fadingOut: Boolean,
    public val width: Int,
    public val unobstructedBlocks: Int,
)

public value class CreativeThruster internal constructor(private val handle: Int) : Peripheral {
    public companion object : TypedPeripheralProvider<CreativeThruster>("propulsion:creative_thruster") {
        override fun wrap(handle: Int): CreativeThruster = CreativeThruster(handle)
    }

    public fun state(): CreativeThrusterState = ThrusterBindings.state(handle)

    /** Claims exclusive program control. Propulsion applies its ordinary startup and obstruction rules. */
    public fun setThrottle(throttle: Double) {
        validateThrottle(throttle)
        ThrusterBindings.setThrottle(handle, throttle)
    }

    /** Changes the saved engine setting, independently of the current throttle. */
    public fun setThrustPercent(percent: Int) {
        validateThrustPercent(percent)
        ThrusterBindings.setThrustPercent(handle, percent)
    }

    public fun setThrustPercentCommand(percent: Int): Command<Unit> {
        validateThrustPercent(percent)
        return Command.unit("propulsion.thrusters.ThrusterBindings.setThrustPercent", listOf(CommandArgument.int(handle), CommandArgument.int(percent)))
    }

    /** Creates a command description without changing the engine. Submit it with batch(). */
    public fun setThrottleCommand(throttle: Double): Command<Unit> {
        validateThrottle(throttle)
        return Command.unit("propulsion.thrusters.ThrusterBindings.setThrottle", listOf(CommandArgument.int(handle), CommandArgument.double(throttle)))
    }

    /** Invalidates this handle and releases digital control back to redstone. */
    public fun close() { ThrusterBindings.close(handle) }
}

private object ThrusterBindings {
    external fun acquire(name: String): Int
    external fun state(handle: Int): CreativeThrusterState
    external fun setThrottle(handle: Int, throttle: Double)
    external fun setThrustPercent(handle: Int, percent: Int)
    external fun close(handle: Int)
}
