package de.clientapi.earthroot;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ParticleEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class EarthRoot extends EarthAbility implements AddonAbility, Listener {

    private static final List<EarthRoot> instances = new ArrayList<>();

    private Permission perm;
    private double particleRadius;
    private long cooldown;
    private boolean isRooted;
    private long duration;
    private double radius;
    private boolean endedByDuration;

    private Map<Player, Block> affectedPlayers;

    public EarthRoot(Player player) {
        super(player);

        this.cooldown = ConfigManager.getConfig().getLong("ExtraAbilities.ClientAPI.Earth.EarthRoot.Cooldown", 5000);
        this.radius = ConfigManager.getConfig().getDouble("ExtraAbilities.ClientAPI.Earth.EarthRoot.Radius", 0.0);
        this.duration = ConfigManager.getConfig().getLong("ExtraAbilities.ClientAPI.Earth.EarthRoot.Duration", 5000);
        this.particleRadius = 0.5;
        this.isRooted = false;
        this.endedByDuration = false;

        if (!bPlayer.canBend(this)) {
            remove();
            return;
        }

        // Check if standing on a dirt_path or use the block below
        Block feetBlock = player.getLocation().getBlock();
        Block blockUnder;
        if (feetBlock.getType() == Material.DIRT_PATH) {
            blockUnder = feetBlock;
        } else {
            blockUnder = feetBlock.getRelative(0, -1, 0);
        }

        if (blockUnder == null || !isEarthbendable(blockUnder)) {
            remove();
            return;
        }

        this.isRooted = true;
        this.affectedPlayers = new HashMap<>();
        affectedPlayers.put(player, blockUnder);

        if (radius > 0) {
            Location center = player.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(player)) continue;
                Block pFeetBlock = p.getLocation().getBlock();
                Block pBlockUnder;
                if (pFeetBlock.getType() == Material.DIRT_PATH) {
                    pBlockUnder = pFeetBlock;
                } else {
                    pBlockUnder = pFeetBlock.getRelative(0, -1, 0);
                }

                if (pBlockUnder != null && isEarthbendable(pBlockUnder)
                        && p.getWorld().equals(center.getWorld())
                        && p.getLocation().distance(center) <= radius) {
                    affectedPlayers.put(p, pBlockUnder);
                }
            }
        }

        start();
        instances.add(this);
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(this, ProjectKorra.plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (isRooted) {
                    endedByDuration = true;
                    bPlayer.addCooldown(EarthRoot.this);
                    remove();
                }
            }
        }.runTaskLater(ProjectKorra.plugin, duration / 50);
    }

    @Override
    public void progress() {
        if (!isRooted || player == null || player.isDead() || !player.isOnline()) {
            remove();
            return;
        }

        Iterator<Map.Entry<Player, Block>> it = affectedPlayers.entrySet().iterator();
        boolean mainPlayerStillOnBlock = false;

        while (it.hasNext()) {
            Map.Entry<Player, Block> entry = it.next();
            Player p = entry.getKey();
            Block originalBlock = entry.getValue();

            if (!p.isOnline() || p.isDead()) {
                it.remove();
                continue;
            }

            p.setVelocity(new Vector(0, 0, 0));

            Block currentBlockUnder = p.getLocation().getBlock().getRelative(0, -1, 0);
            Block pFeetBlock = p.getLocation().getBlock();
            boolean isPath = (pFeetBlock.getType() == Material.DIRT_PATH);
            Block expectedBlock = isPath ? pFeetBlock : currentBlockUnder;

            if (!expectedBlock.equals(originalBlock)) {
                teleportPlayerToRootedBlock(p, originalBlock);
            }

            Location particleLocation = originalBlock.getLocation().add(0.5, 1.1, 0.5);
            ParticleEffect.BLOCK_CRACK.display(
                    particleLocation,
                    10,
                    particleRadius,
                    0.1,
                    particleRadius,
                    0.1,
                    Material.DIRT.createBlockData()
            );

            if (p.equals(player)) {
                mainPlayerStillOnBlock = true;
            }
        }

        if (!mainPlayerStillOnBlock && isRooted) {
            bPlayer.addCooldown(this);
            remove();
        }

        if (affectedPlayers.isEmpty()) {
            remove();
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isRooted) return;
        if (event.getEntity() instanceof Player damagedPlayer) {
            if (affectedPlayers.containsKey(damagedPlayer)) {
                event.setCancelled(false);
                damagedPlayer.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (!isRooted) return;

        BlockFace direction = event.getDirection();
        for (Map.Entry<Player, Block> entry : affectedPlayers.entrySet()) {
            Player p = entry.getKey();
            Block b = entry.getValue();

            if (event.getBlocks().contains(b)) {
                Location newBlockLocation = b.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ());
                Block newBlock = newBlockLocation.getBlock();
                affectedPlayers.put(p, newBlock);
            }
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (!isRooted) return;

        BlockFace direction = event.getDirection();
        for (Map.Entry<Player, Block> entry : affectedPlayers.entrySet()) {
            Player p = entry.getKey();
            Block b = entry.getValue();

            if (event.getBlocks().contains(b)) {
                Location newBlockLocation = b.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ());
                Block newBlock = newBlockLocation.getBlock();
                affectedPlayers.put(p, newBlock);
            }
        }
    }

    private void teleportPlayerToRootedBlock(Player p, Block block) {
        Location oldLoc = p.getLocation();
        double yOffset = 1.0;
        if (block.getType() == Material.DIRT_PATH) { // From previous logic
            yOffset = 1;
        }

        Location newLocation = block.getLocation().add(0.5, yOffset, 0.5);
        newLocation.setYaw(oldLoc.getYaw());
        newLocation.setPitch(oldLoc.getPitch());
        p.teleport(newLocation);
    }

    @Override
    public void remove() {
        isRooted = false;
        instances.remove(this);
        HandlerList.unregisterAll((Listener) this);
        super.remove();
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "EarthRoot";
    }

    @Override
    public Location getLocation() {
        return player != null ? player.getLocation() : null;
    }

    @Override
    public boolean isHarmlessAbility() {
        return true;
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public void load() {
        ConfigManager.getConfig().addDefault("ExtraAbilities.ClientAPI.Earth.EarthRoot.Cooldown", 5000);
        ConfigManager.getConfig().addDefault("ExtraAbilities.ClientAPI.Earth.EarthRoot.Radius", 0.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.ClientAPI.Earth.EarthRoot.Duration", 5000);
        ConfigManager.defaultConfig.save();

        perm = new Permission("bending.ability.EarthRoot");
        perm.setDefault(PermissionDefault.TRUE);
        ProjectKorra.plugin.getServer().getPluginManager().addPermission(perm);

        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(new EarthRootListener(), ProjectKorra.plugin);
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(ProjectKorra.plugin);
        ProjectKorra.plugin.getServer().getPluginManager().removePermission(perm);
    }

    @Override
    public String getAuthor() {
        return "ClientAPI";
    }

    @Override
    public String getDescription() {
        return "Makes you and any players in radius stick to Earthbendable blocks. Adjusted logic for dirt_path.";
    }

    @Override
    public String getInstructions() {
        return "Stand on an Earthbendable block and sneak.";
    }

    @Override
    public String getVersion() {
        return "1.0.1";
    }
}
