package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.hook.GuiHook;
import fr.koriamc.hdv.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notifications de l'hôtel des ventes en toast (layout KoriaMC koriamc/guis/hdv_notification.json, même style que le
 * toast d'entrée / sortie de territoire). Les toasts d'un joueur passent l'un après l'autre.
 *
 * Variables : %var_hdv_toast_categorie%, %var_hdv_toast_titre%, %var_hdv_toast_sous_titre%, %var_hdv_toast_message%,
 * %var_hdv_toast_icone%, %var_hdv_toast_mode_message% et %var_hdv_toast_&lt;vente|achat|info|erreur&gt;% (true / false).
 */
public final class HdvToast {

    /** Couleur de la tuile d'icône. */
    public enum Type {
        VENTE, ACHAT, INFO, ERREUR
    }

    private static final class Toast {
        final Type type;
        final String icon;
        final String category;
        final String title;
        final String subtitle;
        /** Texte long sur plusieurs lignes (à la place du titre et du sous-titre). */
        final String message;

        Toast(Type type, String icon, String category, String title, String subtitle, String message) {
            this.type = type;
            this.icon = icon;
            this.category = category;
            this.title = title;
            this.subtitle = subtitle;
            this.message = message;
        }
    }

    private final KoriaHdv plugin;
    private final Map<UUID, Deque<Toast>> queues = new HashMap<UUID, Deque<Toast>>();

    public HdvToast(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    /** Joueur capable d'afficher les toasts (client KoriaMC). */
    public boolean canShow(Player player) {
        return player != null && plugin.gui().canDisplay(player);
    }

    /** Toast titre + sous-titre (textes avec codes &amp;). */
    public void show(Player player, Type type, String icon, String category, String title, String subtitle) {
        enqueue(player, new Toast(type, icon, category, title, subtitle, ""));
    }

    /** Toast avec un texte long (erreur, information). */
    public void message(Player player, Type type, String icon, String category, String message) {
        enqueue(player, new Toast(type, icon, category, "", "", message));
    }

    public void forget(Player player) {
        queues.remove(player.getUniqueId());
    }

    private void enqueue(Player player, Toast toast) {
        Deque<Toast> queue = queues.get(player.getUniqueId());
        if (queue != null) {
            if (queue.size() < 8) {
                queue.add(toast);
            }
            return;
        }
        queues.put(player.getUniqueId(), new ArrayDeque<Toast>());
        display(player, toast);
    }

    private void display(final Player player, Toast toast) {
        GuiHook gui = plugin.gui();
        gui.setVariable(player, "hdv_toast_categorie", Text.color(toast.category));
        gui.setVariable(player, "hdv_toast_titre", Text.color(toast.title));
        gui.setVariable(player, "hdv_toast_sous_titre", Text.color(toast.subtitle));
        gui.setVariable(player, "hdv_toast_message", Text.color(toast.message));
        gui.setVariable(player, "hdv_toast_mode_message", String.valueOf(!toast.message.isEmpty()));
        gui.setVariable(player, "hdv_toast_icone", toast.icon == null || toast.icon.isEmpty() ? "minecraft:chest" : toast.icon);
        for (Type t : Type.values()) {
            gui.setVariable(player, "hdv_toast_" + t.name().toLowerCase(java.util.Locale.ROOT), String.valueOf(t == toast.type));
        }
        final int duration = plugin.settings().toastDurationMs;
        final String layout = plugin.settings().toastLayout;
        // un tick plus tard : les variables arrivent avant le toast
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.gui().notify(player, layout, duration);
                }
            }
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                next(player);
            }
        }, 1L + duration / 50L + 4L);
    }

    private void next(Player player) {
        Deque<Toast> queue = queues.get(player.getUniqueId());
        if (queue == null) {
            return;
        }
        Toast toast = queue.poll();
        if (toast == null || !player.isOnline()) {
            queues.remove(player.getUniqueId());
            return;
        }
        display(player, toast);
    }
}
