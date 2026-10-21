package fr.koriamc.hdv.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Commande Bukkit enregistrée dynamiquement (nom et alias depuis config.json).
 */
final class MainCommand extends Command {

    private final CommandManager manager;

    MainCommand(String name, String description, List<String> aliases, CommandManager manager) {
        super(name, description, "/" + name, aliases);
        this.manager = manager;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        manager.dispatch(sender, label, args);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return manager.tabComplete(sender, alias, args);
    }
}
