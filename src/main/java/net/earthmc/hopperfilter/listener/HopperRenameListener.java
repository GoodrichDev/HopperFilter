package net.earthmc.hopperfilter.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.earthmc.hopperfilter.HopperFilter;
import net.earthmc.hopperfilter.object.HopperRenameInteraction;
import net.earthmc.hopperfilter.util.PatternUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class HopperRenameListener implements Listener {

    private static final Map<Player, Hopper> typingInteractions = new HashMap<>();
    private static final Map<Player, HopperRenameInteraction> itemInteractions = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !(clickedBlock.getState() instanceof Hopper hopper) || !event.getAction().equals(Action.LEFT_CLICK_BLOCK)) return;

        final Player player = event.getPlayer();
        if (!player.isSneaking() || player.getGameMode().equals(GameMode.CREATIVE)) return;

        final ItemStack item = event.getItem();
        if (item == null) {
            initiateHopperRename(player, hopper);
        } else {
            handleItemInteraction(player, hopper, item);
        }
    }

    @EventHandler
    public void cancelTypingInteractionOnMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final Hopper hopper = typingInteractions.get(player);
        if (hopper == null) return;

        if (event.getTo().distanceSquared(hopper.getLocation()) > 25) { // 5 * 5
            typingInteractions.remove(player);
            playSound(player, hopper.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3F, 1.25F, 1.5F);
        }
    }

    @EventHandler
    public void cancelItemInteractionOnMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final HopperRenameInteraction hri = itemInteractions.get(player);
        if (hri == null) return;

        if (event.getTo().distanceSquared(hri.getHopper().getLocation()) > 25) { // 5 * 5
            itemInteractions.remove(player);
            playSound(player, hri.getHopper().getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3F, 1.25F, 1.5F);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        if (event.isSneaking()) return;

        final Player player = event.getPlayer();
        final HopperRenameInteraction hri = itemInteractions.remove(player);
        if (hri != null) {
            renameHopper(player, hri.getHopper(), String.join(",", hri.getItems()));
        }
    }

    @EventHandler
    public void onAsyncChat(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final Hopper hopper = typingInteractions.get(player);
        if (hopper == null) return;

        event.setCancelled(true);
        final String originalMessage = PatternUtil.serialiseComponent(event.originalMessage());
        renameHopper(player, hopper, originalMessage);
        typingInteractions.remove(player);
    }

    private void initiateHopperRename(final Player player, final Hopper hopper) {
        final BlockBreakEvent bbe = new BlockBreakEvent(hopper.getBlock(), player);
        if (!bbe.callEvent()) return;

        typingInteractions.put(player, hopper);
        playSound(player, hopper.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.1F, 0.55F, 1.25F);
    }

    private void handleItemInteraction(final Player player, final Hopper hopper, final ItemStack item) {
        final BlockBreakEvent bbe = new BlockBreakEvent(hopper.getBlock(), player);
        if (!bbe.callEvent()) return;

        String key = item.getType().getKey().getKey();
        HopperRenameInteraction hri = itemInteractions.get(player);
        if (hri == null) {
            List<String> items = new ArrayList<>(List.of(key));
            itemInteractions.put(player, new HopperRenameInteraction(hopper, items));
        } else {
            List<String> items = hri.getItems();
            if (items.contains(key)) return;
            items.add(key);
        }
        playSound(player, hopper.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.1F, 0.55F, 1.25F);
    }

    private void renameHopper(final Player player, final Hopper hopper, final String name) {
        if (hopper.getBlock().getType().equals(Material.AIR)) return;

        final Component component = name.equals("null") ? null : Component.text(name);
        hopper.customName(component);

        final HopperFilter instance = HopperFilter.getInstance();
        instance.getServer().getRegionScheduler().run(instance, hopper.getLocation(), task -> hopper.update());

        playSound(player, hopper.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.75F, 1.25F, 1.5F);
    }

    private void playSound(Player player, Location location, Sound sound, float volume, float minPitch, float maxPitch) {
        final Random random = new Random();
        player.playSound(location, sound, volume, random.nextFloat(minPitch, maxPitch));
    }
}