/* Copyright 2026 Vsevolod Petrov (lazyhat). Licensed under Apache-2.0. */
package propulsion.heat

import compukter.peripheral.Peripheral
import compukter.peripheral.TypedPeripheralProvider

/** Native liquid-burner buffer observation in millibuckets. */
public value class LiquidBurnerState(
    public val gameTick: Long,
    public val fuel: String,
    public val amountMb: Int,
    public val capacityMb: Int,
    public val burning: Boolean,
)

public value class LiquidBurner internal constructor(private val handle: Int) : Peripheral {
    public companion object : TypedPeripheralProvider<LiquidBurner>("propulsion:liquid_burner") {
        override fun wrap(handle: Int): LiquidBurner = LiquidBurner(handle)
    }
    public fun state(): LiquidBurnerState = BurnerBindings.burnerState(handle)
}
private object BurnerBindings {
    external fun burnerState(handle: Int): LiquidBurnerState
}
