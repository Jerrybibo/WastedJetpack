package com.jerrybibo.wastedjetpack;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Wasted Jetpacks
// Last Edit: May 15, 2020

public final class WastedJetpack extends JavaPlugin implements Listener {

    private final HashMap<Player, Boolean> sneakingPlayers = new HashMap<>();
    private final HashMap<Player, Boolean> repairingPlayers = new HashMap<>();
    // Jetpack item; preferably chainmail, iron, or gold chestplate
    private final Material JETPACK_ITEM = Material.CHAINMAIL_CHESTPLATE;

    // Parameters relevant to jetpack speed; modify to change behavior
    // Durability taken per update for jetpack usage
    private final int JETPACK_USE_SPEED = 1;
    // Durability added per update for jetpack repair
    private final int JETPACK_REPAIR_SPEED = 5;
    // Maximum jetpack y-velocity
    private final float JETPACK_MAX_Y_VELOCITY = (float) 1.5;
    // The speed at which the jetpack climbs in the y-direction
    private final float JETPACK_Y_VELOCITY = (float) 0.3;
    // Fastest speed one can be falling before taking damage from falling
    private final float JETPACK_Y_VELOCITY_DAMAGE_THRESHOLD = (float) -1.2;
    // General magnitude for flight speed; suggested to keep between 0.8 and 1.2
    private final float MAGNITUDE = 1;

    @Override
    // Startup logic
    public void onEnable() {
        System.out.println("Wasted Jetpacks by Jerrybibo — starting up");
        getServer().getPluginManager().registerEvents(this, this);
        // Initializes HashMap values for each online player; used for /reload
        for (Player player : Bukkit.getOnlinePlayers()) {
            sneakingPlayers.put(player, true);
            repairingPlayers.put(player, false);
        }
        // Create a runnable to propell anyone who is wearing the jetpack and sneaking
        new BukkitRunnable() {
            @Override
            public void run() {
                propellJetpackPlayers((float) 1);
            }
        }.runTaskTimer(this, 0, 2);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Initializes HashMap values for each joined player on first join
        Player player = e.getPlayer();
        sneakingPlayers.put(player, true);
        repairingPlayers.put(player, false);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        // Updates HashMap for player isSneaking check
        Player player = e.getPlayer();
        sneakingPlayers.put(player, player.isSneaking());
    }

    @EventHandler
    public void onPunch(PlayerInteractEvent e) {
        // Repairs the jetpack on click with the item in hand
        Player player = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_AIR) {
            ItemStack currentHeldItem = player.getInventory().getItemInMainHand();
            if (currentHeldItem.getType().equals(JETPACK_ITEM) &&
                    currentHeldItem.getDurability() > 0 &&
                    currentHeldItem.hasItemMeta() &&
                    currentHeldItem.getItemMeta().getDisplayName().equals(ChatColor.RESET +"Jetpack") &&
                    currentHeldItem.getItemMeta().hasLore()) {
                // If the player is currently not repairing something:
                if (!repairingPlayers.get(player)) {
                    // Update the HashMap to indicate that the player is repairing something
                    repairingPlayers.put(player, true);
                    // Create a new BukkitRunnable so that the player begins to repair bit by bit
                    new BukkitRunnable() {
                        final ItemStack jetpack = currentHeldItem;
                        @Override
                        public void run() {
                            // If the player stops holding the jetpack, stop repairing
                            if (!jetpack.getType().equals(JETPACK_ITEM)) {
                                repairingPlayers.put(player, false);
                                cancel();
                            }
                            // If the durability hits maximum, stop repairing, otherwise continue
                            if (jetpack.getDurability() > 0) {
                                jetpack.setDurability((short) (jetpack.getDurability() - JETPACK_REPAIR_SPEED));
                                player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, (float) 0.5, 1);
                            } else {
                                repairingPlayers.put(player, false);
                                cancel();
                            }
                        }
                    }.runTaskTimer(this, 0, 5);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Gives the player a usable jetpack
        if (cmd.getName().equals("jetpack")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                short durability = JETPACK_ITEM.getMaxDurability();
                if (args.length > 0) {
                    short durabilityArgument = Short.parseShort(args[0]);
                    if (!(0 <= durabilityArgument && durabilityArgument <= JETPACK_ITEM.getMaxDurability())) {
                        player.sendMessage(ChatColor.RED + "Invalid durability argument - durability must be between 0 and " + JETPACK_ITEM.getMaxDurability());
                        return true;
                    } else {
                        durability = (short) (durability - Short.parseShort(args[0]));
                    }
                }
                ItemStack jetpackItemStack = new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1);
                ItemMeta jetpackMeta = jetpackItemStack.getItemMeta();
                assert jetpackMeta != null;
                jetpackMeta.setDisplayName(ChatColor.RESET + "Jetpack");
                jetpackMeta.setLore(Arrays.asList(ChatColor.AQUA + "Sneak while equipped to fly!",
                        ChatColor.AQUA + "Left click while holding to repair."));
                jetpackItemStack.setItemMeta(jetpackMeta);
                jetpackItemStack.setDurability((short) (JETPACK_ITEM.getMaxDurability() - durability));
                player.getInventory().addItem(jetpackItemStack);
                player.sendMessage(ChatColor.BLUE + "Here's your jetpack!");
            } else {
                System.out.println("You must be a player to execute this command!");
            }
        }
        return true;
    }

    public void propellJetpackPlayers(float magnitude) {
        // For each player that is currently sneaking and wearing a non-0-durability chain chestplate:
        for (Map.Entry<Player, Boolean> isSneaking : sneakingPlayers.entrySet()) {
            if (!isSneaking.getValue()) {
                Player player = isSneaking.getKey();
                ItemStack chestplateItem = player.getInventory().getChestplate();
                if (chestplateItem != null &&
                        chestplateItem.getType().equals(JETPACK_ITEM) &&
                        chestplateItem.hasItemMeta() &&
                        chestplateItem.getItemMeta().getDisplayName().equals(ChatColor.RESET + "Jetpack") &&
                        chestplateItem.getItemMeta().hasLore()) {
                    if (chestplateItem.getDurability() < JETPACK_ITEM.getMaxDurability()) {
                        // Set the player velocity to allow them to fly, and reduce the chestplate durability
                        player.setVelocity(calculateVelocityVector(player, magnitude));
                        if (player.getVelocity().getY() > JETPACK_Y_VELOCITY_DAMAGE_THRESHOLD) {
                            player.setFallDistance(0);
                        }
                        chestplateItem.setDurability((short) (chestplateItem.getDurability() + JETPACK_USE_SPEED));
                        // Spawn particles and play some sounds to go with it
                        player.spawnParticle(Particle.CLOUD, player.getLocation(), 3,
                                0, 0, 0, 0);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SAND_BREAK, 1, 1);
                    } else {
                        // If there is not enough durability, disable flying
                        player.spawnParticle(Particle.SMOKE_LARGE, player.getLocation(), 3,
                                0, 0, 0, 0.1);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1, 1);
                    }
                }
            }
        }
    }

    public Vector calculateVelocityVector(Player player, float magnitude) {
        // Return the vector in the direction of the player's looking-at block, modified to be propelled upwards
        Vector playerAngle = player.getEyeLocation().getDirection();
        double playerYVelocity = player.getVelocity().getY();
        Vector velocity = new Vector(playerAngle.getX() * 2, playerYVelocity, playerAngle.getZ() * 2);
        double y_vel = velocity.getY();
        if (y_vel < JETPACK_MAX_Y_VELOCITY) {
            velocity.setY(y_vel + JETPACK_Y_VELOCITY);
        }
        return velocity.multiply(magnitude);
    }

    @Override
    // Shutdown logic
    public void onDisable() {
        System.out.println("Wasted Jetpacks by Jerrybibo — shutting down");
    }
}
