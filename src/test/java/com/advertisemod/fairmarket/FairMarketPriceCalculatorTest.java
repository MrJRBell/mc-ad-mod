package com.advertisemod.fairmarket;

import com.advertisemod.model.RegisteredBarrel;
import com.advertisemod.model.ShopListing;
import com.advertisemod.model.ShulkerContent;
import org.bukkit.Material;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FairMarketPriceCalculatorTest {

    @Test
    public void equalPerStackPriceIsAllowed() {
        Map<Material, FairMarketPrice> floors = Map.of(
                Material.STONE, new FairMarketPrice(1, "diamond", "stack"));

        List<FairMarketPriceCalculator.Violation> violations = FairMarketPriceCalculator.findViolations(
                List.of(listing("STONE")), 1, "diamond", 1, "stack", floors);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void rejectsOnlyItemsBelowTheFloorInMixedStock() {
        Map<Material, FairMarketPrice> floors = Map.of(
                Material.STONE, new FairMarketPrice(1, "diamond", "stack"),
                Material.NETHERITE_SWORD, new FairMarketPrice(2, "diamond", "stack"));
        List<ShopListing> stock = List.of(listing("STONE"), listing("NETHERITE_SWORD"));

        List<FairMarketPriceCalculator.Violation> violations = FairMarketPriceCalculator.findViolations(
                stock, 1, "diamond", 1, "item", floors);

        assertEquals(1, violations.size());
        assertEquals(Material.NETHERITE_SWORD, violations.get(0).material());
    }

    @Test
    public void diamondBlockConvertsToNineDiamondsForComparison() {
        Map<Material, FairMarketPrice> floors = Map.of(
                Material.STONE, new FairMarketPrice(9, "diamond", "stack"));

        List<FairMarketPriceCalculator.Violation> violations = FairMarketPriceCalculator.findViolations(
                List.of(listing("STONE")), 1, "diamond_block", 1, "stack", floors);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void usesMaterialStackSizesOfSixteenAndOne() {
        Map<Material, FairMarketPrice> floors = Map.of(
                Material.ENDER_PEARL, new FairMarketPrice(16, "diamond", "stack"),
                Material.ELYTRA, new FairMarketPrice(1, "diamond", "stack"));
        List<ShopListing> stock = List.of(listing("ENDER_PEARL"), listing("ELYTRA"));

        List<FairMarketPriceCalculator.Violation> violations = FairMarketPriceCalculator.findViolations(
                stock, 1, "diamond", 1, "item", floors);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void fullShulkerBasisUsesTwentySevenMaterialStacks() {
        Map<Material, FairMarketPrice> floors = Map.of(
                Material.STONE, new FairMarketPrice(27, "diamond", "shulker"));

        List<FairMarketPriceCalculator.Violation> violations = FairMarketPriceCalculator.findViolations(
                List.of(listing("STONE")), 1, "diamond", 1, "stack", floors);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void checksEveryMaterialNestedInsideShulkerBoxes() {
        ShopListing shulker = new ShopListing("BLUE_SHULKER_BOX", 1, true, null,
                List.of(new ShulkerContent("NETHERITE_SWORD", 1)));
        Map<Material, FairMarketPrice> floors = Map.of(
                Material.NETHERITE_SWORD, new FairMarketPrice(2, "diamond", "stack"));

        List<FairMarketPriceCalculator.Violation> violations = FairMarketPriceCalculator.findViolations(
                List.of(shulker), 1, "diamond", 1, "item", floors);

        assertEquals(1, violations.size());
        assertEquals(Material.NETHERITE_SWORD, violations.get(0).material());
    }

    @Test
    public void effectivePriceRoundsUpToTheAdvertisedCurrencyUnit() {
        RegisteredBarrel barrel = barrel(1, "diamond_block", 1, "item");
        ShopListing listing = FairMarketPriceCalculator.withEffectivePrices(barrel, listing("NETHERITE_SWORD"),
                Map.of(Material.NETHERITE_SWORD, new FairMarketPrice(10, "diamond", "stack")));

        assertEquals(2L, listing.getEffectivePricing().getPriceAmount());
        assertTrue(listing.getEffectivePricing().isFairMarketFloorApplied());
    }

    @Test
    public void effectivePriceLeavesOffersAboveFloorUnchanged() {
        RegisteredBarrel barrel = barrel(3, "diamond", 1, "item");
        ShopListing listing = FairMarketPriceCalculator.withEffectivePrices(barrel, listing("STONE"),
                Map.of(Material.STONE, new FairMarketPrice(2, "diamond", "stack")));

        assertEquals(3L, listing.getEffectivePricing().getPriceAmount());
        assertFalse(listing.getEffectivePricing().isFairMarketFloorApplied());
    }

    @Test
    public void addedMaterialsReportsOnlyIncreasedMaterialQuantities() {
        Map<String, Long> before = Map.of("STONE", 3L, "OAK_LOG", 4L);
        Map<String, Long> after = Map.of("STONE", 5L, "OAK_LOG", 4L, "ELYTRA", 1L);

        assertEquals(Set.of("STONE", "ELYTRA"), FairMarketPriceCalculator.addedMaterials(before, after));
    }

    private ShopListing listing(String material) {
        return new ShopListing(material, 1, false);
    }

    private RegisteredBarrel barrel(int amount, String currency, int sellAmount, String sellUnit) {
        return new RegisteredBarrel("world_0_64_0", "world", 0, 64, 0,
                UUID.randomUUID(), "Tester", "Test shop", amount, currency, sellAmount, sellUnit, null);
    }
}
