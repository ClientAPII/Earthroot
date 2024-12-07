package de.clientapi.earthroot;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class EarthRootListener implements Listener {

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

        if (bPlayer == null || !event.isSneaking()) {
            return;
        }

        EarthRoot ability = CoreAbility.getAbility(player, EarthRoot.class);
        if (ability == null) {
            new EarthRoot(player);
        }
    }

}
