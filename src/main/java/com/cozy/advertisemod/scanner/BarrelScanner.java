package com.cozy.advertisemod.scanner;

import com.cozy.advertisemod.model.ShopListing;
import com.cozy.advertisemod.model.ShulkerContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep-scans barrel containers, enforcing currency exclusions,
 * private item exclusions, and recursive Shulker Box NBT/item meta inspection.
 */
public final class BarrelScanner {

    private BarrelScanner() {}

    /**
     * Scans an inventory and produces a list of distinct shop listings.
     */
    public static List<ShopListing> scan(Inventory inventory) {
        if (inventory == null) {
            return Collections.emptyList();
        }

        Map<String, ShopListing> regularItemsMap = new LinkedHashMap<>();
        Map<String, ShulkerBoxListingAccumulator> shulkerGroups = new LinkedHashMap<>();

        ItemStack[] contents = inventory.getContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            // Rule 2: Currency Exclusion (DIAMOND, DIAMOND_BLOCK)
            if (isCurrency(item.getType())) {
                continue;
            }

            // Rule 3: Display / Private Item Exclusion (* prefix)
            if (isPrivateItem(item)) {
                continue;
            }

            Material material = item.getType();

            if (isShulkerBox(material)) {
                // Rule 5: Shulker Box Deep Inspection
                List<ShulkerContent> shulkerContents = scanShulkerContents(item);
                String signature = buildShulkerSignature(material.name(), shulkerContents);

                shulkerGroups.compute(signature, (k, existing) -> {
                    if (existing == null) {
                        return new ShulkerBoxListingAccumulator(
                                material.name(),
                                item.getAmount(),
                                getCleanDisplayName(item),
                                shulkerContents
                        );
                    } else {
                        existing.incrementQuantity(item.getAmount());
                        return existing;
                    }
                });
            } else {
                // Rule 4: Regular items aggregated by material & custom name
                String displayName = getCleanDisplayName(item);
                String key = material.name() + (displayName != null ? "#" + displayName : "");

                regularItemsMap.compute(key, (k, existing) -> {
                    if (existing == null) {
                        return new ShopListing(
                                material.name(),
                                item.getAmount(),
                                false,
                                displayName,
                                new ArrayList<>()
                        );
                    } else {
                        existing.addQuantity(item.getAmount());
                        return existing;
                    }
                });
            }
        }

        List<ShopListing> finalResult = new ArrayList<>();

        // Add shulker listings
        for (ShulkerBoxListingAccumulator acc : shulkerGroups.values()) {
            finalResult.add(new ShopListing(
                    acc.materialName,
                    acc.quantity,
                    true,
                    acc.displayName,
                    acc.contents
            ));
        }

        // Add regular item listings
        finalResult.addAll(regularItemsMap.values());

        return finalResult;
    }

    /**
     * Inspects the internal block entity meta of a Shulker Box ItemStack.
     */
    public static List<ShulkerContent> scanShulkerContents(ItemStack shulkerItem) {
        if (shulkerItem == null || !shulkerItem.hasItemMeta()) {
            return Collections.emptyList();
        }

        ItemMeta meta = shulkerItem.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) {
            return Collections.emptyList();
        }

        if (!(bsm.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return Collections.emptyList();
        }

        Inventory shulkerInv = shulkerBox.getInventory();
        Map<String, ShulkerContent> internalMap = new LinkedHashMap<>();

        for (ItemStack inner : shulkerInv.getContents()) {
            if (inner == null || inner.getType().isAir()) {
                continue;
            }

            // Exclude currency inside shulkers
            if (isCurrency(inner.getType())) {
                continue;
            }

            // Exclude private items inside shulkers
            if (isPrivateItem(inner)) {
                continue;
            }

            String innerMat = inner.getType().name();
            String innerName = getCleanDisplayName(inner);
            List<String> lore = getCleanLore(inner);
            String innerKey = innerMat + (innerName != null ? "#" + innerName : "");

            internalMap.compute(innerKey, (k, existing) -> {
                if (existing == null) {
                    return new ShulkerContent(innerMat, inner.getAmount(), innerName, lore);
                } else {
                    existing.addCount(inner.getAmount());
                    return existing;
                }
            });
        }

        return new ArrayList<>(internalMap.values());
    }

    /**
     * Checks if material is diamond currency.
     */
    public static boolean isCurrency(Material material) {
        return material == Material.DIAMOND || material == Material.DIAMOND_BLOCK;
    }

    /**
     * Checks if material is any variant of Shulker Box.
     */
    public static boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    /**
     * Checks if item is marked private with asterisk (*).
     */
    public static boolean isPrivateItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        if (item.getType().name().startsWith("*")) {
            return true;
        }

        String displayName = getCleanDisplayName(item);
        return displayName != null && displayName.startsWith("*");
    }

    /**
     * Extracts plain text display name handling Paper Adventure Component and Legacy String.
     */
    public static String getCleanDisplayName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }

        try {
            Component comp = meta.displayName();
            if (comp != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(comp).trim();
                return plain.isEmpty() ? null : plain;
            }
        } catch (Throwable ignored) {
            // Fallback to legacy Bukkit getDisplayName
        }

        String legacy = meta.getDisplayName();
        if (legacy != null) {
            String stripped = ChatColor.stripColor(legacy).trim();
            return stripped.isEmpty() ? null : stripped;
        }

        return null;
    }

    /**
     * Extracts clean plain-text lore lines.
     */
    public static List<String> getCleanLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Collections.emptyList();
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return Collections.emptyList();
        }

        try {
            List<Component> loreList = meta.lore();
            if (loreList != null && !loreList.isEmpty()) {
                List<String> plainList = new ArrayList<>(loreList.size());
                for (Component comp : loreList) {
                    plainList.add(PlainTextComponentSerializer.plainText().serialize(comp));
                }
                return plainList;
            }
        } catch (Throwable ignored) {
            // Fallback
        }

        List<String> legacyLore = meta.getLore();
        if (legacyLore != null) {
            List<String> stripped = new ArrayList<>(legacyLore.size());
            for (String line : legacyLore) {
                stripped.add(ChatColor.stripColor(line));
            }
            return stripped;
        }

        return Collections.emptyList();
    }

    private static String buildShulkerSignature(String materialName, List<ShulkerContent> contents) {
        StringBuilder sb = new StringBuilder(materialName);
        for (ShulkerContent content : contents) {
            sb.append("|").append(content.getItem()).append(":").append(content.getCount());
            if (content.getDisplayName() != null) {
                sb.append("@").append(content.getDisplayName());
            }
        }
        return sb.toString();
    }

    private static class ShulkerBoxListingAccumulator {
        private final String materialName;
        private int quantity;
        private final String displayName;
        private final List<ShulkerContent> contents;

        public ShulkerBoxListingAccumulator(String materialName, int quantity, String displayName, List<ShulkerContent> contents) {
            this.materialName = materialName;
            this.quantity = quantity;
            this.displayName = displayName;
            this.contents = contents;
        }

        public void incrementQuantity(int amount) {
            this.quantity += amount;
        }
    }
}

