package GoldenEggOVO.TDCasino.game.crash;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;

public final class CrashMachine extends GameMachine<CrashRound> {
    private ItemDisplay rocket;
    private TextDisplay readout;
    private boolean wasActive;

    public CrashMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new CrashRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("crash_machine");
        button("start", "casino_button_play");
        button("cash", "casino_button_cashout");
        rocket = model("crash_rocket", rocketX(), rocketY(round.multiplier()), .57, 4);
        readout = text(.53, 2.75, .64, .75);
    }

    @Override
    protected boolean available(String action) {
        return action.equals("start")
                ? !round.active()
                : action.equals("cash") && round.active() && !round.cashed();
    }

    @Override
    protected void action(String action) {
        if (action.equals("start")) round.start(System.currentTimeMillis());
        else round.cash(System.currentTimeMillis());
        refresh();
    }

    @Override
    protected void refresh() {
        PlatformMessages.text(readout,
                Component.text(String.format(Locale.ROOT, "%.2f×", round.multiplier() / 100.0)));
        moveRocket();
        rocket.setGlowing(round.active());
        if (wasActive && !round.active()) {
            origin.getWorld()
                    .spawnParticle(Particle.SMOKE, rocket.getLocation(), 8, .12, .12, .08, .015);
        }
        wasActive = round.active();
    }

    @Override
    protected boolean feedbackFinished() { return round.finished() || round.cashed(); }

    @Override
    protected Long cashoutAmount() {
        return round.active() && !round.cashed() ? round.stake() * round.multiplier() / 100 : null;
    }

    private double rocketX() {
        return -.55;
    }

    private void moveRocket() {
        int multiplier = round.multiplier();
        double launch = Math.clamp((multiplier - 100) / 65.0, 0, 1);
        double arc = Math.sin(launch * Math.PI);
        double drift = arc * arc;
        DisplayMovement.move(rocket, at(rocketX() + .14 * drift, rocketY(multiplier), .57));
        var tilt = MachineGeometry.itemPose(4, 0, 0);
        tilt.getLeftRotation().rotateZ((float) (-.17 * drift * (1 - 2 * launch)));
        pose(rocket, tilt);
        rocket.setInterpolationDelay(0);
    }

    private double rocketY(int multiplier) {
        return MachineGeometry.rocketHeight(multiplier);
    }

    @Override
    protected void animate() {
        if (round.active()) {
            round.tick(System.currentTimeMillis());
            if (round.active()) touchPlayerInput();
            if (age % 10 == 0) sound(Sound.BLOCK_NOTE_BLOCK_BASS, .13f,
                    Math.min(1.8f, .7f + round.multiplier() / 500f));
            if (age % 2 == 0 || !round.active()) refresh();
            else moveRocket();
        }
    }
}
