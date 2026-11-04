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

import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.creative_vector_thruster.CreativeVectorThrusterBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry

@PrefixGameTestTemplate(false)
object CommandBatchGameTests {
    @JvmStatic
    @GameTest(batch = "propulsion_commands", template = "bastion/mobs/empty", templateNamespace = "minecraft", timeoutTicks = 100_000)
    fun guestCommandList(helper: GameTestHelper) {
        val computer = BlockPos(2, 2, 3)
        val positions = listOf(BlockPos(0, 2, 1), BlockPos(4, 2, 1), BlockPos(0, 2, 5), BlockPos(4, 2, 5))
        helper.setBlock(computer, CompuktersRegistry.COMPUTER.get())
        val block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("createpropulsion", "creative_vector_thruster"))
        positions.forEach { helper.setBlock(it, block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)) }
        val appliedTicks = LongArray(4) { -1 }
        var observing = false
        helper.onEachTick {
            if (observing) {
                positions.forEachIndexed { index, position ->
                    val engine = helper.getBlockEntity(position) as CreativeVectorThrusterBlockEntity
                    if (engine.throttle == (index + 1) * 0.125f && appliedTicks[index] == -1L) {
                        appliedTicks[index] = helper.level.gameTime
                    }
                }
            }
        }
        val guest = GuestComputerScenario(helper, computer)
        val sequence = helper.startSequence()
        sequence.thenExecuteAfter(5) {
            PeripheralNetworkGameTestFixtures.bind(helper, *listOf(computer).plus(positions).map(helper::absolutePos).toTypedArray())
            positions.forEachIndexed {
                index,
                position,
                ->
                PeripheralNetworkGameTestFixtures.name(helper, helper.absolutePos(position), "e$index")
            }
        }
        guest.prepare(sequence, SOURCE)
        guest.awaitMarker(sequence, "commands-described")
        sequence.thenExecute {
            positions.forEach {
                val engine = helper.getBlockEntity(it) as CreativeVectorThrusterBlockEntity
                helper.assertTrue(!PropulsionGuestIntegration.isControlled(engine), "Describing a command claimed control")
            }
            observing = true
        }
        guest.resume(sequence)
        guest.awaitMarker(sequence, "commands-applied")
        sequence.thenWaitUntil { helper.assertTrue(appliedTicks.all { it >= 0 }, "Waiting to observe all engine updates") }
        sequence.thenExecute {
            helper.assertTrue(
                appliedTicks.all { it >= 0 } && appliedTicks.distinct().size == 1,
                "Commands reached engines in different ticks",
            )
            observing = false
            positions.forEachIndexed { index, position ->
                val engine = helper.getBlockEntity(position) as CreativeVectorThrusterBlockEntity
                helper.assertTrue(engine.throttle == (index + 1) * 0.125f, "Command $index did not reach the actual engine")
                helper.assertTrue(PropulsionGuestIntegration.isControlled(engine), "Command $index did not claim program control")
            }
        }
        guest.terminate(sequence)
        sequence.thenSucceed()
    }

    private val SOURCE =
        """
        import propulsion.thrusters.CreativeVectorThruster
        import compukter.commands.batch
        import compukter.concurrent.async
        fun main() {
            val e0 = CreativeVectorThruster.named("e0")
            val e1 = CreativeVectorThruster.named("e1")
            val e2 = CreativeVectorThruster.named("e2")
            val stale = CreativeVectorThruster.named("e3")
            val unavailable = stale.setThrottleCommand(0.9)
            stale.close()
            val e3 = CreativeVectorThruster.named("e3")
            val commands = listOf(e0.setThrottleCommand(0.125), unavailable,
                e1.setThrottleCommand(0.25), e2.setThrottleCommand(0.375), e3.setThrottleCommand(0.5))
            println("commands-described")
            readln()
            val results = async { batch(commands) }.await()
            check(results.size == 5 && results[1].isFailure)
            results[0].getOrThrow()
            results[2].getOrThrow()
            results[3].getOrThrow()
            results[4].getOrThrow()
            println("commands-applied")
            readln()
        }
        """.trimIndent()
}
