package fr.koriamc.hdv.hook;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pont vers les GUI custom du serveur KoriaMC (fr.koriamc.gui.KoriaGuiAPI), par réflexion :
 * le plugin fonctionne aussi sur un Spigot classique (menu remplacé par un résumé dans le chat).
 */
public final class GuiHook {

    private final Logger logger;
    private boolean available;
    private Method isKoriaClient;
    private Method openGui;
    private Method setVariable;
    private Method setList;
    private Method pushClientActions;
    private Method notify;
    private Method managerGet;
    private Method managerReload;
    private Method getPlayerVar;
    private Method setPlayerVar;
    /** KoriaGuiAPI.getInventory (serveurs KoriaMC récents), ou null. */
    private Method getInventory;

    public GuiHook(Logger logger) {
        this.logger = logger;
        try {
            Class<?> api = Class.forName("fr.koriamc.gui.KoriaGuiAPI");
            isKoriaClient = api.getMethod("isKoriaClient", Player.class);
            openGui = api.getMethod("openGui", Player.class, String.class, boolean.class);
            setVariable = api.getMethod("setVariable", Player.class, String.class, String.class);
            setList = api.getMethod("setList", Player.class, String.class, List.class);
            pushClientActions = api.getMethod("pushClientActions", Player.class, String.class, String.class);
            notify = api.getMethod("notify", Player.class, String.class, int.class);
            Class<?> manager = Class.forName("fr.koriamc.gui.KoriaGuiManager");
            managerGet = manager.getMethod("get");
            managerReload = manager.getMethod("reload");
            getPlayerVar = api.getMethod("getPlayerVar", Player.class, String.class);
            setPlayerVar = api.getMethod("setPlayerVar", Player.class, String.class, String.class);
            try {
                getInventory = api.getMethod("getInventory", Player.class, String.class);
            } catch (NoSuchMethodException e) {
                getInventory = null;
            }
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    /** Serveur KoriaMC avec le système de GUI. */
    public boolean isAvailable() {
        return available;
    }

    /** Joueur sur le client KoriaMC (seul capable d'afficher le menu custom). */
    public boolean canDisplay(Player player) {
        if (!available) {
            return false;
        }
        try {
            return (Boolean) isKoriaClient.invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Affiche un layout en notification (toast) pendant durationMs. */
    public boolean notify(Player player, String layoutId, int durationMs) {
        return Boolean.TRUE.equals(call(notify, player, layoutId, durationMs));
    }

    public void setVariable(Player player, String key, String value) {
        call(setVariable, player, key, value);
    }

    public void setList(Player player, String key, List<Map<String, String>> items) {
        call(setList, player, key, items);
    }

    /** Ouvre le layout sans vérifier sa permission (le plugin l'a déjà fait). */
    public boolean open(Player player, String layoutId) {
        Object result = call(openGui, player, layoutId, Boolean.TRUE);
        return Boolean.TRUE.equals(result);
    }

    /** Actions client (page:, show:...) sur le layout affiché. */
    public void clientActions(Player player, String layoutId, String lines) {
        call(pushClientActions, player, layoutId, lines);
    }

    /** Variable persistante %pvar_key% du joueur (null si absente). */
    public String getPlayerVar(Player player, String key) {
        Object value = call(getPlayerVar, player, key);
        return value == null ? null : value.toString();
    }

    public void setPlayerVar(Player player, String key, String value) {
        call(setPlayerVar, player, key, value);
    }

    /** Le serveur peut-il afficher les vrais objets (éléments SLOT d'un inventaire virtuel) ? */
    public boolean hasInventories() {
        return available && getInventory != null;
    }

    /** Inventaire affiché par les éléments SLOT (pvirtual:nom...), ou null. */
    public Inventory getInventory(Player player, String spec) {
        if (getInventory == null) {
            return null;
        }
        Object value = call(getInventory, player, spec);
        return value instanceof Inventory ? (Inventory) value : null;
    }

    /** Relit les layouts de koriamc/guis (après installation du menu). */
    public void reloadLayouts() {
        if (!available) {
            return;
        }
        try {
            managerReload.invoke(managerGet.invoke(null));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Impossible de recharger les GUI KoriaMC", t);
        }
    }

    private Object call(Method method, Object... args) {
        if (!available) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Appel du GUI KoriaMC impossible (" + method.getName() + ")", t);
            return null;
        }
    }
}
