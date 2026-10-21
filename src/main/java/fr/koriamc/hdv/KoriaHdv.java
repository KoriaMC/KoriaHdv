package fr.koriamc.hdv;

import fr.koriamc.hdv.command.CommandManager;
import fr.koriamc.hdv.config.ConfigDocs;
import fr.koriamc.hdv.config.JsonFile;
import fr.koriamc.hdv.config.Messages;
import fr.koriamc.hdv.config.Settings;
import fr.koriamc.hdv.gui.Menu;
import fr.koriamc.hdv.gui.MenuManager;
import fr.koriamc.hdv.hook.GuiHook;
import fr.koriamc.hdv.hook.VaultHook;
import fr.koriamc.hdv.listener.MarketListener;
import fr.koriamc.hdv.market.MarketManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KoriaHdv : hôtel des ventes pour KoriaMC (Spigot 1.8.8). Les joueurs vendent en dollars ou
 * en points boutique, avec une taxe (10 % par défaut) : en dollars elle est payée à la mise
 * en vente, en points boutique elle est retenue sur le montant reçu à la vente.
 */
public final class KoriaHdv extends JavaPlugin {

    private static KoriaHdv instance;

    // Fichiers
    private JsonFile configFile;
    private JsonFile messagesFile;
    private JsonFile menusFile;
    private Configuration config = new MemoryConfiguration();
    private Configuration messagesConfig = new MemoryConfiguration();
    private Configuration menusConfig = new MemoryConfiguration();

    // Réglages
    private final Settings settings = new Settings();
    private final Messages messages = new Messages();
    private final ConfigDocs configDocs = new ConfigDocs();

    // Services
    private MarketManager market;
    private MenuManager menus;
    private CommandManager commands;
    private GuiHook gui;
    private VaultHook vault;
    private boolean vaultChecked;

    public static KoriaHdv get() {
        return instance;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        configFile = new JsonFile(this, "config.json");
        messagesFile = new JsonFile(this, "messages.json");
        menusFile = new JsonFile(this, "menus.json", "editeur.fichiers");

        gui = new GuiHook(getLogger());
        market = new MarketManager(this);
        menus = new MenuManager(this);
        commands = new CommandManager(this);

        for (String problem : loadConfigs()) {
            getLogger().warning(problem);
        }
        market.load();
        configDocs.load(this);
        configDocs.writeReadme(this, configFiles());
        commands.load(config, messagesConfig);

        if (menus.installLayout()) {
            gui.reloadLayouts();
        }
        if (gui.isAvailable() && !gui.hasInventories()) {
            getLogger().warning("Serveur KoriaMC sans KoriaGuiAPI.getInventory : le menu affiche des icônes au lieu des vrais objets"
                    + " (mettez le serveur à jour).");
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new MarketListener(this), this);
        pm.registerEvents(menus, this);
        pm.registerEvents(menus.prompt(), this);
        commands.registerBukkitCommand();

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                market.expireListings();
            }
        }, 20L * 30, 20L * 60);

        getLogger().info("KoriaHdv activé en " + (System.currentTimeMillis() - start) + " ms ("
                + market.listings(null).size() + " vente(s) en cours).");
    }

    @Override
    public void onDisable() {
        if (market != null) {
            market.save();
        }
        if (commands != null) {
            commands.unregisterBukkitCommand();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        instance = null;
    }

    /** @return les problèmes à signaler (erreurs JSON) */
    private List<String> loadConfigs() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : configFiles()) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messagesConfig = messagesFile.toConfiguration();
        menusConfig = menusFile.toConfiguration();

        settings.load(config, getLogger());
        messages.load(messagesConfig, settings);
        return problems;
    }

    /**
     * Recharge la configuration (/hdv reload, éditeur). Les ventes restent en mémoire
     * (donnees.json n'est relu que s'il était illisible).
     *
     * @return les problèmes détectés, à afficher à l'auteur de la commande
     */
    public List<String> reload() {
        List<String> problems = loadConfigs();
        if (market != null && market.isReadOnly()) {
            market.load();
            if (market.isReadOnly()) {
                problems.add("donnees.json est toujours illisible.");
            }
        }
        if (commands != null) {
            commands.load(config, messagesConfig);
        }
        if (menus != null && menus.installLayout()) {
            gui.reloadLayouts();
        }
        return problems;
    }

    /** Économie Vault (dollars), ou null si Vault ou une économie manque. */
    public VaultHook economy() {
        if (vault == null && !vaultChecked && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vaultChecked = true;
            vault = VaultHook.create();
        }
        return vault;
    }

    // ============================================================ Accesseurs

    public Settings settings() {
        return settings;
    }

    public Messages msg() {
        return messages;
    }

    /** Explication des options (éditeur /hdv config et LISEZMOI.txt). */
    public ConfigDocs configDocs() {
        return configDocs;
    }

    public Configuration config() {
        return config;
    }

    public Configuration menusConfig() {
        return menusConfig;
    }

    /** Fichiers de configuration, dans l'ordre de l'éditeur. */
    public List<JsonFile> configFiles() {
        return Arrays.asList(configFile, messagesFile, menusFile);
    }

    public MarketManager market() {
        return market;
    }

    public MenuManager menus() {
        return menus;
    }

    public CommandManager commands() {
        return commands;
    }

    /** Pont vers les GUI custom KoriaMC. */
    public GuiHook gui() {
        return gui;
    }
}
