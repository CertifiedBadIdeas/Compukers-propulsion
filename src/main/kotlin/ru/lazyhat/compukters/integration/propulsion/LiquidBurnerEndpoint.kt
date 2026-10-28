// Copyright 2026 Vsevolod Petrov (lazyhat). Licensed under Apache-2.0.
package ru.lazyhat.compukters.integration.propulsion

import dev.propulsionteam.propulsionsimulated.content.heat.burners.liquid.LiquidBurnerBlockEntity
import net.minecraft.core.registries.BuiltInRegistries
import propulsion.LiquidBurnerState
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralContract
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralEndpoint

internal class LiquidBurnerEndpoint(
    private val entity: LiquidBurnerBlockEntity,
    private val reachable: () -> Boolean,
) : CompuktersPeripheralEndpoint {
    override val identity: Any get() = entity

    override fun valid(): Boolean {
        val level = entity.level ?: return false
        check(level.server?.isSameThread == true)
        return !entity.isRemoved && level.hasChunkAt(entity.blockPos) &&
            level.getBlockEntity(entity.blockPos) === entity && reachable()
    }

    fun state(): LiquidBurnerState {
        val handler = checkNotNull(entity.primaryFluidHandler)
        val fuel = handler.getFluidInTank(0)
        return LiquidBurnerState(
            checkNotNull(entity.level).gameTime,
            if (fuel.isEmpty) "" else BuiltInRegistries.FLUID.getKey(fuel.fluid).toString(),
            fuel.amount,
            handler.getTankCapacity(0),
            entity.isBurning,
        )
    }

    companion object {
        val contract =
            CompuktersPeripheralContract("propulsion:liquid_burner", "liquid_burner", "propulsion.heat.LiquidBurner") { location ->
                if (!location.level.hasChunkAt(location.position)) {
                    null
                } else {
                    (location.level.getBlockEntity(location.position) as? LiquidBurnerBlockEntity)
                        ?.takeUnless { it.isRemoved }
                        ?.let { LiquidBurnerEndpoint(it) { location.isReachable() } }
                }
            }
    }
}
