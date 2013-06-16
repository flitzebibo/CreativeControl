/*
 * Copyright (C) 2011-2013 FurmigaHumana.  All rights reserved.
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

import de.diddiz.LogBlock.Consumer;
import java.util.ArrayList;
import java.util.List;
import me.FurH.Core.blocks.BlockUtils;
import me.FurH.Core.util.Communicator;
import me.FurH.CreativeControl.CreativeControl;
import me.FurH.CreativeControl.blacklist.CreativeBlackList;
import me.FurH.CreativeControl.configuration.CreativeMainConfig;
import me.FurH.CreativeControl.configuration.CreativeMessages;
import me.FurH.CreativeControl.configuration.CreativeWorldNodes;
import me.FurH.CreativeControl.manager.CreativeBlockData;
import me.FurH.CreativeControl.manager.CreativeBlockManager;
import me.FurH.CreativeControl.stack.CreativeItemStack;
import me.botsko.prism.Prism;
import me.botsko.prism.actionlibs.ActionFactory;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PistonBaseMaterial;

/**
 *
 * @author FurmigaHumana
 */
public class CreativeBlockListener implements Listener {
    
    /*
     * Block Place Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) { return; }
        if (e.getPlayer() == null) { return; }
        
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        World world = p.getWorld();
        
        CreativeMessages        messages   = CreativeControl.getMessages();
        CreativeControl         plugin     = CreativeControl.getPlugin();
        CreativeWorldNodes      config     = CreativeControl.getWorldNodes(world);
        Communicator            com        = plugin.getCommunicator();
        CreativeBlackList       blacklist  = CreativeControl.getBlackList();

        /*
         * Excluded Worlds
         */
        if (config.world_exclude) {
            return;
        }

        /*
         * Gamemode Handler
         */
        CreativeMainConfig      main       = CreativeControl.getMainConfig();
        if (!main.events_move) {
            if (CreativePlayerListener.onPlayerWorldChange(p, false)) {
                e.setCancelled(true);
                return;
            }
        }
        
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            /*
             * Block Place BlackList
             */
            
            CreativeItemStack itemStack = new CreativeItemStack(b.getTypeId(), b.getData());

            if (!config.black_place.isEmpty() && (blacklist.isBlackListed(config.black_place, itemStack))) {
                if (!plugin.hasPerm(p, "BlackList.BlockPlace." + b.getTypeId())) {
                    com.msg(p, messages.blockplace_cantplace);
                    e.setCancelled(true);
                    return;
                }
            }
            
            /*
             * Anti Whiter Creation
             */
            if ((config.prevent_wither) && (!plugin.hasPerm(p, "Preventions.Wither"))) {
                if (e.getBlockPlaced().getType() == Material.SKULL) {
                    if ((world.getBlockAt(b.getX(), b.getY() - 1, b.getZ()).getType() == Material.SOUL_SAND) &&
                            (world.getBlockAt(b.getX(), b.getY() - 2, b.getZ()).getType() == Material.SOUL_SAND) &&
                            (world.getBlockAt(b.getX() + 1, b.getY() - 1, b.getZ()).getType() == Material.SOUL_SAND) &&
                            (world.getBlockAt(b.getX() - 1, b.getY() - 1, b.getZ()).getType() == Material.SOUL_SAND) ||
                            (world.getBlockAt(b.getX(), b.getY() - 1, b.getZ() - 1).getType() == Material.SOUL_SAND) &&
                            (world.getBlockAt(b.getX(), b.getY() - 1, b.getZ() + 1).getType() == Material.SOUL_SAND)) {
                        com.msg(p, messages.mainode_restricted);
                        e.setCancelled(true);
                        return;
                    }
                }
            }
            
            /*
             * Anti SnowGolem Creation
             */
            if ((config.prevent_snowgolem) && (!plugin.hasPerm(p, "Preventions.SnowGolem")) && 
                    ((b.getType() == Material.PUMPKIN) || (b.getType() == Material.JACK_O_LANTERN)) &&
                    (world.getBlockAt(b.getX(), b.getY() - 1, b.getZ()).getType() == Material.SNOW_BLOCK) &&
                    (world.getBlockAt(b.getX(), b.getY() - 2, b.getZ()).getType() == Material.SNOW_BLOCK)) {
                com.msg(p, messages.mainode_restricted);
                e.setCancelled(true);
                return;
            }
            
            /*
             * Anti IronGolem Creation
             */
            if ((config.prevent_irongolem) && (!plugin.hasPerm(p, "Preventions.IronGolem")) && 
                    ((b.getType() == Material.PUMPKIN) || (b.getType() == Material.JACK_O_LANTERN)) && 
                    (world.getBlockAt(b.getX(), b.getY() - 1, b.getZ()).getType() == Material.IRON_BLOCK) &&
                    (world.getBlockAt(b.getX(), b.getY() - 2, b.getZ()).getType() == Material.IRON_BLOCK) &&
                    (((world.getBlockAt(b.getX() + 1, b.getY() - 1, b.getZ()).getType() == Material.IRON_BLOCK) &&
                    (world.getBlockAt(b.getX() - 1, b.getY() - 1, b.getZ()).getType() == Material.IRON_BLOCK)) ||
                    ((world.getBlockAt(b.getX(), b.getY() - 1, b.getZ() + 1).getType() == Material.IRON_BLOCK) &&
                    (world.getBlockAt(b.getX(), b.getY() - 1, b.getZ() - 1).getType() == Material.IRON_BLOCK)))) {
                com.msg(p, messages.mainode_restricted);
                e.setCancelled(true);
                return;
            }
            
            /* piston fix */
            if (config.block_pistons) {
                BlockFace[] faces = new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
                for (BlockFace face : faces) {
                    Block relative = b.getRelative(face);

                    if (relative.getType() == Material.PISTON_BASE || relative.getType() == Material.PISTON_EXTENSION || relative.getType() == Material.PISTON_MOVING_PIECE || relative.getType() == Material.PISTON_STICKY_BASE) {

                        int data = b.getData() ^ 0x7;

                        BlockFace head = relative.getFace(relative.getRelative
                                ((data == 1 ? BlockFace.UP : (data == 2 ? BlockFace.EAST : (data == 3 ? BlockFace.WEST : (data == 4 ? BlockFace.NORTH : BlockFace.SOUTH))))));

                        Block front = relative.getRelative(head.getOppositeFace());
                        if (front.getLocation().equals(b.getLocation())) {
                            e.setCancelled(true); return;
                        }

                        break;
                    }
                }
            }
            /* piston fix */
        }

        CreativeBlockManager    manager    = CreativeControl.getManager();

        Block r = e.getBlockReplacedState().getBlock();
        Block ba = e.getBlockAgainst();

        if (config.block_nodrop) {
            if (config.misc_liquid) {
                if (r.getType() != Material.AIR) {
                    manager.unprotect(r);
                }
            } 
            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                if (!plugin.hasPerm(p, "NoDrop.DontSave")) {
                    manager.protect(p, b);
                }
            }
        } else
        if (config.block_ownblock) {
            if (config.misc_liquid) {
                if (r.getType() != Material.AIR) {
                    CreativeBlockData data = manager.isprotected(r, true);
                    if (data != null) {
                        if (manager.isAllowed(p, data)) {
                            manager.unprotect(b);
                        } else {
                            com.msg(p, messages.blockmanager_belongs, data.owner);
                            e.setCancelled(true);
                            return;
                        }
                    }
                }
            }

            if (config.block_against) {
                CreativeBlockData data = manager.isprotected(ba, true);
                if (data != null) {
                    if (!manager.isAllowed(p, data)) {
                        com.msg(p, messages.blockmanager_belongs, data.owner);
                        e.setCancelled(true);
                        return;
                    }
                }
            }

            if (p.getGameMode().equals(GameMode.CREATIVE)) {
                if (!plugin.hasPerm(p, "OwnBlock.DontSave")) {
                    manager.protect(p, b);
                }
            }
        }
    }

    
    /*
     * Block Break Module
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) { return; }
        if (e.getPlayer() == null) { return; }
        
        Player p = e.getPlayer();
        Block b = e.getBlock();
        World world = b.getWorld();

        CreativeControl         plugin     = CreativeControl.getPlugin();
        CreativeMessages        messages   = CreativeControl.getMessages();
        CreativeWorldNodes      config     = CreativeControl.getWorldNodes(world);
        CreativeBlockManager    manager    = CreativeControl.getManager();
        Communicator            com        = plugin.getCommunicator();
        CreativeBlackList       blacklist  = CreativeControl.getBlackList();

        if (config.world_exclude) {
            return;
        }

        /*
         * Gamemode Handler
         */
        CreativeMainConfig      main       = CreativeControl.getMainConfig();
        if (!main.events_move) {
            if (CreativePlayerListener.onPlayerWorldChange(p, false)) {
                e.setCancelled(true);
                return;
            }
        }

        /*
         * Anti BedRock Breaking
         */
        if (p.getGameMode().equals(GameMode.CREATIVE)) {
            if ((config.prevent_bedrock) && (!plugin.hasPerm(p, "Preventions.BreakBedRock"))) {
                if (b.getType() == Material.BEDROCK) {
                    if (b.getY() < 1) {
                        com.msg(p, messages.blockbreak_survival);
                        e.setCancelled(true);
                        return;
                    }
                }
            }

            /*
             * Block Break BlackList
             */
            CreativeItemStack itemStack = new CreativeItemStack(b.getTypeId(), b.getData());
            
            if (!config.black_break.isEmpty() && blacklist.isBlackListed(config.black_break, itemStack)) {
                if (!plugin.hasPerm(p, "BlackList.BlockBreak." + b.getTypeId())) {
                    com.msg(p, messages.blockbreak_cantbreak);
                    e.setCancelled(true);
                    return;
                }
            }
        }
        
        List<Block> attached = new ArrayList<Block>();

        if (config.block_nodrop || config.block_ownblock) {
            
            if (config.block_attach) {

                if (!config.block_physics && isPhysics(b.getRelative(BlockFace.UP))) {
                    attached.add(b.getRelative(BlockFace.UP));
                }
                
                attached.addAll(BlockUtils.getAttachedBlock(b));
            }

            attached.add(b);
        }

        if (config.block_nodrop) {
            for (Block block : attached) {
                CreativeBlockData data = manager.isprotected(block, false);

                if (data != null) {
                    process(config, e, block, p);
                }
            }
        } else
        if (config.block_ownblock) {
            for (Block block : attached) {
                CreativeBlockData data = manager.isprotected(block, true);

                if (data != null) {
                    if (!manager.isAllowed(p, data)) {
                        com.msg(p, messages.blockmanager_belongs, data.owner);
                        e.setCancelled(true);
                        break;
                    } else {
                        process(config, e, block, p);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.isCancelled()) { return; }
        
        World world = e.getBlock().getWorld();

        CreativeBlockManager    manager     = CreativeControl.getManager();
        CreativeWorldNodes      config      = CreativeControl.getWorldNodes(world);

        if (config.world_exclude) {
            return;
        }
        
        if (config.block_pistons) {
            for (Block b : e.getBlocks()) {
                if (b.getType() != Material.AIR) {
                    if (manager.isprotected(b, true) != null) {
                        e.setCancelled(true);
                        break;
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.isCancelled()) { return; }

        Block b = e.getBlock();
        World world = b.getWorld();

        if (b.getType() == Material.AIR) {
            return;
        }

        if (!e.isSticky()) {
            return;
        }
        
        CreativeWorldNodes config = CreativeControl.getWorldNodes(world);
        if (config.world_exclude) {
            return;
        }

        if (config.block_pistons) {
            BlockFace direction = null;
            MaterialData data = b.getState().getData();

            if (data instanceof PistonBaseMaterial) {
                direction = ((PistonBaseMaterial) data).getFacing();
            }
            
            if (direction == null) { return; }
            Block moved = b.getRelative(direction, 2);
            CreativeBlockManager    manager    = CreativeControl.getManager();
            if (manager.isprotected(moved, true) != null) {
                e.setCancelled(true);
            }
        }
    }
    
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (e.isCancelled()) { return; }

        if (e.getEntity() instanceof FallingBlock) {

            CreativeWorldNodes config = CreativeControl.getWorldNodes(e.getBlock().getWorld());
           
            if (config.world_exclude) {
                return;
            }
            
            if (!config.block_physics) {
                return;
            }
            
            CreativeBlockManager    manager    = CreativeControl.getManager();
            
            if (manager.isprotected(e.getBlock(), true) != null) {
                e.setCancelled(true);
            }
        }
    }
    
    private boolean isPhysics(Block block) {
        return block.getType() == Material.SAND || block.getType() == Material.GRAVEL || block.getType() == Material.CACTUS || block.getType() == Material.SUGAR_CANE_BLOCK || block.getType() == Material.ANVIL;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (e.isCancelled()) { return; }

        CreativeBlockManager    manager     = CreativeControl.getManager();
        Block block = e.getBlock();
        CreativeWorldNodes config = CreativeControl.getWorldNodes(e.getBlock().getWorld());

        if (config.world_exclude) {
            return;
        }
        
        if (e.getBlock().getType() == Material.DRAGON_EGG) {
            
            if (config.block_nodrop || config.block_ownblock) {
                CreativeBlockData data = manager.isprotected(block, false);
                if (data != null) {
                    e.setCancelled(true);
                }
            }
            
            return;
        }
        
        if (e.getBlock().getType() != Material.WATER && e.getBlock().getType() != Material.STATIONARY_WATER) {
            return;
        }

        if (!isWaterAffected(e.getToBlock())) {
            return;
        }
        
        if (!config.block_water) {
            return;
        }

        if (config.block_nodrop) {
            CreativeBlockData data = manager.isprotected(block, false);
            if (data != null) {
                block.setType(Material.AIR);
            }
        } else
        if (config.block_ownblock) {
            CreativeBlockData data = manager.isprotected(block, true);
            if (data != null) {
                e.setCancelled(true);
            }
        }
    }

    private boolean isWaterAffected(Block block) {
        return block.getTypeId() == 6 
                || block.getTypeId() == 30
                || block.getTypeId() == 31
                || block.getTypeId() == 37
                || block.getTypeId() == 38
                || block.getTypeId() == 39
                || block.getTypeId() == 40
                || block.getTypeId() == 50
                || block.getTypeId() == 78
                || block.getTypeId() == 390
                || block.getTypeId() == 397
                || block.getTypeId() == 69
                || block.getTypeId() == 75
                || block.getTypeId() == 76
                || block.getTypeId() == 77
                || block.getTypeId() == 131
                || block.getTypeId() == 143
                || block.getTypeId() == 55
                || block.getTypeId() == 404
                || block.getTypeId() == 356
                || block.getTypeId() == 27
                || block.getTypeId() == 28
                || block.getTypeId() == 66
                || block.getTypeId() == 157;
    }

    private void log(Player p, Block b) {
        Consumer                consumer   = CreativeControl.getLogBlock();
        
        if (consumer != null) {
            consumer.queueBlockBreak(p.getName(), b.getState());
        }
        
        if (CreativeControl.getPrism()) {
            Prism.actionsRecorder.addToQueue(ActionFactory.create("block-break", b, p.getName()));
        }
        
        CoreProtectAPI protect = CreativeControl.getCoreProtect();
        if (protect != null) {
            protect.logRemoval(p.getName(), b.getLocation(), b.getTypeId(), b.getData());
        }
    }

    private void process(CreativeWorldNodes config, BlockBreakEvent e, Block b, Player p) {
        if (!e.isCancelled()) {
            CreativeMessages        messages   = CreativeControl.getMessages();
            CreativeBlockManager    manager    = CreativeControl.getManager();
            Communicator            com        = CreativeControl.plugin.getCommunicator();
            
            if (config.block_creative) {
                if (!p.getGameMode().equals(GameMode.CREATIVE)) {
                    com.msg(p, messages.blockbreak_cantbreak);
                    e.setCancelled(true);
                    return;
                }
            }
            
            manager.unprotect(b);
            
            log(p, b);
            
            e.setExpToDrop(0);
            b.setType(Material.AIR);

            if (!p.getGameMode().equals(GameMode.CREATIVE)) {
                com.msg(p, messages.blockbreak_creativeblock);
            }
        }
    }
}
