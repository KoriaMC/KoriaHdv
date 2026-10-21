package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saisie d'une valeur au clavier : le prochain message du joueur est capturé
 * (et n'apparaît pas dans le chat), puis traité sur le thread principal.
 */
public final class ChatPrompt implements Listener {

    /** Traitement de la réponse. */
    public interface Callback {
        void answer(String text);
    }

    private static final long TIMEOUT_MS = 120000L;

    private static final class Pending {
        final Callback callback;
        final Runnable onCancel;
        final long expiresAt;

        Pending(Callback callback, Runnable onCancel) {
            this.callback = callback;
            this.onCancel = onCancel;
            this.expiresAt = System.currentTimeMillis() + TIMEOUT_MS;
        }
    }

    private final KoriaHdv plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<UUID, Pending>();

    public ChatPrompt(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    /**
     * Ferme l'inventaire et attend la réponse du joueur (le mot d'annulation de
     * messages.json pour renoncer).
     *
     * @param onCancel exécuté en cas d'annulation (peut être null)
     */
    public void ask(Player player, Callback callback, Runnable onCancel) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Pending(callback, onCancel));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }
        event.setCancelled(true);
        if (System.currentTimeMillis() > p.expiresAt) {
            return;
        }
        final String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (text.equalsIgnoreCase(plugin.msg().text("config-editor.cancel-word"))) {
                    plugin.msg().send(player, "config-editor.cancelled");
                    if (p.onCancel != null) {
                        p.onCancel.run();
                    }
                    return;
                }
                p.callback.answer(text);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
