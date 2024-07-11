package net.earthmc.hopperfilter.listener;

import net.earthmc.hopperfilter.util.PatternUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

public class InventoryActionListener implements Listener {

    private final Object lock = new Object();

    @EventHandler
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        synchronized (lock) {
            handleInventoryMoveItem(event);
        }
    }

    @EventHandler
    public void onInventoryPickupItem(final InventoryPickupItemEvent event) {
        synchronized (lock) {
            handleInventoryPickupItem(event);
        }
    }

    private void handleInventoryMoveItem(final InventoryMoveItemEvent event) {
        final Inventory destination = event.getDestination();
        if (!destination.getType().equals(InventoryType.HOPPER)) return;

        final ItemStack item = event.getItem();
        final InventoryHolder holder = destination.getHolder(false);
        final String hopperName = getHopperName(holder);

        if (!canItemPassHopper(hopperName, item)) {
            event.setCancelled(true);
            return;
        }

        if (!(holder instanceof Hopper destinationHopper)) return;
        if (hopperName != null) return;

        final Inventory source = event.getSource();
        if (shouldCancelDueToMoreSuitableHopper(source, destinationHopper, item)) {
            event.setCancelled(true);
        }
    }

    private void handleInventoryPickupItem(final InventoryPickupItemEvent event) {
        final Inventory inventory = event.getInventory();
        if (!inventory.getType().equals(InventoryType.HOPPER)) return;

        final InventoryHolder holder = inventory.getHolder(false);
        final String hopperName = getHopperName(holder);

        if (hopperName == null) return;

        if (!canItemPassHopper(hopperName, event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    private String getHopperName(InventoryHolder holder) {
        if (holder instanceof Hopper) {
            return PatternUtil.serialiseComponent(((Hopper) holder).customName());
        } else if (holder instanceof HopperMinecart) {
            return PatternUtil.serialiseComponent(((HopperMinecart) holder).customName());
        }
        return null;
    }

    private boolean shouldCancelDueToMoreSuitableHopper(final Inventory source, final Hopper destinationHopper, final ItemStack item) {
        if (!(source.getHolder(false) instanceof Hopper sourceHopper)) return false;

        final org.bukkit.block.data.type.Hopper initiatorHopperData = (org.bukkit.block.data.type.Hopper) sourceHopper.getBlockData();
        if (initiatorHopperData.getFacing().equals(BlockFace.DOWN)) return false;

        Block facingBlock = sourceHopper.getBlock().getRelative(initiatorHopperData.getFacing());
        if (!facingBlock.getType().equals(Material.HOPPER)) return false;

        BlockState facingBlockState = facingBlock.getState(false);
        if (!(facingBlockState instanceof Hopper)) return false;

        final Hopper facingHopper = (Hopper) facingBlockState;
        Hopper otherHopper = facingHopper.equals(destinationHopper)
                ? getBlockBelowAsHopper(sourceHopper)
                : facingHopper;

        if (otherHopper == null) return false;

        final String hopperName = PatternUtil.serialiseComponent(otherHopper.customName());
        return hopperName != null && canItemPassHopper(hopperName, item);
    }

    private Hopper getBlockBelowAsHopper(Hopper sourceHopper) {
        Block belowBlock = sourceHopper.getBlock().getRelative(BlockFace.DOWN);
        if (belowBlock.getType().equals(Material.HOPPER)) {
            BlockState belowBlockState = belowBlock.getState(false);
            if (belowBlockState instanceof Hopper) {
                return (Hopper) belowBlockState;
            }
        }
        return null;
    }

    private boolean canItemPassHopper(final String hopperName, final ItemStack item) {
        if (hopperName == null) return true;

        for (final String condition : hopperName.split(",")) {
            boolean matchesCondition = true;
            for (final String andString : condition.split("&")) {
                boolean matchesAnd = false;
                for (final String orString : andString.split("\\|")) {
                    if (canItemPassPattern(orString.toLowerCase().strip(), item)) {
                        matchesAnd = true;
                        break;
                    }
                }
                if (!matchesAnd) {
                    matchesCondition = false;
                    break;
                }
            }
            if (matchesCondition) {
                return true;
            }
        }
        return false;
    }

    private boolean canItemPassPattern(final String pattern, final ItemStack item) {
        final String itemName = item.getType().getKey().getKey();
        if (pattern.equals(itemName)) return true;

        final char prefix = pattern.charAt(0);
        final String string = pattern.substring(1);
        switch (prefix) {
            case '*': return itemName.contains(string);
            case '^': return itemName.startsWith(string);
            case '$': return itemName.endsWith(string);
            case '#': return itemHasTag(string, item);
            case '~': return itemHasPotionEffect(string, item);
            case '+': return itemHasEnchantment(string, item);
            case '!': return !canItemPassPattern(string, item);
            default: return false;
        }
    }

    private boolean itemHasTag(String string, ItemStack item) {
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(string), Material.class);
        if (tag == null) tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(string), Material.class);
        return tag != null && tag.isTagged(item.getType());
    }

    private boolean itemHasPotionEffect(String string, ItemStack item) {
        final Material material = item.getType();
        if (!(material.equals(Material.POTION) || material.equals(Material.SPLASH_POTION) || material.equals(Material.LINGERING_POTION))) return false;

        final Pair<String, Integer> pair = PatternUtil.getStringIntegerPairFromString(string);
        final PotionEffectType type = (PotionEffectType) PatternUtil.getKeyedFromString(pair.getLeft(), Registry.POTION_EFFECT_TYPE);
        final Integer userLevel = pair.getRight();

        final PotionMeta meta = (PotionMeta) item.getItemMeta();
        final List<PotionEffect> effects = meta.getBasePotionType().getPotionEffects();
        if (userLevel == null) {
            for (PotionEffect effect : effects) {
                if (effect.getType().equals(type)) return true;
            }
        } else {
            for (PotionEffect effect : effects) {
                if (effect.getType().equals(type) && effect.getAmplifier() + 1 == userLevel) return true;
            }
        }
        return false;
    }

    private boolean itemHasEnchantment(String string, ItemStack item) {
        Map<Enchantment, Integer> enchantments;
        if (item.getType().equals(Material.ENCHANTED_BOOK)) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            enchantments = meta.getStoredEnchants();
        } else {
            enchantments = item.getEnchantments();
        }

        final Pair<String, Integer> pair = PatternUtil.getStringIntegerPairFromString(string);
        final Enchantment enchantment = (Enchantment) PatternUtil.getKeyedFromString(pair.getLeft(), Registry.ENCHANTMENT);
        if (enchantment == null) return false;

        final Integer userLevel = pair.getRight();
        final Integer enchantmentLevel = enchantments.get(enchantment);
        return userLevel == null ? enchantmentLevel != null : enchantmentLevel != null && enchantmentLevel.equals(userLevel);
    }
}
