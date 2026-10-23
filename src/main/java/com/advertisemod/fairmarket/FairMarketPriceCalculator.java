package com.advertisemod.fairmarket;

import com.advertisemod.model.RegisteredBarrel;
import com.advertisemod.model.ShopListing;
import com.advertisemod.model.ShulkerContent;
import org.bukkit.Material;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Shared exact price comparison and web-feed floor adjustment logic. */
public final class FairMarketPriceCalculator {

    private static final BigInteger NINE = BigInteger.valueOf(9);

    private FairMarketPriceCalculator() {}

    public static List<Violation> findViolations(List<ShopListing> listings, int priceAmount, String priceUnit,
                                                 int sellAmount, String sellUnit,
                                                 FairMarketPriceManager priceManager) {
        return findViolations(listings, priceAmount, priceUnit, sellAmount, sellUnit, priceManager::getPrice, null);
    }

    public static List<Violation> findViolations(List<ShopListing> listings, int priceAmount, String priceUnit,
                                                 int sellAmount, String sellUnit,
                                                 Map<Material, FairMarketPrice> prices) {
        return findViolations(listings, priceAmount, priceUnit, sellAmount, sellUnit, prices::get, null);
    }

    public static List<Violation> findViolations(List<ShopListing> listings, int priceAmount, String priceUnit,
                                                 int sellAmount, String sellUnit,
                                                 FairMarketPriceManager priceManager, Set<String> onlyMaterials) {
        return findViolations(listings, priceAmount, priceUnit, sellAmount, sellUnit,
                priceManager::getPrice, onlyMaterials);
    }

    private static List<Violation> findViolations(List<ShopListing> listings, int priceAmount, String priceUnit,
                                                  int sellAmount, String sellUnit,
                                                  Function<Material, FairMarketPrice> priceLookup,
                                                  Set<String> onlyMaterials) {
        Map<String, Material> materials = new LinkedHashMap<>();
        for (ShopListing listing : listings) {
            addMaterial(materials, listing.getItemType());
            if (listing.isShulker()) {
                for (ShulkerContent content : listing.getShulkerContents()) {
                    addMaterial(materials, content.getItem());
                }
            }
        }

        List<Violation> violations = new ArrayList<>();
        for (Material material : materials.values()) {
            if (onlyMaterials != null && !onlyMaterials.contains(material.name())) {
                continue;
            }
            FairMarketPrice fairPrice = priceLookup.apply(material);
            if (fairPrice == null) {
                continue;
            }

            long advertisedItemCount = Math.multiplyExact((long) sellAmount,
                    itemCountForUnit(material, sellUnit));
            long fairMarketItemCount = itemCountForUnit(material, fairPrice.unit());
            BigInteger advertisedValue = BigInteger.valueOf(priceAmount)
                    .multiply(currencyFactor(priceUnit))
                    .multiply(BigInteger.valueOf(fairMarketItemCount));
            BigInteger fairMarketValue = BigInteger.valueOf(fairPrice.amount())
                    .multiply(currencyFactor(fairPrice.currency()))
                    .multiply(BigInteger.valueOf(advertisedItemCount));

            if (advertisedValue.compareTo(fairMarketValue) < 0) {
                violations.add(new Violation(material, fairPrice));
            }
        }
        return violations;
    }

    /** Captures material totals, including the contents of each shulker box. */
    public static Map<String, Long> materialQuantities(List<ShopListing> listings) {
        Map<String, Long> quantities = new LinkedHashMap<>();
        for (ShopListing listing : listings) {
            quantities.merge(listing.getItemType(), (long) listing.getQuantityAvailable(), Long::sum);
            if (listing.isShulker()) {
                for (ShulkerContent content : listing.getShulkerContents()) {
                    long count = (long) content.getCount() * listing.getQuantityAvailable();
                    quantities.merge(content.getItem(), count, Long::sum);
                }
            }
        }
        return quantities;
    }

    public static Set<String> addedMaterials(Map<String, Long> before, Map<String, Long> after) {
        Set<String> added = new LinkedHashSet<>();
        for (Map.Entry<String, Long> entry : after.entrySet()) {
            if (entry.getValue() > before.getOrDefault(entry.getKey(), 0L)) {
                added.add(entry.getKey());
            }
        }
        return added;
    }

    public static ShopListing withEffectivePrices(RegisteredBarrel barrel, ShopListing listing,
                                                   FairMarketPriceManager priceManager) {
        return withEffectivePrices(barrel, listing, priceManager::getPrice);
    }

    public static ShopListing withEffectivePrices(RegisteredBarrel barrel, ShopListing listing,
                                                   Map<Material, FairMarketPrice> prices) {
        return withEffectivePrices(barrel, listing, prices::get);
    }

    private static ShopListing withEffectivePrices(RegisteredBarrel barrel, ShopListing listing,
                                                    Function<Material, FairMarketPrice> priceLookup) {
        EffectivePricing pricing = effectivePricing(listing.getItemType(), barrel, priceLookup);
        List<ShulkerContent> contents = new ArrayList<>();
        for (ShulkerContent content : listing.getShulkerContents()) {
            contents.add(content.withEffectivePricing(
                    effectivePricing(content.getItem(), barrel, priceLookup)));
        }
        if (listing.isShulker() && !contents.isEmpty()) {
            long packagePrice = pricing.getPriceAmount();
            for (ShulkerContent content : contents) {
                packagePrice = Math.max(packagePrice, content.getEffectivePricing().getPriceAmount());
            }
            pricing = new EffectivePricing(packagePrice, barrel.getPriceUnit(), barrel.getSellAmount(),
                    barrel.getSellUnit(), packagePrice > barrel.getPriceAmount());
        }
        return listing.withEffectivePricing(pricing, contents);
    }

    private static EffectivePricing effectivePricing(String materialName, RegisteredBarrel barrel,
                                                       Function<Material, FairMarketPrice> priceLookup) {
        int originalAmount = barrel.getPriceAmount();
        FairMarketPrice floor = null;
        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            floor = priceLookup.apply(material);
        }
        if (floor == null) {
            return new EffectivePricing(originalAmount, barrel.getPriceUnit(), barrel.getSellAmount(),
                    barrel.getSellUnit(), false);
        }

        long advertCount = Math.multiplyExact((long) barrel.getSellAmount(),
                itemCountForUnit(material, barrel.getSellUnit()));
        long floorCount = itemCountForUnit(material, floor.unit());
        BigInteger floorNumerator = BigInteger.valueOf(floor.amount())
                .multiply(currencyFactor(floor.currency()))
                .multiply(BigInteger.valueOf(advertCount));
        BigInteger denominator = BigInteger.valueOf(floorCount)
                .multiply(currencyFactor(barrel.getPriceUnit()));
        BigInteger[] qr = floorNumerator.divideAndRemainder(denominator);
        BigInteger requiredAmount = qr[1].signum() == 0 ? qr[0] : qr[0].add(BigInteger.ONE);
        BigInteger effectiveAmount = requiredAmount.max(BigInteger.valueOf(originalAmount));
        boolean floorApplied = effectiveAmount.compareTo(BigInteger.valueOf(originalAmount)) > 0;
        return new EffectivePricing(effectiveAmount.longValueExact(), barrel.getPriceUnit(), barrel.getSellAmount(),
                barrel.getSellUnit(), floorApplied);
    }

    private static long itemCountForUnit(Material material, String unit) {
        long stackSize = material.getMaxStackSize();
        return switch (unit.toLowerCase()) {
            case "item" -> 1;
            case "stack" -> stackSize;
            case "shulker" -> 27L * stackSize;
            default -> throw new IllegalArgumentException("Unknown sale unit: " + unit);
        };
    }

    private static BigInteger currencyFactor(String currency) {
        return currency.equalsIgnoreCase("diamond_block") ? NINE : BigInteger.ONE;
    }

    private static void addMaterial(Map<String, Material> materials, String name) {
        Material material = Material.matchMaterial(name);
        if (material != null && !material.isAir()) {
            materials.putIfAbsent(material.name(), material);
        }
    }

    public record Violation(Material material, FairMarketPrice floor) {}
}
