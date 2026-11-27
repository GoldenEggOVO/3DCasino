package GoldenEggOVO.TDCasino.game.plinko;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.Core.render.DisplayPlacement;
import GoldenEggOVO.TDCasino.Core.render.DisplayAudience;
import GoldenEggOVO.TDCasino.rules.CasinoRules;

import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.security.SecureRandom;
import java.util.*;

public final class PlinkoMachine extends GameMachine<PlinkoRound> {
    private final Map<PlinkoFlights.Flight, ItemDisplay> balls = new HashMap<>();
    private BlockDisplay light;

    public PlinkoMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new PlinkoRound(new SecureRandom()));
    }

    public static double slotLabelX(int slot) {
        return (slot - 6) * .36;
    }

    public static String multiplierLabel(int slot) {
        return String.format(
                Locale.ROOT, "%.2fX", CasinoRules.plinkoPayout(1_000_000, slot) / 1_000_000.0);
    }

    @Override
    protected void buildGame() {
        body("plinko_machine");
        button("play", "casino_button_play");
        for (int i = 0; i < 13; i++) {
            var label = text(slotLabelX(i), .76, .49, .26 / .75);
            PlatformMessages.text(label, Component.text(multiplierLabel(i), NamedTextColor.GOLD));
        }
        light =
                DisplayPlacement.spawn(
                                at(0, 1.04, .43),
                                BlockDisplay.class,
                                display -> {
                                    common(display);
                                    display.setBlock(Material.SEA_LANTERN.createBlockData());
                                    pose(
                                            display,
                                            new Transformation(
                                                    new Vector3f(-.15f / .75f, 0, -.08f / .75f),
                                                    new Quaternionf(),
                                                    new Vector3f(
                                                            .30f / .75f, .12f / .75f, .16f / .75f),
                                                    new Quaternionf()));
                                });
    }

    @Override
    protected boolean available(String action) {
        return action.equals("play");
    }

    @Override
    protected void action(String action) {
        var flight = round.launch();
        if (flight == null) return;
        launchFeedback(flight, round.stake());
        accountFeedback(flight, flight.returned());
        var ball = model("plinko_ball", 0, 4.5, .43, .135 / .75);
        ball.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        balls.put(flight, ball);
    }

    @Override
    protected void refresh() {}

    @Override
    protected void resultSound(MachineResultState.Result result) {
        switch (result.outcome()) {
            case LOSS -> resultCue(result, Sound.BLOCK_NOTE_BLOCK_BASS, .23f, .7f);
            case EVEN -> resultCue(result, Sound.BLOCK_NOTE_BLOCK_PLING, .18f, 1f);
            case WIN -> resultCue(result, Sound.BLOCK_NOTE_BLOCK_PLING, .22f, 1.25f);
            case BIG_WIN -> resultCue(result, Sound.BLOCK_NOTE_BLOCK_PLING, .25f, 1.5f);
        }
    }

    @Override
    protected void animate() {
        boolean pegHit = false;
        for (var flight : round.activeFlights()) {
            var point = PlinkoPath.at(flight.path(), flight.frame() / 8.0);
            DisplayMovement.move(balls.get(flight), at(point.x(), point.y(), .43));
            pegHit |= flight.frame() > 0 && flight.frame() < 96 && flight.frame() % 8 == 0;
        }
        if (pegHit)
            sound(Sound.BLOCK_GLASS_HIT, .16f, 1.5f);
        for (var flight : round.advance()) {
            int slot = PlinkoPath.slot(flight.path());
            DisplayMovement.move(light, at((slot - 6) * .36, 1.04, .43));
            var ball = balls.remove(flight);
            DisplayAudience.retire(ball);
            parts.remove(ball);
            sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, .2f, .8f);
            settleFeedback(flight, flight.returned());
        }
    }

    @Override
    protected boolean busy() {
        return false;
    }

    @Override
    protected boolean canEditStake() {
        return balls.isEmpty();
    }
}
