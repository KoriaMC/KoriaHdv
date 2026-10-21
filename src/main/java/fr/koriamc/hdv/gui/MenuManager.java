package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.command.SubCommand;
import fr.koriamc.hdv.config.JsonFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Menu custom de l.hôtel des ventes (layout KoriaMC) et éditeur de configuration (menus coffre des admins).
 */
public final class MenuManager implements Listener {

    /** Layouts par défaut, copiés dans koriamc/guis/ s'ils n'y sont pas. */
    private static final String LAYOUT_RESOURCE = "gui/hdv.json";
    private static final String SELL_LAYOUT_RESOURCE = "gui/hdv_vente.json";
    private static final String BUY_LAYOUT_RESOURCE = "gui/hdv_achat.json";
    private static final String TOAST_LAYOUT_RESOURCE = "gui/hdv_notification.json";

    private final KoriaHdv plugin;
    private final ChatPrompt prompt;
    private final HdvMenu hdvMenu;
    private final SellMenu sellMenu;
    private final BuyMenu buyMenu;
    private final HdvToast toast;

    public MenuManager(KoriaHdv plugin) {
        this.plugin = plugin;
        this.prompt = new ChatPrompt(plugin);
        this.hdvMenu = new HdvMenu(plugin);
        this.sellMenu = new SellMenu(plugin);
        this.buyMenu = new BuyMenu(plugin);
        this.toast = new HdvToast(plugin);
    }

    /** Saisie de valeurs au clavier (éditeur de configuration). */
    public ChatPrompt prompt() {
        return prompt;
    }

    /** Section de menus.json (jamais nulle). */
    public ConfigurationSection section(String path) {
        ConfigurationSection s = plugin.menusConfig().getConfigurationSection(path);
        return s != null ? s : new MemoryConfiguration().createSection(path);
    }

    /** Ouvre un menu d'après son identifiant : principal (ventes), config, config:&lt;fichier&gt;. */
    public void open(Player player, String id) {
        String raw = id == null ? "principal" : id.trim();
        int colon = raw.indexOf(':');
        String key = (colon < 0 ? raw : raw.substring(0, colon)).toLowerCase(Locale.ROOT);
        String arg = colon < 0 ? null : raw.substring(colon + 1).trim();
        if (key.equals("principal") || key.equals("hdv") || key.equals("menu")) {
            hdvMenu.open(player, HdvMenu.Tab.ALL, 1);
        } else if (key.equals("vente") || key.equals("sell")) {
            if (!sellMenu.open(player)) {
                plugin.msg().send(player, "sell.client-required");
            }
        } else if (key.equals("config")) {
            SubCommand command = plugin.commands().get("config");
            if (command == null || !command.hasPermission(player)) {
                plugin.msg().send(player, "general.no-permission");
                return;
            }
            if (!openConfig(player, arg)) {
                plugin.msg().send(player, "config-editor.unknown-file", "file", arg);
            }
        } else {
            plugin.getLogger().warning("Menu inconnu : " + id);
        }
    }

    public HdvMenu hdvMenu() {
        return hdvMenu;
    }

    public SellMenu sellMenu() {
        return sellMenu;
    }

    public BuyMenu buyMenu() {
        return buyMenu;
    }

    public HdvToast toast() {
        return toast;
    }

    /**
     * Éditeur de configuration : liste des fichiers, ou directement un fichier.
     *
     * @return false si le fichier demandé n'existe pas
     */
    public boolean openConfig(Player player, String fileName) {
        if (fileName == null) {
            new ConfigFilesMenu(plugin, player).open();
            return true;
        }
        for (JsonFile file : plugin.configFiles()) {
            String name = file.getName();
            if (name.equalsIgnoreCase(fileName) || name.equalsIgnoreCase(fileName + ".json")) {
                new ConfigSectionMenu(plugin, player, file, Collections.<String>emptyList(), 0).open();
                return true;
            }
        }
        return false;
    }

    /**
     * Copie les layouts (menu, écran de vente) dans koriamc/guis/ s'ils n'existent pas encore (ils ne sont jamais
     * écrasés, sauf un ancien menu par défaut resté tel quel : les modifications faites dans l'éditeur de GUI sont gardées).
     *
     * @return true si un fichier vient d'être créé
     */
    public boolean installLayout() {
        if (!plugin.settings().installLayout || !plugin.gui().isAvailable()) {
            return false;
        }
        boolean menu = installLayout(plugin.settings().layout, LAYOUT_RESOURCE);
        boolean sell = installLayout(plugin.settings().sellLayout, SELL_LAYOUT_RESOURCE);
        boolean buy = installLayout(plugin.settings().buyLayout, BUY_LAYOUT_RESOURCE);
        boolean notification = installLayout(plugin.settings().toastLayout, TOAST_LAYOUT_RESOURCE);
        return menu || sell || buy || notification;
    }

    private boolean installLayout(String layout, String resource) {
        File target = new File(new File("koriamc", "guis"), layout + ".json");
        if (target.exists()) {
            String old = oldDefaultVersion(target);
            if (old == null) {
                return false;
            }
            File backup = new File(target.getParentFile(), target.getName() + "." + old + ".bak");
            if ((backup.exists() && !backup.delete()) || !target.renameTo(backup)) {
                return false;
            }
            plugin.getLogger().info("Ancien menu de l'hôtel des ventes remplacé par le nouveau (copie : " + backup.getName() + ").");
        }
        InputStream in = plugin.getResource(resource);
        if (in == null) {
            return false;
        }
        try {
            File folder = target.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("dossier " + folder.getPath() + " impossible à créer");
            }
            Files.copy(in, target.toPath());
            plugin.getLogger().info("Menu custom installé : " + target.getPath());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible d'installer le menu " + target.getPath(), e);
            return false;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // rien à faire
            }
        }
    }

    /** Premier menu par défaut du plugin (avant le thème tropical), jamais modifié dans l'éditeur de GUI. */
    /** Version d'un ancien menu par défaut non modifié à remplacer (v1, v2), null sinon. */
    private static String oldDefaultVersion(File file) {
        try {
            String text = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            if (text.contains("\"note\": \"Hôtel des ventes (plugin KoriaHdv)")) {
                return "v1";
            }
            if (text.contains("\"note\": \"HDV tropical, écran de vente (plugin KoriaHdv, v1)")) {
                return "v1";
            }
            for (String version : new String[] {"v2", "v3", "v4", "v5"}) {
                if (text.contains("\"note\": \"HDV tropical (plugin KoriaHdv, " + version + ")")) {
                    return version;
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize() && event.getWhoClicked() instanceof Player) {
            ((Menu) top.getHolder()).handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
