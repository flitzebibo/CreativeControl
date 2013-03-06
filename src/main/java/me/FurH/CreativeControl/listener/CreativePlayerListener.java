/*
 * Copyright (C) 2011-2012 FurmigaHumana.  All rights reserved.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation,  version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package me.FurH.CreativeControl.listener;

import com.sk89q.worldedit.bukkit.selections.Selection;
import java.util.ArrayList;
import me.FurH.Core.cache.CoreLRUCache;
import me.FurH.Core.location.LocationUtils;
import me.FurH.Core.util.Communicator;
import me.FurH.CreativeControl.CreativeControl;
import me.FurH.CreativeControl.configuration.CreativeMainConfig;
import me.FurH.CreativeControl.configuration.CreativeMessages;
import me.FurH.CreativeControl.configuration.CreativeWorldNodes;
import me.FurH.CreativeControl.data.CreativePlayerData;
import me.FurH.CreativeControl.data.friend.CreativePlayerFriends;
import me.FurH.CreativeControl.integration.worldedit.CreativeWorldEditHook;
import me.FurH.CreativeControl.manager.CreativeBlockData;
import me.FurH.CreativeControl.manager.CreativeBlockManager;
import me.FurH.CreativeControl.region.CreativeRegion;
import me.FurH.CreativeControl.region.CreativeRegion.CreativeMode;
import me.FurH.CreativeControl.util.CreativeUtil;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author FurmigaHumana
 */
public class CreativePlayerListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.isCancelled()) { return; }
                
        Player player = e.getPlayer();
        GameMode newgm = e.getNewGameMode();
        GameMode oldgm = player.getGameMode();

        CreativeMainConfig    config   = CreativeControl.getMainConfig();
        CreativeControl       plugin   = CreativeControl.getPlugin();
        if (config.data_inventory) {
            if (!plugin.hasPerm(player, "Data.Status")) {
                InventoryView view = player.getOpenInventory();
                view.close();
                if (plugin.isLoggedIn(player)) {
                    CreativePlayerData    data     = CreativeControl.getPlayerData();
                    data.process(player, newgm, oldgm);
                } else {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        
        if (config.perm_enabled) {
            Permission permissions = CreativeControl.getPermissions();

            if (permissions != null) {
                if (newgm.equals(GameMode.CREATIVE)) {
                    for (String group : permissions.getPlayerGroups(player)) {
                        if (group.equalsIgnoreCase(config.perm_from)) {
                            permissions.playerAddGroup(player, config.perm_to);
                            break;
                        }
                    }
                } else {
                    if (permissions.playerInGroup(player, config.perm_to)) {
                        permissions.playerRemoveGroup(player, config.perm_to);
                    }
                }
            }
        }
    }
    
    /*
     * Player Command Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (e.isCancelled()) { return; }
                
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase();
        World world = p.getWorld();

        /*
         * Command Black List
         */
        CreativeControl       plugin   = CreativeControl.getPlugin();
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }
        
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            if (!plugin.hasPerm(p, "BlackList.Commands")) {
                for (String cmd : msg.split(" ")) {
                    if (config.black_cmds.contains(cmd)) {
                        Communicator          com      = plugin.getCommunicator();
                        CreativeMessages      messages = CreativeControl.getMessages();
                        com.msg(p, messages.player_cmdblacklist);
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
        
        if (config.block_worledit) {
            if (plugin.getWorldEdit() != null) {
                CreativeWorldEditHook weh      = CreativeControl.getWorldEditHook();
                Selection select = plugin.getWorldEdit().getSelection(p);
                if (msg.startsWith("//set")) {
                    weh.saveBlocks(select, p);
                } else
                if (msg.startsWith("//undo")) {
                    weh.delBlocks(select, p);
                } else
                if (msg.startsWith("//redo")) {
                    weh.saveBlocks(select, p);
                }
            }
        }
    }
    
    /*
     * Player Death Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent e) {

        Player p = e.getEntity();
        World world = p.getWorld();
        
        /*
         * Clear drops on creative death
         */
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }
        
        if ((p.getGameMode().equals(GameMode.CREATIVE)) && (config.prevent_drops)) {
            CreativeControl       plugin   = CreativeControl.getPlugin();
            if (!plugin.hasPerm(p, "Preventions.ClearDrops")) {
                e.getDrops().clear();
            }
        }
    }
    
    /*
     * Player Enchant Item Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEnchantItemEvent(EnchantItemEvent e) {
        if (e.isCancelled()) { return; }
                
        Player p = e.getEnchanter();
        World world = p.getWorld();

        /*
         * Prevent Creative Player Enchant Items
         */
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }
        
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            CreativeControl       plugin   = CreativeControl.getPlugin();
            if ((!plugin.hasPerm(p, "Preventions.Enchantments")) && (config.prevent_enchant)) {
                e.setCancelled(true);
            }
        }
    }
    
    /*
     * Inventory Close Event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) { return; }
                
        Player p = (Player)e.getPlayer();
        World world = p.getWorld();
        
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            CreativeControl       plugin   = CreativeControl.getPlugin();
            if (!plugin.hasPerm(p, "BlackList.Inventory")) {
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null) {
                        if (config.black_inventory.contains(item.getTypeId())) {
                            p.getInventory().remove(item);
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.isCancelled()) { return; }
        
        HumanEntity entity = e.getPlayer();
        if (!(entity instanceof Player)) { return; }
        
        Player p = (Player)entity;
        World world = p.getWorld();
        
        CreativeMessages     messages   = CreativeControl.getMessages();
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }

        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            CreativeControl       plugin   = CreativeControl.getPlugin();
            Communicator          com      = plugin.getCommunicator();

            if (config.prevent_invinteract) {
                if (!plugin.hasPerm(p, "Preventions.InventoryOpen")) {
                    com.msg(p, messages.player_cantdo);
                    p.closeInventory();
                    e.setCancelled(true);
                }
            }
        }
    }

    /*
     * Inventory Click Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.isCancelled()) { return; }
        if (!(e.getWhoClicked() instanceof Player)) { return; }
                
        Player p = (Player)e.getWhoClicked();
        World world = p.getWorld();

        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }
        
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            CreativeControl       plugin   = CreativeControl.getPlugin();
            if (config.prevent_invinteract) {
                if (!plugin.hasPerm(p, "Preventions.InventoryInteract")) {
                    int slot = e.getRawSlot();
                    if (e.getInventory().getType() == InventoryType.PLAYER) {
                        if (!((slot >= 36) && (slot <= 44))) {
                            e.setCancelled(true);
                        }
                    } else {
                        e.setCancelled(true);
                    }
                }
            }

            if (e.getInventory().getType() == InventoryType.PLAYER) {
                if (!plugin.hasPerm(p, "Preventions.StackLimit")) {
                    int stacklimit = config.prevent_stacklimit;
                    ItemStack current = e.getCurrentItem();
                    if (current != null) {
                        if (current.getAmount() > stacklimit) {
                            current.setAmount(stacklimit);
                        }
                    }

                    ItemStack cursor = e.getCursor();
                    if (cursor != null) {
                        if (cursor.getAmount() > stacklimit) {
                            cursor.setAmount(stacklimit);
                        }
                    }

                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null) {
                            if (item.getAmount() > stacklimit) {
                                item.setAmount(stacklimit);
                            }
                        }
                    }
                }

                if (!plugin.hasPerm(p, "BlackList.Inventory")) {
                    ItemStack current = e.getCurrentItem();
                    if (current != null) {
                        if (config.black_inventory.contains(current.getTypeId())) {
                            p.getInventory().remove(current);
                            e.setCancelled(true);
                        }
                    }

                    ItemStack cursor = e.getCursor();
                    if (cursor != null) {
                        if (config.black_inventory.contains(cursor.getTypeId())) {
                            p.getInventory().remove(cursor);
                            e.setCancelled(true);
                        }
                    }

                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null) {
                            if (config.black_inventory.contains(item.getTypeId())) {
                                p.getInventory().remove(item);
                            }
                        }
                    }
                }
            }
        }
    }
 
    /*
     * Player Kick Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerKick(PlayerKickEvent e) {
        cleanup(e.getPlayer());
    }
    
    /*
     * Player Quit Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (e.isCancelled()) { return; }

        Player p = e.getPlayer();
        World world = p.getWorld();
        Location loc = p.getLocation();

        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);

        if (config.world_exclude) { return; }

        CreativeMessages     messages   = CreativeControl.getMessages();
        CreativeControl      plugin     = CreativeControl.getPlugin();
        Communicator         com        = plugin.getCommunicator();
        
        CreativeRegion region = CreativeControl.getRegioner().getRegion(loc);
        if (region != null) {
            World w = region.world;
            
            if (w != world) { 
                return; 
            }

            CreativeMode type = region.type;
            if (type == CreativeMode.CREATIVE) {
                if (!plugin.hasPerm(p, "Region.Keep.Survival")) {
                    if (!p.getGameMode().equals(GameMode.CREATIVE)) {
                        com.msg(p, messages.region_cwelcome);
                        p.setGameMode(GameMode.CREATIVE);
                    }
                }
            } else
            if (type == CreativeRegion.CreativeMode.SURVIVAL) {
                if (!p.getGameMode().equals(GameMode.SURVIVAL)) {
                    if (!plugin.hasPerm(p, "Region.Keep.Creative")) {
                        CreativeUtil.getFloor(p);
                        com.msg(p, messages.region_swelcome);
                        p.setGameMode(GameMode.SURVIVAL);
                    }
                }
            }
        }
    }
    
    /*
     * Player Join Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerJoin(PlayerJoinEvent e) {
                
        final Player p = e.getPlayer();
        final CreativeControl       plugin   = CreativeControl.getPlugin();
        
        if (CreativeControl.getMainConfig().data_teleport) {
            CreativeUtil.getFloor(p);
        }
        
        if (CreativeControl.getMainConfig().data_survival) {
            if (plugin.isLoggedIn(p)) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
        
        if (plugin.hasUpdate) {
            if (plugin.hasPerm(p, "Updater.Broadcast")) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        Communicator          com      = plugin.getCommunicator();
                        CreativeMessages      messages = CreativeControl.getMessages();
                        com.msg(p, messages.updater_new, plugin.newversion, plugin.currentversion);
                        com.msg(p, messages.updater_visit);
                    }
                }, 40L);
            }
        }
        
    }

    /*
     * Player Changed World Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent e) {

        Player p = e.getPlayer();
        World world = p.getWorld();

        /*
         * Gamemode Handler
         */
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        if (config.world_changegm) {
            CreativeControl       plugin   = CreativeControl.getPlugin();
            Communicator          com      = plugin.getCommunicator();
            CreativeMessages      messages = CreativeControl.getMessages();
            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                if ((!config.world_creative) && (!plugin.hasPerm(p, "World.Keep"))) {
                    com.msg(p, messages.blocks_nocreative);
                    p.setGameMode(GameMode.SURVIVAL);
                }
            } else 
            if (p.getGameMode().equals(GameMode.SURVIVAL)) {
                if ((config.world_creative) && (!plugin.hasPerm(p, "World.Keep"))) {
                    com.msg(p, messages.blocks_nosurvival);
                    p.setGameMode(GameMode.CREATIVE);
                }
            }
        }
    }

    /*
     * Player Pickup Item Module
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerPickupItem(PlayerPickupItemEvent e) {
        if (e.isCancelled()) { return; }
                
        Player p = e.getPlayer();
        World world = p.getWorld();

        /*
        * Item Pickup prevent
        */
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);

        if (config.world_exclude) { return; }

        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            if (config.prevent_pickup) {
                CreativeControl       plugin   = CreativeControl.getPlugin();
                if (!plugin.hasPerm(p, "Preventions.Pickup")) {
                    e.setCancelled(true);
                }
            }
        }
    }
    
    /*
     * Player Drops Item Module
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (e.isCancelled()) { return; }
                
        Player p = e.getPlayer();
        World world = p.getWorld();
        
        /*
        * Item drop prevent
        */
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        CreativeControl plugin = CreativeControl.getPlugin();
        
        if (config.world_exclude) { return; }
        
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            if (config.prevent_drop) {
                if (!plugin.hasPerm(p, "Preventions.ItemDrop")) {
                    String itemName = e.getItemDrop().getType().getName().toLowerCase().replace("_", " ");
                    Communicator          com      = plugin.getCommunicator();
                    CreativeMessages      messages = CreativeControl.getMessages();
                    com.msg(p, messages.player_cantdrop, itemName);
                    e.getItemDrop().remove();
                }
            }
            if (!plugin.hasPerm(p, "BlackList.Inventory")) {
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null) {
                        if (config.black_inventory.contains(item.getTypeId())) {
                            p.getInventory().remove(item);
                        }
                    }
                }
            }
        }
    }
    
    /*
     * Chicken Egg Throw Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerEggThrowEvent(PlayerEggThrowEvent e) {

        Player p = e.getPlayer();
        World world = p.getWorld();
        
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        
        if (config.world_exclude) { return; }
        
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            if (config.prevent_eggs) {
                CreativeControl       plugin   = CreativeControl.getPlugin();
                if (!plugin.hasPerm(p, "Preventions.Eggs")) {
                    Communicator          com      = plugin.getCommunicator();
                    CreativeMessages      messages = CreativeControl.getMessages();
                    com.msg(p, messages.player_chicken);
                    e.setHatching(false);
                    e.setNumHatches((byte)0);
                }
            }
        }
    }
    
    /*
     * Player interact section
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent e) {
        
        Player p = e.getPlayer();
        Block i = e.getClickedBlock();
        World world = p.getWorld();
        String ItemName = p.getItemInHand().getType().toString().toLowerCase().replace("_", " ");
        
        CreativeMessages      messages = CreativeControl.getMessages();
        CreativeControl       plugin   = CreativeControl.getPlugin();
        Communicator          com      = plugin.getCommunicator();
        CreativeWorldNodes    config   = CreativeControl.getWorldNodes(world);
        CreativeMainConfig    main     = CreativeControl.getMainConfig();

        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            if (!plugin.hasPerm(p, "BlackList.Inventory")) {
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null) {
                        if (config.black_inventory.contains(item.getTypeId())) {
                            p.getInventory().remove(item);
                        }
                    }
                }
            }
            if (i != null) {
                if (i.getType() == Material.WALL_SIGN || i.getType() == Material.SIGN_POST) {
                    Sign sign = (Sign)i.getState();
                    if (CreativeUtil.isEconomySign(sign)) {
                        if (!plugin.hasPerm(p, "BlackList.EconomySigns")) {
                            com.msg(p, messages.player_cantdo);
                            e.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }

        if (main.selection_tool == p.getItemInHand().getTypeId()) {
            if (plugin.hasPerm(p, "Utily.Selection")) {
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    Location right = e.getClickedBlock().getLocation();
                    plugin.right.put(p, right);
                    com.msg(p, messages.sel_second, right.getBlockX(), right.getBlockY(), right.getBlockZ());
                    e.setCancelled(true);
                    return;
                } else
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    Location left = e.getClickedBlock().getLocation();
                    plugin.left.put(p, left);
                    com.msg(p, messages.sel_first, left.getBlockX(), left.getBlockY(), left.getBlockZ());
                    e.setCancelled(true);
                    return;
                }
            }
        }
        
        if (plugin.mods.containsKey(p.getName())) {
            int id = plugin.mods.get(p.getName());
            if (id == 0) {
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (plugin.hasPerm(p, "Utily.Tool.info")) {
                        info(p, i);
                        e.setCancelled(true);
                        return;
                    }
                } else
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    if (plugin.hasPerm(p, "Utily.Tool.add")) {
                        add(p, i);
                        e.setCancelled(true);
                        return;
                    }
                }
            } else 
            if (id == 1) {
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (plugin.hasPerm(p, "Utily.Tool.info")) {
                        info(p, i);
                        e.setCancelled(true);
                        return;
                    }
                } else
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    if (plugin.hasPerm(p, "Utily.Tool.del")) {
                        del(p, i);
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (config.world_exclude) { return; }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                if (config.black_interact.contains(i.getTypeId())) {
                    if (!plugin.hasPerm(p, "BlackList.ItemInteract."+i.getTypeId())) {
                        com.msg(p, messages.player_cantuse, i.getType().toString().toLowerCase().replace("_", " "));
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
        
        if ((e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_BLOCK) && (e.getMaterial() == Material.MINECART || e.getMaterial() == Material.BOAT)) {
            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                if (!CreativeEntityListener.waiting.contains(p)) {
                    CreativeEntityListener.waiting.add(p);
                }
            }
        }
        
        if ((e.getAction() == Action.RIGHT_CLICK_AIR) || (e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                if (e.getItem() != null) {
                    if (config.prevent_eggs) {
                        if ((p.getItemInHand().getType() == Material.MONSTER_EGG) || (p.getItemInHand().getType() == Material.MONSTER_EGGS)) {
                            if (!plugin.hasPerm(p, "Preventions.Eggs")) {
                                com.msg(p, messages.player_cantuse, ItemName);
                                e.setCancelled(true);
                                return;
                            }
                        }
                    }
                    
                    if (config.black_use.contains(e.getItem().getTypeId())) {
                        if (!plugin.hasPerm(p, "BlackList.ItemUse."+e.getItem().getTypeId())) {
                            com.msg(p, messages.player_cantuse, ItemName);
                            e.setCancelled(true);
                            return;
                        }
                    } 

                    if (config.prevent_potion) {
                        if (p.getItemInHand().getTypeId() == 373) {
                            if (!plugin.hasPerm(p, "Preventions.PotionSplash")) {
                                com.msg(p, messages.player_cantuse, ItemName);
                                e.setCancelled(true);
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Block info Module
     */
    /*
     * Print informations about the block
     */
    public void info(Player p, Block b) {
        if (!is(p, b)) { return; }

        CreativeWorldNodes nodes = CreativeControl.getWorldNodes(b.getWorld());
        
        CreativeBlockManager manager = CreativeControl.getManager();
        
        CoreLRUCache<String, CreativeBlockData> cache = manager.getCache();

        CreativeBlockData data1 = manager.getFullData(b.getLocation());        
        CreativeBlockData data2 = null;
        
        if (nodes.block_ownblock) {
            data2 = cache.get(LocationUtils.locationToString(b.getLocation()));
        }

        boolean insql = data1 != null;
        boolean incache = data2 != null;

        if (nodes.block_nodrop) {
            incache = cache.containsKey(LocationUtils.locationToString(b.getLocation()));
        }

        CreativeMessages messages = CreativeControl.getMessages();
        CreativeControl plugin = CreativeControl.getPlugin();
        Communicator com = plugin.getCommunicator();
        
        if (!insql && !incache) {
            com.msg(p, messages.blockinfo_notprotected);
            plugin.mods.remove(p.getName());
            return;
        }

        String owner = null;
        String allowed = null;
        int type = 0;
        String date = null;
        
        if (insql) {
            owner = data1.owner;
            allowed = new ArrayList<String>(data1.allowed).toString();
            type = data1.type;
            date = data1.date;
        }

        if (incache) {
            if (data2.owner != null) {
                owner = data2.owner;
            }
            
            if (data2.allowed != null) {
                allowed = new ArrayList<String>(data2.allowed).toString();
            }
            
            type = data2.type;
            date = Long.toString(System.currentTimeMillis());
        }

        Location loc = b.getLocation();
        com.msg(p, messages.blockinfo_owner, owner);
        com.msg(p, messages.blockinfo_location, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getBlock().getTypeId(), type);
        if (!"".equals(allowed) && allowed != null && !"null".equals(allowed) && !allowed.isEmpty() && !"[]".equals(allowed)) {
            com.msg(p, messages.blockinfo_allowed, allowed.replaceAll(" ,", " &a,&7").replaceAll("\\[", "").replaceAll("\\]", ""));
        }
        com.msg(p, messages.blockinfo_status, (incache ? messages.blockinfo_incache : ""), (insql ? messages.blockinfo_insql : messages.blockinfo_queue));
        com.msg(p, messages.blockinfo_date, CreativeUtil.getDate(Long.parseLong(date)));
        
        plugin.mods.remove(p.getName());
    }
    
    /*
     * Add a block to the database
     */
    public void add(Player p, Block b) {
        if (!is(p, b)) { return; }
        
        CreativeBlockManager manager = CreativeControl.getManager();
        CreativeMessages messages = CreativeControl.getMessages();
        CreativeControl plugin = CreativeControl.getPlugin();
        CreativeWorldNodes config = CreativeControl.getWorldNodes(b.getWorld());
        Communicator com = plugin.getCommunicator();

        if (config.block_ownblock) {
            CreativeBlockData data = manager.isprotected(b, false);
            if (data != null) {
                com.msg(p, messages.blockadd_already);
            } else {
                com.msg(p, messages.blockadd_protected);
                manager.protect(p, b);
            }
        } else
        if (config.block_nodrop) {
            if (manager.isprotected(b, false) != null) {
                com.msg(p, messages.blockadd_already);
            } else {
                com.msg(p, messages.blockadd_protected);
                manager.protect(p, b);
            }
        }

        plugin.mods.remove(p.getName());
    }
    
    /*
     * Remove a protection from the block
     */
    public void del(Player p, Block b) {
        if (!is(p, b)) { return; }
        
        CreativeBlockManager manager = CreativeControl.getManager();
        CreativeMessages messages = CreativeControl.getMessages();
        CreativeControl plugin = CreativeControl.getPlugin();
        CreativeWorldNodes config = CreativeControl.getWorldNodes(b.getWorld());
        Communicator com = plugin.getCommunicator();

        if (config.block_ownblock) {
            CreativeBlockData data = manager.isprotected(b, false);
            if (data != null) {
                if (!data.owner.equalsIgnoreCase(p.getName())) {
                    com.msg(p, messages.blocks_pertence, data.owner);
                } else {
                    com.msg(p, messages.blockdel_disprotected);
                    manager.unprotect(b);
                }
            } else {
                com.msg(p, messages.blockinfo_notprotected);
            }
        } else
        if (config.block_nodrop) {
            if (manager.isprotected(b, true) != null) {
                com.msg(p, messages.blockinfo_notprotected);
            } else {
                com.msg(p, messages.blockdel_disprotected);
                manager.unprotect(b);
            }
        }

        plugin.mods.remove(p.getName());
    }
    
    private boolean is(Player p, Block b) {
        CreativeBlockManager manager = CreativeControl.getManager();
        CreativeMessages messages = CreativeControl.getMessages();
        CreativeControl plugin = CreativeControl.getPlugin();
        CreativeWorldNodes config = CreativeControl.getWorldNodes(b.getWorld());
        
        Communicator com = plugin.getCommunicator();
        
        if (config.world_exclude) {
            com.msg(p, messages.blockinfo_world);
            plugin.mods.remove(p.getName());
            return false;
        }
        
        if (!manager.isprotectable(b.getWorld(), b.getTypeId())) {
            com.msg(p, messages.blockinfo_protectable);
            plugin.mods.remove(p.getName());
            return false;
        }
        
        return true;
    }

    private void cleanup(Player p) {
        CreativeControl plugin = CreativeControl.getPlugin();
        plugin.right.remove(p);
        plugin.left.remove(p);
        plugin.mods.remove(p.getName());
        CreativePlayerFriends friend = CreativeControl.getFriends();
        friend.uncache(p);
        CreativePlayerData data = CreativeControl.getPlayerData();
        data.clear(p.getName());
    }
}