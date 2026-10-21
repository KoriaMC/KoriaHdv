package fr.koriamc.hdv.market;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.hook.VaultHook;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Dollars : économie du serveur via Vault.
 */
public final class MoneyWallet implements Wallet {

    private final KoriaHdv plugin;

    public MoneyWallet(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        return plugin.economy() != null;
    }

    @Override
    public double balance(Player player) {
        VaultHook economy = plugin.economy();
        return economy == null ? -1 : economy.getBalance(player);
    }

    @Override
    public boolean has(Player player, double amount) {
        VaultHook economy = plugin.economy();
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        VaultHook economy = plugin.economy();
        return economy != null && economy.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, String name, double amount) {
        VaultHook economy = plugin.economy();
        if (economy == null) {
            return false;
        }
        economy.deposit(player, amount);
        return true;
    }
}
