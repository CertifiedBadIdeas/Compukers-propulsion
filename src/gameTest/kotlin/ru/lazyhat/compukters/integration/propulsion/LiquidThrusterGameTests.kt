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

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorSaveResult

@PrefixGameTestTemplate(false)
object LiquidThrusterGameTests {
    @JvmStatic
    @GameTest(batch = "propulsion_liquid", template = "bastion/mobs/empty", templateNamespace = "minecraft", timeoutTicks = 100_000)
    fun finiteFuelAndProgramOwnership(helper: GameTestHelper) {
        val first = BlockPos(2, 2, 3)
        val second = BlockPos(4, 2, 5)
        val engine = BlockPos(5, 2, 3)
        helper.setBlock(first, CompuktersRegistry.COMPUTER.get())
        helper.setBlock(second, CompuktersRegistry.COMPUTER.get())
        val block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:thruster"))
        helper.setBlock(engine, block.defaultBlockState().setValue(AbstractThrusterBlock.FACING, Direction.UP))
        val thruster = helper.getBlockEntity(engine) as ThrusterBlockEntity
        val owner = GuestComputerScenario(helper, first)
        val contender = GuestComputerScenario(helper, second)
        val sequence = helper.startSequence()
        var remaining = 0
        sequence.thenExecuteAfter(5) {
            thruster.tank.primaryHandler.fill(
                FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("createpropulsion:turpentine")), 1000),
                IFluidHandler.FluidAction.EXECUTE,
            )
            PeripheralNetworkGameTestFixtures.bind(helper, *listOf(first, second, engine).map(helper::absolutePos).toTypedArray())
            helper.assertTrue(
                PeripheralNetworkGameTestFixtures.name(helper, helper.absolutePos(engine), "engine") ==
                    PeripheralConfiguratorSaveResult.NAMED_DEVICE,
                "Standard liquid engine was not discovered",
            )
        }
        owner.prepare(sequence, OWNER)
        owner.awaitMarker(sequence, "liquid-owned")
        sequence.thenExecute {
            helper.assertTrue(thruster.throttle == 0.375f, "Guest throttle did not reach the native liquid engine")
            val saved = thruster.saveWithFullMetadata(helper.level.registryAccess())
            val copy = ThrusterBlockEntity(helper.absolutePos(engine), thruster.blockState)
            copy.loadWithComponents(saved, helper.level.registryAccess())
            helper.assertTrue(copy.fuelAmountMb == thruster.fuelAmountMb, "Native save changed finite fuel")
            helper.assertTrue(
                (copy as TransientThrusterControl).`compukters$digitalInput`() == 0f,
                "Native reload retained unowned throttle",
            )
        }
        contender.prepare(sequence, CONTENDER)
        contender.awaitMarker(sequence, "liquid-busy")
        sequence.thenExecute { remaining = thruster.fuelAmountMb }
        owner.resume(sequence)
        owner.awaitMarker(sequence, "liquid-zero")
        sequence.thenExecute {
            helper.assertTrue((thruster as TransientThrusterControl).`compukters$digitalInput`() == 0f, "Tiny command survived exact zero")
            helper.assertTrue(thruster.fuelAmountMb <= remaining, "Observation/control created fuel")
            remaining = thruster.fuelAmountMb
        }
        sequence.thenExecuteAfter(30) { helper.assertTrue(thruster.fuelAmountMb == remaining, "Zero throttle consumed fuel") }
        owner.terminate(sequence)
        sequence.thenWaitUntil {
            helper.assertTrue(
                !PropulsionGuestIntegration.isControlled(thruster),
                "Cancellation leaked the engine lease",
            )
        }
        contender.resume(sequence)
        contender.awaitMarker(sequence, "liquid-released")
        sequence.thenExecute {
            helper.assertTrue((thruster as TransientThrusterControl).`compukters$digitalInput`() == 0f, "Close leaked digital power")
            helper.assertTrue(!PropulsionGuestIntegration.isControlled(thruster), "Close leaked ownership")
            helper.assertTrue(thruster.fuelAmountMb in 0..999, "Native engine did not consume its finite fuel")
        }
        contender.resume(sequence)
        sequence.thenSucceed()
    }

    private val OWNER =
        """
        import propulsion.thrusters.LiquidThruster
        fun main() {
            val engine = LiquidThruster.named("engine")
            check(engine == LiquidThruster.first())
            check(LiquidThruster.all().size == 1)
            val mount = engine.mount()
            check(mount.forceY == 1)
            val state = engine.state()
            check(state.fuelAmountMb == 1000 && state.fuelCapacityMb >= 1000 && state.usableFuel)
            check(state.nominalThrustKn > 0.0 && state.gameTick >= 0L)
            var invalid = false
            try { engine.setThrottle(0.0 / 0.0) } catch (e: IllegalArgumentException) { invalid = true }
            check(invalid)
            engine.setThrottle(0.375)
            println("liquid-owned")
            readln()
            engine.setThrottle(0.00005)
            engine.setThrottle(0.0)
            check(engine.state().throttle == 0.0)
            println("liquid-zero")
            readln()
        }
        """.trimIndent()

    private val CONTENDER =
        """
        import propulsion.thrusters.LiquidThruster
        fun main() {
            var busy = false
            try { LiquidThruster.named("engine") } catch (e: IllegalStateException) { busy = true }
            check(busy)
            println("liquid-busy")
            readln()
            val engine = LiquidThruster.named("engine")
            engine.setThrottle(0.25)
            engine.close()
            println("liquid-released")
            readln()
        }
        """.trimIndent()
}
