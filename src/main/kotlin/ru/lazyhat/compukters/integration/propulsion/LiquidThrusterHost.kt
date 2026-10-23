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

package ru.lazyhat.compukters.integration.propulsion

import dev.propulsionteam.propulsionsimulated.PropulsionConfig
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode
import dev.propulsionteam.propulsionsimulated.content.thruster.ThrusterFuelManager
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
import dev.ryanhcode.sable.Sable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import propulsion.LiquidThrusterMount
import propulsion.LiquidThrusterState
import ru.lazyhat.compukters.api.addon.AddonCallResult
import ru.lazyhat.compukters.api.addon.addonCompleted
import ru.lazyhat.compukters.api.addon.addonFailed
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersComputerContext
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralAccessException
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralContract
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPersistentPeripheralEndpoint
import ru.lazyhat.compukters.lang.runtime.vm.HostFailureKind

internal class LiquidThrusterHost(
    private val computer: CompuktersComputerContext,
    private val leases: ThrusterControlLeases<ThrusterBlockEntity>,
) {
    internal class BoundThruster(
        val entity: ThrusterBlockEntity,
        private val reachable: () -> Boolean,
    ) : CompuktersPersistentPeripheralEndpoint {
        override val identity: Any get() = entity
        override val persistentIdentity: String get() = (entity as TransientThrusterControl).`compukters$persistentIdentity`()
        private var stale = false

        override fun valid(): Boolean {
            if (!stale && !reachable()) stale = true
            return !stale
        }
    }

    private val handles = mutableMapOf<Int, BoundThruster>()

    fun acquire(name: String): AddonCallResult<Int> {
        checkThread()
        return try {
            val handle = computer.peripheralNamed(contract, name)
            if (handle == 0) unavailable() else addonCompleted(handle)
        } catch (failure: CompuktersPeripheralAccessException) {
            addonFailed(failure.kind, failure.message)
        }
    }

    fun mount(handle: Int): AddonCallResult<LiquidThrusterMount> =
        withHandle(handle) { bound ->
            val body = Sable.HELPER.getContaining(computer.level, computer.position)
            if (body !== Sable.HELPER.getContaining(computer.level, bound.entity.blockPos)) {
                return@withHandle invalid("Thruster and computer are on different constructions")
            }
            val offset = bound.entity.blockPos.subtract(computer.position)
            val force = bound.entity.facing
            addonCompleted(
                LiquidThrusterMount(
                    body?.uniqueId?.toString() ?: "",
                    offset.x,
                    offset.y,
                    offset.z,
                    force.stepX,
                    force.stepY,
                    force.stepZ,
                ),
            )
        }

    fun state(handle: Int): AddonCallResult<LiquidThrusterState> =
        withHandle(handle) { bound ->
            val entity = bound.entity
            val fluid = entity.fluidStack()
            val properties = entity.getFuelProperties(fluid.fluid)
            val usable = !fluid.isEmpty && entity.validFluid() && properties != null
            val nominal =
                if (usable) {
                    PropulsionConfig.BASE_THRUST.get() * (entity as TransientThrusterControl).`compukters$atmosphericFactor`() *
                        requireNotNull(properties).thrustMultiplier() * ThrusterFuelManager.getEfficiency(fluid.fluid)
                } else {
                    0.0
                }
            addonCompleted(
                LiquidThrusterState(
                    computer.level.gameTime,
                    entity.throttle.toDouble(),
                    entity.effectiveThrottle.toDouble(),
                    nominal,
                    entity.currentThrust.toDouble() / PropulsionConfig.getThrustUnitsPerKnOrDefault(),
                    if (fluid.isEmpty) "" else BuiltInRegistries.FLUID.getKey(fluid.fluid).toString(),
                    entity.fuelAmountMb,
                    entity.fuelCapacityMb,
                    usable,
                    entity.startupProgress.toDouble(),
                    entity.isActive,
                    entity.unobstructedBlocks,
                ),
            )
        }

    fun setThrottle(
        handle: Int,
        throttle: Double,
    ): AddonCallResult<Unit> {
        if (!throttle.isFinite() || throttle !in 0.0..1.0) return invalid("Throttle must be finite and between 0 and 1")
        return withHandle(handle) { bound ->
            val entity = bound.entity
            if (entity.computerBehaviour?.hasAttachedComputer() == true) return@withHandle invalid("Thruster controlled by ComputerCraft")
            if (!leases.claim(entity, this, {
                    bound.valid() && entity.computerBehaviour?.hasAttachedComputer() != true
                }) { restoreRedstone(entity) }
            ) {
                return@withHandle invalid("Thruster controlled by another program")
            }
            // The native setter's deadband must not leave a tiny positive command at shutdown.
            if (throttle == 0.0) {
                (entity as TransientThrusterControl).`compukters$clearProgramPower`()
            } else {
                entity.setDigitalInput(throttle.toFloat())
            }
            entity.setControlMode(ControlMode.PERIPHERAL)
            addonCompleted(Unit)
        }
    }

    fun close(handle: Int): AddonCallResult<Unit> =
        withHandle(handle) { bound ->
            computer.closePeripheral(contract, handle)
            handles.remove(handle)
            leases.release(bound.entity, this)
            addonCompleted(Unit)
        }

    fun checkpoint(): List<ThrusterCommand> {
        checkThread()
        return handles.entries.sortedBy { it.key }.distinctBy { it.value.entity }.mapNotNull { (handle, bound) ->
            if (!leases.ownedBy(bound.entity, this)) return@mapNotNull null
            val control = bound.entity as TransientThrusterControl
            ThrusterCommand(handle, control.`compukters$peripheralMode`(), control.`compukters$digitalInput`())
        }
    }

    fun restoreCheckpoint(commands: List<ThrusterCommand>) {
        checkThread()
        commands.forEach { command ->
            val bound =
                try {
                    computer.peripheral(contract, command.handle)
                } catch (failure: CompuktersPeripheralAccessException) {
                    if (failure.kind != HostFailureKind.INPUT_OUTPUT) throw failure
                    return@forEach
                }
            val entity = bound.entity
            require(entity.computerBehaviour?.hasAttachedComputer() != true) { "Thruster controlled by ComputerCraft" }
            require(leases.claim(entity, this, bound::valid) { restoreRedstone(entity) }) { "Thruster controlled by another program" }
            handles[command.handle] = bound
            if (command.digitalInput == 0f) {
                (entity as TransientThrusterControl).`compukters$clearProgramPower`()
            } else {
                entity.setDigitalInput(command.digitalInput)
            }
            entity.setControlMode(if (command.peripheralMode) ControlMode.PERIPHERAL else ControlMode.NORMAL)
            entity.setChanged()
        }
    }

    fun reset() {
        checkThread()
        try {
            leases.releaseOwner(this)
        } finally {
            handles.clear()
        }
    }

    private fun <T> withHandle(
        handle: Int,
        action: (BoundThruster) -> AddonCallResult<T>,
    ): AddonCallResult<T> {
        checkThread()
        val bound =
            try {
                computer.peripheral(contract, handle)
            } catch (failure: CompuktersPeripheralAccessException) {
                handles.remove(handle)?.let { leases.release(it.entity, this) }
                return addonFailed(failure.kind, failure.message)
            }
        handles[handle] = bound
        if (!bound.valid()) {
            leases.release(bound.entity, this)
            handles.remove(handle)
            return unavailable()
        }
        return action(bound)
    }

    private fun checkThread() = check(computer.level.server.isSameThread) { "Thrusters require the server thread" }

    private fun unavailable() =
        addonFailed(HostFailureKind.UNAVAILABLE, "Liquid thruster is missing, closed, disconnected, replaced or unloaded")

    private fun invalid(message: String) = addonFailed(HostFailureKind.OTHER, message)

    companion object {
        const val DEVICE_KEY = "liquid_thruster"
        val contract =
            CompuktersPeripheralContract("propulsion:liquid_thruster", DEVICE_KEY, "propulsion.thrusters.LiquidThruster") { location ->
                resolve(location.level, location.position)?.let { entity ->
                    BoundThruster(entity) { resolve(location.level, location.position) === entity && location.isReachable() }
                }
            }

        fun resolve(
            level: ServerLevel,
            position: BlockPos,
        ): ThrusterBlockEntity? {
            if (!level.hasChunkAt(position)) return null
            val entity = level.getBlockEntity(position) as? ThrusterBlockEntity ?: return null
            // This API's fuel/capacity contract is intentionally the standard single liquid engine.
            if (entity.javaClass != ThrusterBlockEntity::class.java || !entity.isController || entity.width != 1 ||
                entity.isRemoved
            ) {
                return null
            }
            return entity
        }

        private fun restoreRedstone(entity: ThrusterBlockEntity) {
            if (entity.computerBehaviour?.hasAttachedComputer() == true) return
            (entity as TransientThrusterControl).`compukters$clearProgramPower`()
            entity.setControlMode(ControlMode.NORMAL)
            val level = entity.level
            if (level != null && level.hasChunkAt(entity.blockPos)) entity.setRedstoneInput(level.getBestNeighborSignal(entity.blockPos))
            // Never call updateThrust/tick here: those methods consume liquid fuel.
            entity.setChanged()
        }
    }
}
