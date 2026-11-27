package GoldenEggOVO.TDCasino;

import GoldenEggOVO.TDCasino.Core.pack.CraftEngineRegistration;
import GoldenEggOVO.TDCasino.Core.pack.ResourcePackService;
import GoldenEggOVO.TDCasino.Core.player.GamePermissions;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import GoldenEggOVO.TDCasino.Core.platform.PlatformEvents;
import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDCasino.Core.render.PlayerViews;
import GoldenEggOVO.TDCasino.Core.render.ResourcePackState;
import GoldenEggOVO.TDCasino.Core.text.Language;
import GoldenEggOVO.TDCasino.command.CasinoCommand;
import GoldenEggOVO.TDCasino.integration.OptionalIntegrations;
import GoldenEggOVO.TDCasino.interaction.MachineSettingsTargets;
import GoldenEggOVO.TDCasino.menu.CasinoMenus;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.Objects;

public final class TDCasinoPlugin extends JavaPlugin {
    private ResourcePackService pack;
    private PlayerSessions playerSessions;
    private OptionalIntegrations integrations;
    private CasinoModule casino;
    private final PlatformScheduler scheduler = new PlatformScheduler(this);
    private PlayerViews playerViews;

    public PlatformScheduler scheduler() { return scheduler; }
    public PlayerViews playerViews() { return playerViews; }

    public ResourcePackService pack() { return pack; }
    public ResourcePackState resourcePackState() { return pack; }
    public PlayerSessions sessions() { return playerSessions; }
    public OptionalIntegrations integrations() { return integrations; }
    public CasinoModule casino() { return casino; }
    public CasinoMenus menus() { return casino == null ? null : casino.menus(); }
    public MachineSettingsTargets machineSettings() { return casino.targets(); }

    @Override public void onLoad() {
        var engine = getServer().getPluginManager().getPlugin("CraftEngine");
        if (engine == null) return;
        try {
            int added = CraftEngineRegistration.install(engine.getDataFolder().toPath());
            if (added > 0) getLogger().info("Updated " + added + " CraftEngine resource registration files.");
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Cannot install CraftEngine resource registrations: " + exception.getMessage());
        }
    }

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            Language.load(this);
            playerSessions = new PlayerSessions();
            integrations = new OptionalIntegrations(this);
            pack = new ResourcePackService(this);
            PlatformEvents.register(this, pack);
            pack.start();
            playerViews = new PlayerViews(this);
            playerViews.start();
            casino = new CasinoModule(this);
            var command = new CasinoCommand(this);
            var registered = Objects.requireNonNull(getCommand("3dcasino"));
            registered.setExecutor(command);
            registered.setTabCompleter(command);
            PlatformEvents.register(this, command);
            getLogger().info("3DCasino enabled.");
        } catch (Exception | LinkageError exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot initialize Casino; original records retained", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public synchronized void onDisable() {
        if (casino != null) cleanup("Casino", casino::close);
        if (playerViews != null) cleanup("player views", playerViews::close);
        if (playerSessions != null) cleanup("sessions", playerSessions::close);
        if (pack != null) cleanup("resource pack", pack::close);
    }

    private void cleanup(String module, Runnable action) {
        try { action.run(); }
        catch (RuntimeException exception) { getLogger().log(java.util.logging.Level.SEVERE, "Could not fully close " + module, exception); }
    }

    public boolean allowed(Player player) { return player.isOnline() && GamePermissions.use(player); }
    public void tell(Player player, Component message) { PlatformMessages.send(player, Language.component("chat.prefix", "message", message)); }
}
