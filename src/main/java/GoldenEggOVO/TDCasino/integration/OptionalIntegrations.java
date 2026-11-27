package GoldenEggOVO.TDCasino.integration;

import GoldenEggOVO.TDCasino.Core.economy.EconomyAccess;
import GoldenEggOVO.TDCasino.Core.economy.VaultEconomy;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.OptionalDouble;
import java.util.UUID;

public final class OptionalIntegrations {
    private EconomyAccess economy = new EconomyAccess() {
        public boolean available() { return false; }
        public OptionalDouble balance(UUID player) { return OptionalDouble.empty(); }
        public String format(double amount) { return java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString(); }
        public Transfer withdraw(UUID player, double amount) { return Transfer.REJECTED; }
        public Transfer deposit(UUID player, double amount) { return Transfer.REJECTED; }
    };

    public OptionalIntegrations(JavaPlugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) return;
        try { economy = VaultEconomy.connect(plugin); }
        catch (RuntimeException | LinkageError exception) { plugin.getLogger().warning("Cannot connect optional Vault API: " + exception.getClass().getSimpleName()); }
    }
    public EconomyAccess economy() { return economy; }
}
