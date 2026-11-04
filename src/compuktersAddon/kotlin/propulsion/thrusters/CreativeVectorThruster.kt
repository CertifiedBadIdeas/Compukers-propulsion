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

/** Server observation. Thrust is in kN; vector coordinates are local normalized nozzle controls. */
public value class CreativeVectorThrusterState(
    public val gameTick: Long,
    public val throttle: Double,
    public val effectiveThrottle: Double,
    public val thrustKn: Double,
    public val maxThrustKn: Double,
    public val customThrust: Boolean,
    public val currentThrustKn: Double,
    public val targetVectorX: Double,
    public val targetVectorY: Double,
    public val currentVectorX: Double,
    public val currentVectorY: Double,
    public val startupProgress: Double,
    public val active: Boolean,
)

/** Mount relative to the calling computer's block, in construction axes (world axes outside a construction).
 * Empty constructionId denotes the ordinary world. Neutral force direction ignores live nozzle steering.
 */
public value class CreativeVectorThrusterMount(
    public val constructionId: String,
    public val offsetX: Int,
    public val offsetY: Int,
    public val offsetZ: Int,
    public val forceX: Int,
    public val forceY: Int,
    public val forceZ: Int,
)

public value class CreativeVectorThruster internal constructor(private val handle: Int) : Peripheral {
    public companion object : TypedPeripheralProvider<CreativeVectorThruster>("propulsion:creative_vector_thruster") {
        override fun wrap(handle: Int): CreativeVectorThruster = CreativeVectorThruster(handle)
    }

    /** Read-only; rejects a mount on a different construction from the computer. */
    public fun mount(): CreativeVectorThrusterMount = VectorThrusterBindings.vectorMount(handle)

    public fun state(): CreativeVectorThrusterState = VectorThrusterBindings.vectorState(handle)

    /** Claims exclusive program control; normal Propulsion startup rules still apply. */
    public fun setThrottle(throttle: Double) {
        validateThrottle(throttle)
        VectorThrusterBindings.vectorSetThrottle(handle, throttle)
    }

    /** Local nozzle controls, not world coordinates or degrees. Fractional targets retain upstream Float precision. */
    public fun setVector(x: Double, y: Double) {
        validateVector(x, y)
        VectorThrusterBindings.vectorSetVector(handle, x, y)
    }

    /** Temporarily overrides creative engine thrust before throttle, bounded by state().maxThrustKn. */
    public fun setThrustKn(thrust: Double) {
        validateThrust(thrust)
        VectorThrusterBindings.vectorSetThrustKn(handle, thrust)
    }

    /** Returns the thrust setting to the engine's saved scroll-wheel configuration. */
    public fun clearThrustOverride() { VectorThrusterBindings.vectorClearThrustOverride(handle) }

    public fun setVectorCommand(x: Double, y: Double): Command<Unit> {
        validateVector(x, y)
        return Command.unit("propulsion.thrusters.VectorThrusterBindings.vectorSetVector", listOf(CommandArgument.int(handle), CommandArgument.double(x), CommandArgument.double(y)))
    }

    public fun setThrustKnCommand(thrust: Double): Command<Unit> {
        validateThrust(thrust)
        return Command.unit("propulsion.thrusters.VectorThrusterBindings.vectorSetThrustKn", listOf(CommandArgument.int(handle), CommandArgument.double(thrust)))
    }

    public fun clearThrustOverrideCommand(): Command<Unit> =
        Command.unit("propulsion.thrusters.VectorThrusterBindings.vectorClearThrustOverride", listOf(CommandArgument.int(handle)))

    /** Creates a command description without changing the engine. Submit it with batch(). */
    public fun setThrottleCommand(throttle: Double): Command<Unit> {
        validateThrottle(throttle)
        return Command.unit("propulsion.thrusters.VectorThrusterBindings.vectorSetThrottle", listOf(CommandArgument.int(handle), CommandArgument.double(throttle)))
    }

    /** Releases this program's throttle, steering and custom thrust; invalidates the handle. */
    public fun close() { VectorThrusterBindings.vectorClose(handle) }
}

internal object VectorThrusterBindings {
    external fun vectorAcquire(name: String): Int
    external fun vectorMount(handle: Int): CreativeVectorThrusterMount
    external fun vectorState(handle: Int): CreativeVectorThrusterState
    external fun vectorSetThrottle(handle: Int, throttle: Double)
    external fun vectorSetVector(handle: Int, x: Double, y: Double)
    external fun vectorSetThrustKn(handle: Int, thrust: Double)
    external fun vectorClearThrustOverride(handle: Int)
    external fun vectorClose(handle: Int)
}
