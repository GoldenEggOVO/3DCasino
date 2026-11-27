package GoldenEggOVO.TDCasino.game.blackjack;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.Core.render.DisplayAudience;
import GoldenEggOVO.TDCasino.text.CasinoMessages;
import GoldenEggOVO.TDCasino.rules.CasinoRules;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;

public final class BlackjackMachine extends GameMachine<BlackjackRound> {
    private final List<ItemDisplay> dealerCards = new ArrayList<>(), playerCards = new ArrayList<>();
    private final Map<ItemDisplay, Slide> slides = new LinkedHashMap<>();
    private final Set<ItemDisplay> pendingDeals = new HashSet<>();
    private final Map<ItemDisplay, String> faces = new HashMap<>();
    private int dealLayer;
    private TextDisplay readout;

    public BlackjackMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new BlackjackRound(new SecureRandom()));
    }

    private static Component readout(List<Integer> player, List<Integer> dealer, boolean active) {
        var visibleDealer = active ? dealer.subList(0, Math.min(1, dealer.size())) : dealer;
        return CasinoMessages.component("blackjack.readout",
                "player", player.isEmpty() ? "—" : CasinoRules.total(player),
                "dealer", dealer.isEmpty() ? "—" : active ? "?" : CasinoRules.total(dealer))
                .append(Component.text("\n" + CasinoRules.cards(player) + "\n"
                        + CasinoRules.cards(visibleDealer) + (active && dealer.size() > 1 ? "  ?" : "")));
    }

    @Override
    protected void buildGame() {
        body("blackjack_machine");
        for (String action : List.of("double", "stand", "hit", "start"))
            button(action, "casino_button_" + (action.equals("start") ? "play" : action));
        model("blackjack_score_panel", 0, 0, 0, 4);
        readout = text(0, 1.24, -.76, .25);
    }

    @Override
    protected boolean available(String action) {
        return switch (action) {
            case "start" -> !round.active();
            case "double" -> round.active() && round.player().size() == 2;
            case "hit", "stand" -> round.active();
            default -> false;
        };
    }

    @Override
    protected void action(String action) {
        switch (action) {
            case "start" -> {
                round.start(System.currentTimeMillis());
                trim(dealerCards, 0);
                trim(playerCards, 0);
            }
            case "double" -> round.doubleDown();
            case "hit" -> round.hit();
            case "stand" -> round.stand();
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        refresh();
    }

    @Override
    protected void refresh() {
        var dealer = round.dealer();
        var player = round.player();
        trim(dealerCards, dealer.size());
        trim(playerCards, player.size());
        for (int i = 0; i < Math.max(player.size(), dealer.size()); i++) {
            if (i < player.size()) hand(player, playerCards, .35, false, i);
            if (i < dealer.size()) hand(dealer, dealerCards, -.32, round.active(), i);
        }
        PlatformMessages.text(readout, readout(player, dealer, round.active()));
    }

    private void trim(List<ItemDisplay> cards, int count) {
        while (cards.size() > count) {
            var card = cards.removeLast();
            slides.remove(card);
            pendingDeals.remove(card);
            faces.remove(card);
            parts.remove(card);
            DisplayAudience.retire(card);
        }
    }

    private void hand(List<Integer> hand, List<ItemDisplay> cards, double z, boolean hidden, int i) {
        String face = "card_" + (hidden && i > 0 ? 52 : hand.get(i));
        var target = at(MachineGeometry.cardX(i, hand.size()), .96 + i * .004, z);
        ItemDisplay card;
        if (i < cards.size()) card = cards.get(i);
        else {
            if (slides.isEmpty()) dealLayer = 0;
            card = item(model(face), 1.3, 1.1 - .006 * dealLayer++, -.55, .43, -Math.PI / 2);
            cards.add(card);
            int start = Math.max(age + 1,
                    slides.values().stream().mapToInt(Slide::start).max().orElse(age - 1) + 2);
            slides.put(card, new Slide(card.getLocation(), target, start));
            pendingDeals.add(card);
        }
        if (!face.equals(faces.get(card))) {
            card.setItemStack(model(face));
            faces.put(card, face);
        }
        if (slides.containsKey(card)) {
            var previous = slides.get(card);
            slides.put(card, new Slide(previous.from, target, previous.start));
        } else DisplayMovement.move(card, target);
    }

    @Override
    protected boolean busy() { return round.finished() && !slides.isEmpty(); }

    @Override
    protected boolean revealing() { return !slides.isEmpty(); }

    @Override
    protected void animate() {
        for (var iterator = slides.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var slide = entry.getValue();
            if (age >= slide.start && pendingDeals.remove(entry.getKey()))
                sound(Sound.ITEM_BOOK_PAGE_TURN, .2f, 1.2f);
            double t = Math.clamp((age - slide.start) / 8.0, 0, 1);
            double eased = t * t * (3 - 2 * t);
            double arc = Math.sin(Math.PI * t);
            double lift = .045 * slide.from.distance(slide.to) * arc * arc;
            DisplayMovement.move(entry.getKey(),
                            slide.from
                                    .clone()
                                    .add(
                                            slide.to
                                                    .toVector()
                                                    .subtract(slide.from.toVector())
                                                    .multiply(eased))
                                    .add(0, lift, 0));
            if (t >= 1) iterator.remove();
        }
    }

    private record Slide(Location from, Location to, int start) {}
}
