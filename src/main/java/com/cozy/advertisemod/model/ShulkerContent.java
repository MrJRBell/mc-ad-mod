package com.cozy.advertisemod.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Represents an item contained inside a deep-scanned Shulker Box.
 */
public class ShulkerContent {

    @SerializedName("item")
    private final String item;

    @SerializedName("count")
    private int count;

    @SerializedName("display_name")
    private final String displayName;

    @SerializedName("lore")
    private final List<String> lore;

    public ShulkerContent(String item, int count, String displayName, List<String> lore) {
        this.item = item;
        this.count = count;
        this.displayName = displayName;
        this.lore = lore;
    }

    public ShulkerContent(String item, int count) {
        this(item, count, null, null);
    }

    public String getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void addCount(int amount) {
        this.count += amount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }
}

