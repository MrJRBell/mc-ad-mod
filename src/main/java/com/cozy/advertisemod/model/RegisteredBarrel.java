package com.cozy.advertisemod.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a registered shopping district barrel in the database and memory.
 */
public class RegisteredBarrel {

    private final String id;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID ownerUuid;
    private final String ownerName;
    private String shopName;
    private int priceAmount;
    private String priceUnit;
    private int sellAmount;
    private String sellUnit;
    private final String createdAt;

    public RegisteredBarrel(String id, String world, int x, int y, int z,
                            UUID ownerUuid, String ownerName, String shopName,
                            int priceAmount, String priceUnit, int sellAmount,
                            String sellUnit, String createdAt) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.shopName = shopName;
        this.priceAmount = priceAmount;
        this.priceUnit = priceUnit;
        this.sellAmount = sellAmount;
        this.sellUnit = sellUnit;
        this.createdAt = createdAt;
    }

    /**
     * Helper to generate standard barrel ID format: "world_x_y_z"
     */
    public static String formatId(String world, int x, int y, int z) {
        return world + "_" + x + "_" + y + "_" + z;
    }

    public String getId() {
        return id;
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

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getShopName() {
        return shopName != null && !shopName.isBlank() ? shopName : "Cozy Shop";
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public int getPriceAmount() {
        return priceAmount;
    }

    public void setPriceAmount(int priceAmount) {
        this.priceAmount = priceAmount;
    }

    public String getPriceUnit() {
        return priceUnit;
    }

    public void setPriceUnit(String priceUnit) {
        this.priceUnit = priceUnit;
    }

    public int getSellAmount() {
        return sellAmount;
    }

    public void setSellAmount(int sellAmount) {
        this.sellAmount = sellAmount;
    }

    public String getSellUnit() {
        return sellUnit;
    }

    public void setSellUnit(String sellUnit) {
        this.sellUnit = sellUnit;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisteredBarrel that = (RegisteredBarrel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RegisteredBarrel{" +
                "id='" + id + '\'' +
                ", world='" + world + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", ownerName='" + ownerName + '\'' +
                ", shopName='" + shopName + '\'' +
                ", price=" + priceAmount + " " + priceUnit +
                ", sell=" + sellAmount + " " + sellUnit +
                '}';
    }
}

