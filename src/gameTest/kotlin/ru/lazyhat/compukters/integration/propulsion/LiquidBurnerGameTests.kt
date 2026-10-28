// Copyright 2026 Vsevolod Petrov (lazyhat). Licensed under Apache-2.0.
package ru.lazyhat.compukters.integration.propulsion

import dev.propulsionteam.propulsionsimulated.content.heat.burners.liquid.LiquidBurnerBlockEntity
import dev.propulsionteam.propulsionsimulated.content.heat.engine.StirlingEngineBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@PrefixGameTestTemplate(false)
object LiquidBurnerGameTests {
    @JvmStatic
    @GameTest(batch = "liquid_burner", template = "bastion/mobs/empty", templateNamespace = "minecraft", timeoutTicks = 400)
    fun nativeFuelAndColdStirlingStartup(helper: GameTestHelper) {
        val pos = BlockPos(3, 3, 3)

        fun block(id: String) = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState()
        val burnerState = block("createpropulsion:liquid_burner")
        helper.setBlock(pos, burnerState)
        helper.setBlock(
            pos.above(),
            block("createpropulsion:stirling_engine").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
        )
        lateinit var endpoint: LiquidBurnerEndpoint
        var reachable = true
        helper
            .startSequence()
            .thenExecuteAfter(5) {
                val burner = helper.getBlockEntity(pos) as LiquidBurnerBlockEntity
                val fuel = BuiltInRegistries.FLUID.get(ResourceLocation.parse("createpropulsion:turpentine"))
                helper.assertTrue(
                    burner.primaryFluidHandler.fill(FluidStack(fuel, 200), IFluidHandler.FluidAction.EXECUTE) == 200,
                    "Native prime failed",
                )
                endpoint = LiquidBurnerEndpoint(burner) { reachable }
                helper.assertTrue(endpoint.state().amountMb == 200 && endpoint.state().capacityMb == 200, "Wrong buffer sample")
            }.thenWaitUntil {
                val sample = endpoint.state()
                helper.assertTrue(
                    sample.gameTick == helper.level.gameTime && sample.fuel == "createpropulsion:turpentine",
                    "Wrong native tick/fuel",
                )
                helper.assertTrue(sample.burning && sample.amountMb < 200, "Native burner did not consume finite fuel")
                helper.assertTrue(
                    (helper.getBlockEntity(pos.above()) as StirlingEngineBlockEntity).generatedSpeed != 0f,
                    "Cold Stirling did not start from real heat",
                )
            }.thenExecute {
                reachable = false
                helper.assertTrue(!endpoint.valid(), "Network loss did not invalidate burner")
                reachable = true
                helper.setBlock(pos, Blocks.AIR)
                helper.setBlock(pos, burnerState)
                helper.assertTrue(!endpoint.valid(), "Replacement inherited the old endpoint")
            }.thenSucceed()
    }
}
