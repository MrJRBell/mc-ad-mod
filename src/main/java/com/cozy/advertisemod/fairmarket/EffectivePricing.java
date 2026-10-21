package com.cozy.advertisemod.fairmarket;

import com.google.gson.annotations.SerializedName;

/** Item-level price exposed by the web feed after applying a fair market floor. */
public class EffectivePricing {

    @SerializedName("price_amount")
    private final long priceAmount;
    @SerializedName("price_unit")
    private final String priceUnit;
    @SerializedName("sell_amount")
    private final int sellAmount;
    @SerializedName("sell_unit")
    private final String sellUnit;
    @SerializedName("fair_market_floor_applied")
    private final boolean fairMarketFloorApplied;

    public EffectivePricing(long priceAmount, String priceUnit, int sellAmount, String sellUnit,
                            boolean fairMarketFloorApplied) {
        this.priceAmount = priceAmount;
        this.priceUnit = priceUnit;
        this.sellAmount = sellAmount;
        this.sellUnit = sellUnit;
        this.fairMarketFloorApplied = fairMarketFloorApplied;
    }

    public long getPriceAmount() { return priceAmount; }
    public String getPriceUnit() { return priceUnit; }
    public int getSellAmount() { return sellAmount; }
    public String getSellUnit() { return sellUnit; }
    public boolean isFairMarketFloorApplied() { return fairMarketFloorApplied; }
}
