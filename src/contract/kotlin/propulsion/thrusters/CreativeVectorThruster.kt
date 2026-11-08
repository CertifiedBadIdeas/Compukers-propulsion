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
import ru.lazyhat.compukters.api.contracts.Finite
import ru.lazyhat.compukters.api.contracts.GuestCall
import ru.lazyhat.compukters.api.contracts.GuestCommand
import ru.lazyhat.compukters.api.contracts.GuestDevice
import ru.lazyhat.compukters.api.contracts.GuestRead
import ru.lazyhat.compukters.api.contracts.GuestRecord

/** Server observation. Thrust is in kN; vector coordinates are local normalized nozzle controls. */
@GuestRecord
value class CreativeVectorThrusterState(
    val gameTick: Long,
    val throttle: Double,
    val effectiveThrottle: Double,
    val thrustKn: Double,
    val maxThrustKn: Double,
    val customThrust: Boolean,
    val currentThrustKn: Double,
    val targetVectorX: Double,
    val targetVectorY: Double,
    val currentVectorX: Double,
    val currentVectorY: Double,
    val startupProgress: Double,
    val active: Boolean,
)

/** Mount relative to the calling computer's block, in construction axes (world axes outside a construction).
 * Empty constructionId denotes the ordinary world. Neutral force direction ignores live nozzle steering.
 */
@GuestRecord
value class CreativeVectorThrusterMount(
    val constructionId: String,
    val offsetX: Int,
    val offsetY: Int,
    val offsetZ: Int,
    val forceX: Int,
    val forceY: Int,
    val forceZ: Int,
)

@GuestDevice("propulsion:creative_vector_thruster", bindings = "VectorThrusterBindings")
interface CreativeVectorThruster {
    /** Read-only; rejects a mount on a different construction from the computer. */
    @GuestRead(binding = "vectorMount")
    fun mount(): CreativeVectorThrusterMount

    @GuestRead(binding = "vectorState")
    fun state(): CreativeVectorThrusterState

    /** Claims exclusive program control; normal Propulsion startup rules still apply. */
    @GuestCommand(binding = "vectorSetThrottle")
    fun setThrottle(
        @DoubleRange(min = 0.0, max = 1.0) throttle: Double,
    ): Unit

    /** Local nozzle controls, not world coordinates or degrees. Fractional targets retain upstream Float precision. */
    @GuestCommand(binding = "vectorSetVector")
    fun setVector(
        @DoubleRange(min = -1.0, max = 1.0) x: Double,
        @DoubleRange(min = -1.0, max = 1.0) y: Double,
    ): Unit

    /** Temporarily overrides creative engine thrust before throttle, bounded by state().maxThrustKn. */
    @GuestCommand(binding = "vectorSetThrustKn")
    fun setThrustKn(
        @Finite @DoubleRange(min = 0.0) thrust: Double,
    ): Unit

    /** Returns the thrust setting to the engine's saved scroll-wheel configuration. */
    @GuestCommand(binding = "vectorClearThrustOverride")
    fun clearThrustOverride(): Unit

    /** Releases this program's throttle, steering and custom thrust; invalidates the handle. */
    @GuestCall(binding = "vectorClose")
    fun close(): Unit
}
