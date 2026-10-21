package fr.koriamc.hdv.listener;

import fr.koriamc.hdv.KoriaHdv;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Connexion : gains en points en attente, ventes faites hors ligne, objets à récupérer.
 */
public final class MarketListener implements Listener {

    private final KoriaHdv plugin;

    public MarketListener(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // un peu plus tard : les messages de bienvenue des autres plugins passent d'abord
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.market().onJoin(player);
                }
            }
        }, 80L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.menus().hdvMenu().forget(event.getPlayer());
        plugin.menus().toast().forget(event.getPlayer());
    }
}
