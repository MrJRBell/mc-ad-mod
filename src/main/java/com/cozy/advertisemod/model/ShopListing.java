package com.cozy.advertisemod.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single item or container listing within a registered shop barrel.
 */
public class ShopListing {

    @SerializedName("item_type")
    private final String itemType;

    @SerializedName("quantity_available")
    private int quantityAvailable;

    @SerializedName("is_shulker")
    private final boolean isShulker;

    @SerializedName("display_name")
    private final String displayName;

    @SerializedName("shulker_contents")
    private final List<ShulkerContent> shulkerContents;

    public ShopListing(String itemType, int quantityAvailable, boolean isShulker,
                       String displayName, List<ShulkerContent> shulkerContents) {
        this.itemType = itemType;
        this.quantityAvailable = quantityAvailable;
        this.isShulker = isShulker;
        this.displayName = displayName;
        this.shulkerContents = shulkerContents != null ? shulkerContents : new ArrayList<>();
    }

    public ShopListing(String itemType, int quantityAvailable, boolean isShulker) {
        this(itemType, quantityAvailable, isShulker, null, new ArrayList<>());
    }

    public String getItemType() {
        return itemType;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public void addQuantity(int amount) {
        this.quantityAvailable += amount;
    }

    public boolean isShulker() {
        return isShulker;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<ShulkerContent> getShulkerContents() {
        return shulkerContents;
    }
}

