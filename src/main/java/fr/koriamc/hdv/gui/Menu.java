package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.util.Items;
import fr.koriamc.hdv.util.Sounds;
import fr.koriamc.hdv.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base des menus : titre, taille, fond, objets et effet de chaque clic viennent
 * de menus.json. Actions disponibles partout :
 * COMMANDE:, CONSOLE:, MENU:, MESSAGE:, FERMER, ACTUALISER, PAGE-SUIVANTE,
 * PAGE-PRECEDENTE ; chaque menu en ajoute (RECOMPENSES, RETOUR...).
 */
public abstract class Menu implements InventoryHolder {

    /** Clés de menus.json qui décrivent l'effet d'un clic. */
    private static final String[] CLICK_KEYS = {"action", "clic", "clic-gauche", "clic-droit", "clic-milieu", "shift-clic",
            "shift-clic-gauche", "shift-clic-droit", "touche-q", "ctrl-q"};

    /** Action exécutée lors d'un clic sur un emplacement. */
    public interface ClickHandler {
        void onClick(InventoryClickEvent event);
    }

    /** Action propre à un objet (ex. RECOMPENSES). @return true si elle est reconnue */
    public interface CustomAction {
        boolean run(String type, String value);
    }

    protected final KoriaHdv plugin;
    protected final Player player;
    private final Map<Integer, ClickHandler> handlers = new HashMap<Integer, ClickHandler>();
    private Inventory inventory;
    private String currentTitle;
    /** Page affichée et nombre de pages (menus paginés). */
    protected int page;
    protected int pages = 1;

    protected Menu(KoriaHdv plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Section de menus.json décrivant ce menu. */
    protected abstract ConfigurationSection section();

    /** Remplit l'inventaire (après le fond). */
    protected abstract void render();

    protected String titleTemplate() {
        return section().getString("titre", "");
    }

    protected int rows() {
        return section().getInt("lignes", 3);
    }

    /** Variables communes du menu. */
    protected Object[] vars() {
        return new Object[]{"player", player.getName(), "joueur", player.getName(),
                "label", plugin.commands().getLabel(), "page", page + 1, "pages", pages};
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Ouvre (ou redessine) le menu. */
    public void open() {
        int size = Math.max(1, Math.min(6, rows())) * 9;
        String title = Text.truncate(Text.color(Text.replace(titleTemplate(), vars())), 32);
        boolean reuse = inventory != null && inventory.getSize() == size && title.equals(currentTitle);
        if (reuse) {
            inventory.clear();
        } else {
            inventory = Bukkit.createInventory(this, size, title);
            currentTitle = title;
        }
        handlers.clear();
        fill(section().getConfigurationSection("fond"));
        render();
        if (!reuse || player.getOpenInventory().getTopInventory() != inventory) {
            player.openInventory(inventory);
        }
    }

    private void fill(ConfigurationSection fill) {
        if (fill == null) {
            return;
        }
        ItemStack item = Items.fromSection(fill);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item);
        }
    }

    protected void set(int slot, ItemStack item, ClickHandler handler) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        } else {
            handlers.remove(slot);
        }
    }

    /** Place un objet décrit dans la configuration (case, apparence, actions des clics). */
    protected void setConfigured(ConfigurationSection item, Object... kv) {
        setConfigured(item, null, kv);
    }

    protected void setConfigured(final ConfigurationSection item, final CustomAction custom, final Object... kv) {
        if (item == null || !item.contains("case")) {
            return;
        }
        set(item.getInt("case"), Items.fromSection(item, kv), clickHandler(item, custom, kv));
    }

    /** Clic sur un objet : actions de sa section selon le type de clic (null si aucune). */
    protected ClickHandler clickHandler(final ConfigurationSection item, final CustomAction custom, final Object... kv) {
        if (item == null || !hasActions(item)) {
            return null;
        }
        return new ClickHandler() {
            @Override
            public void onClick(InventoryClickEvent event) {
                runActions(actionsFor(item, event.getClick()), custom, kv);
            }
        };
    }

    public void handleClick(final InventoryClickEvent event) {
        final ClickHandler handler = handlers.get(event.getRawSlot());
        if (handler == null) {
            return;
        }
        Sounds.play(player, plugin.settings().sound("clic-menu"));
        // ouvrir / modifier un inventaire pendant un clic désynchronise le client en 1.8
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == Menu.this) {
                    handler.onClick(event);
                }
            }
        });
    }

    protected void runActions(List<String> actions, Object... kv) {
        runActions(actions, null, kv);
    }

    /** Exécute des actions dans l'ordre, au tick suivant. */
    protected void runActions(final List<String> actions, final CustomAction custom, final Object... kv) {
        if (actions.isEmpty()) {
            return;
        }
        final List<String> resolved = new ArrayList<String>();
        String label = plugin.commands().getLabel();
        for (String action : actions) {
            resolved.add(Text.replace(action, kv).replace("{label}", label));
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (String action : resolved) {
                    runAction(action.trim(), custom);
                }
            }
        });
    }

    private void runAction(String action, CustomAction custom) {
        int colon = action.indexOf(':');
        String type = (colon < 0 ? action : action.substring(0, colon)).toUpperCase(Locale.ROOT);
        String value = colon < 0 ? "" : action.substring(colon + 1).trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        switch (type) {
            case "CLOSE":
            case "FERMER":
                player.closeInventory();
                return;
            case "COMMAND":
            case "COMMANDE":
                player.performCommand(value);
                return;
            case "CONSOLE":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                return;
            case "MENU":
                plugin.menus().open(player, value);
                return;
            case "MESSAGE":
                plugin.msg().sendLine(player, plugin.msg().format(value));
                return;
            case "ACTUALISER":
                refreshLater();
                return;
            case "PAGE-SUIVANTE":
                if (page < pages - 1) {
                    page++;
                    open();
                }
                return;
            case "PAGE-PRECEDENTE":
                if (page > 0) {
                    page--;
                    open();
                }
                return;
            default:
                if ((custom != null && custom.run(type, value)) || menuAction(type, value)) {
                    return;
                }
                plugin.getLogger().warning("Action de menu inconnue : " + action);
        }
    }

    /** Actions propres au menu (hors objet). @return true si elle est reconnue */
    protected boolean menuAction(String type, String value) {
        return false;
    }

    /** Redessine le menu un peu plus tard (après l'exécution d'une commande). */
    protected void refreshLater() {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == Menu.this) {
                    open();
                }
            }
        }, 2L);
    }

    private static boolean hasActions(ConfigurationSection item) {
        for (String key : CLICK_KEYS) {
            if (item.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Actions d'un clic selon son type : shift-clic-gauche / shift-clic-droit /
     * shift-clic, touche-q / ctrl-q, clic-gauche / clic-droit, puis clic (ou action).
     */
    protected static List<String> actionsFor(ConfigurationSection section, ClickType click) {
        List<String> keys = new ArrayList<String>();
        if (click.isShiftClick()) {
            keys.add(click.isRightClick() ? "shift-clic-droit" : "shift-clic-gauche");
            keys.add("shift-clic");
        }
        if (click == ClickType.DROP) {
            keys.add("touche-q");
        } else if (click == ClickType.CONTROL_DROP) {
            keys.add("ctrl-q");
        } else if (click == ClickType.MIDDLE) {
            keys.add("clic-milieu");
        } else if (click.isRightClick()) {
            keys.add("clic-droit");
        } else if (click.isLeftClick()) {
            keys.add("clic-gauche");
        }
        keys.add("clic");
        keys.add("action");
        for (String key : keys) {
            if (section.contains(key)) {
                return Items.stringList(section, key);
            }
        }
        return new ArrayList<String>();
    }
}
