package GoldenEggOVO.TDCasino;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.interaction.MachineSettingsTargets;
import GoldenEggOVO.TDCasino.machine.MachineManager;
import GoldenEggOVO.TDCasino.menu.CasinoMenus;
import GoldenEggOVO.TDCasino.economy.MachinePaymentLedger;
import GoldenEggOVO.TDCasino.Core.text.RuleViolation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import GoldenEggOVO.TDCasino.Core.platform.PlatformEvents;
import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;
import java.util.UUID;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CasinoModule implements Listener, AutoCloseable {
    private final TDCasinoPlugin plugin;
    private final MachinePaymentLedger payments;
    private final MachineSettingsTargets targets;
    private final MachineManager machines;
    private final CasinoMenus menus;
    private final PlatformScheduler.Task paymentRetryTask;

    public CasinoModule(TDCasinoPlugin plugin) throws IOException {
        this.plugin = plugin;
        payments = new MachinePaymentLedger(plugin.getDataFolder().toPath().resolve("casino/payments.json"),
                () -> plugin.integrations().economy(), this::economyEnabled,
                () -> stakeLimitCents("minimum", 1), () -> stakeLimitCents("maximum", 1000), plugin.getLogger()::warning, this::scheduleCredit);
        try { payments.recover(); }
        catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Casino payment recovery stopped; positive amounts blocked", exception);
        }
        menus = new CasinoMenus(plugin, this);
        targets = new MachineSettingsTargets(plugin);
        try { machines = new MachineManager(plugin); }
        catch (IOException | RuntimeException exception) {
            targets.close();
            menus.close();
            try { payments.close(); }
            catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
        PlatformEvents.register(plugin, this);
        paymentRetryTask = plugin.scheduler().repeat(this::retryPayments, 600, 600);
    }

    public MachinePaymentLedger payments() { return payments; }
    public CasinoMenus menus() { return menus; }
    public MachineManager machines() { return machines; }
    public MachineSettingsTargets targets() { return targets; }

    public boolean economyEnabled() { return plugin.getConfig().getBoolean("economy.enabled", false); }
    public boolean canConfigureStake() { return economyEnabled() && plugin.integrations().economy().available(); }

    public long stakeLimitCents(String name, double fallbackAmount) {
        double value = plugin.getConfig().getDouble("economy.stakes." + name, fallbackAmount);
        try {
            long cents = BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return cents >= 0 && cents <= 1_000_000_000L ? cents : -1;
        } catch (ArithmeticException | NumberFormatException exception) { return -1; }
    }

    public void validateStakeCents(long cents) {
        long minimumCents = stakeLimitCents("minimum", 1), maximumCents = stakeLimitCents("maximum", 1000);
        if (cents < 0 || cents > 1_000_000_000L || minimumCents < 0 || maximumCents < minimumCents || cents != 0 && (cents < minimumCents || cents > maximumCents))
            throw new RuleViolation("casino.error.amount-limits", "Amount outside configured limits",
                    "minimum", formatCents(minimumCents), "maximum", formatCents(maximumCents));
        if (cents == 0) return;
        if (!canConfigureStake()) throw new RuleViolation("error.economy.unavailable", "Economy unavailable");
        var economy = plugin.integrations().economy();
        int fractionDigits = economy.fractionalDigits();
        if (fractionDigits < 0) fractionDigits = 2;
        if (BigDecimal.valueOf(cents, 2).stripTrailingZeros().scale() > fractionDigits || !economy.supports(cents / 100.0))
            throw new RuleViolation("error.economy.amount", "Invalid amount precision");
    }

    public String formatCents(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }

    private void scheduleCredit(UUID id) {
        var scheduler = plugin.scheduler();
        if (!scheduler.folia()) {
            payments.retry(id);
            return;
        }
        // Disable runs outside ticking regions. The durable pending credit is retried on enable.
        if (!plugin.isEnabled()) return;
        var player = plugin.getServer().getPlayer(id);
        if (player != null) {
            scheduler.execute(player, () -> {
                if (plugin.getServer().getPlayer(id) == player) payments.retry(id);
            });
        } else scheduler.execute(() -> {
            if (plugin.getServer().getPlayer(id) == null) payments.retry(id);
            else scheduleCredit(id);
        });
    }

    private void retryPayments() {
        try { payments.retry(); }
        catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Casino payment retry stopped; inspect casino/payments.json", exception);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { menus.forget(event.getPlayer()); }

    @EventHandler
    public void world(PlayerChangedWorldEvent event) { menus.forget(event.getPlayer()); }

    @Override
    public void close() {
        paymentRetryTask.cancel();
        menus.close();
        machines.close();
        targets.close();
        try { payments.close(); }
        finally { HandlerList.unregisterAll(this); }
    }
}
