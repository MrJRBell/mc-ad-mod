package com.cozy.advertisemod.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Full shop feed object serialized directly for the /api/shops JSON endpoint.
 */
public class ShopFeedItem {

    @SerializedName("barrel_id")
    private final String barrelId;

    @SerializedName("shop_name")
    private final String shopName;

    @SerializedName("owner_name")
    private final String ownerName;

    @SerializedName("owner_uuid")
    private final String ownerUuid;

    @SerializedName("location")
    private final LocationDto location;

    @SerializedName("pricing")
    private final PricingDto pricing;

    @SerializedName("listings")
    private final List<ShopListing> listings;

    public ShopFeedItem(String barrelId, String shopName, String ownerName, String ownerUuid,
                        LocationDto location, PricingDto pricing, List<ShopListing> listings) {
        this.barrelId = barrelId;
        this.shopName = shopName;
        this.ownerName = ownerName;
        this.ownerUuid = ownerUuid;
        this.location = location;
        this.pricing = pricing;
        this.listings = listings;
    }

    public static ShopFeedItem fromBarrel(RegisteredBarrel barrel, List<ShopListing> listings) {
        LocationDto loc = new LocationDto(barrel.getWorld(), barrel.getX(), barrel.getY(), barrel.getZ());
        PricingDto pricing = new PricingDto(
                barrel.getPriceAmount(),
                barrel.getPriceUnit(),
                barrel.getSellAmount(),
                barrel.getSellUnit()
        );
        return new ShopFeedItem(
                barrel.getId(),
                barrel.getShopName(),
                barrel.getOwnerName(),
                barrel.getOwnerUuid().toString(),
                loc,
                pricing,
                listings
        );
    }

    public String getBarrelId() {
        return barrelId;
    }

    public String getShopName() {
        return shopName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public LocationDto getLocation() {
        return location;
    }

    public PricingDto getPricing() {
        return pricing;
    }

    public List<ShopListing> getListings() {
        return listings;
    }

    public static class LocationDto {
        @SerializedName("world")
        private final String world;

        @SerializedName("x")
        private final int x;

        @SerializedName("y")
        private final int y;

        @SerializedName("z")
        private final int z;

        public LocationDto(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getWorld() {
            return world;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }

    public static class PricingDto {
        @SerializedName("price_amount")
        private final int priceAmount;

        @SerializedName("price_unit")
        private final String priceUnit;

        @SerializedName("sell_amount")
        private final int sellAmount;

        @SerializedName("sell_unit")
        private final String sellUnit;

        public PricingDto(int priceAmount, String priceUnit, int sellAmount, String sellUnit) {
            this.priceAmount = priceAmount;
            this.priceUnit = priceUnit;
            this.sellAmount = sellAmount;
            this.sellUnit = sellUnit;
        }

        public int getPriceAmount() {
            return priceAmount;
        }

        public String getPriceUnit() {
            return priceUnit;
        }

        public int getSellAmount() {
            return sellAmount;
        }

        public String getSellUnit() {
            return sellUnit;
        }
    }
}

