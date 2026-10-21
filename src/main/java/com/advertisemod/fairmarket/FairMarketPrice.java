package com.advertisemod.fairmarket;

/** An admin-defined minimum price for one material, quoted per stack or full shulker. */
public record FairMarketPrice(int amount, String currency, String unit) {
}
