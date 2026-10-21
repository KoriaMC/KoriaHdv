package fr.koriamc.hdv.command;

import fr.koriamc.hdv.KoriaHdv;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Sous-commande de /hdv. Alias et permission viennent de config.json
 * (section "commandes"), usage et description de messages.json (help.commands).
 */
public abstract class SubCommand {

    protected final KoriaHdv plugin;
    private final String id;
    private final boolean playerOnly;
    private final int minArgs;
    private final boolean admin;

    private boolean enabled = true;
    private List<String> aliases = Collections.emptyList();
    private String permission = "";
    private String usage = "";
    private String description = "";

    protected SubCommand(KoriaHdv plugin, String id, boolean playerOnly, int minArgs, boolean admin) {
        this.plugin = plugin;
        this.id = id;
        this.playerOnly = playerOnly;
        this.minArgs = minArgs;
        this.admin = admin;
    }

    /**
     * @param s    section "commandes.&lt;id&gt;" de config.json (peut être nulle)
     * @param help section "help.commands.&lt;id&gt;" de messages.json (peut être nulle)
     */
    public void configure(ConfigurationSection s, ConfigurationSection help) {
        usage = help == null || help.getString("usage") == null ? "" : help.getString("usage");
        description = help == null || help.getString("description") == null ? "" : help.getString("description");
        if (s == null) {
            return;
        }
        enabled = !s.contains("actif") || s.getBoolean("actif");
        aliases = new ArrayList<String>();
        for (String alias : s.getStringList("alias")) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
        permission = s.getString("permission") == null ? "" : s.getString("permission");
    }

    public abstract void execute(CommandContext ctx);

    public List<String> complete(CommandContext ctx) {
        return Collections.emptyList();
    }

    /** Sous-commande interne (boutons des menus) : absente de l'aide et de l'autocomplétion. */
    public boolean isHidden() {
        return false;
    }

    public String getId() {
        return id;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public int getMinArgs() {
        return minArgs;
    }

    public boolean isAdmin() {
        return admin;
    }

    public String getPermission() {
        return permission;
    }

    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasPermission(CommandSender sender) {
        return permission.isEmpty() || sender.hasPermission(permission);
    }
}
