// Copyright 2026 Vsevolod Petrov (lazyhat). Licensed under Apache-2.0.
package propulsion.heat

import ru.lazyhat.compukters.api.contracts.GuestDevice
import ru.lazyhat.compukters.api.contracts.GuestRead
import ru.lazyhat.compukters.api.contracts.GuestRecord

/** Native liquid-burner buffer observation in millibuckets. */
@GuestRecord
value class LiquidBurnerState(
    val gameTick: Long,
    val fuel: String,
    val amountMb: Int,
    val capacityMb: Int,
    val burning: Boolean,
)

@GuestDevice("propulsion:liquid_burner", bindings = "BurnerBindings")
interface LiquidBurner {
    @GuestRead(binding = "burnerState")
    fun state(): LiquidBurnerState
}
