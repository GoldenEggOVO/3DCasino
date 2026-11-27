package GoldenEggOVO.TDCasino.game.blackjack;

import GoldenEggOVO.TDCasino.rules.CasinoRules;
import GoldenEggOVO.TDCasino.game.StartedRound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BlackjackRound extends StartedRound {
    private final List<Integer> player = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private final List<Integer> deck = new ArrayList<>();
    private int nextCardIndex;

    public BlackjackRound(Random random) {
        super(random);
    }

    public List<Integer> player() {
        return List.copyOf(player);
    }

    public List<Integer> dealer() {
        return List.copyOf(dealer);
    }

    public List<Integer> deck() {
        return List.copyOf(deck);
    }

    public int cardsDrawn() {
        return nextCardIndex;
    }

    @Override
    public void start(long now) {
        if (active) {
            return;
        }
        beginRound();
        deck.clear();
        for (int card = 0; card < 52; card++) {
            deck.add(card);
        }
        Collections.shuffle(deck, random);
        nextCardIndex = 0;
        player.clear();
        dealer.clear();
        player.add(draw());
        dealer.add(draw());
        player.add(draw());
        dealer.add(draw());
        if (CasinoRules.natural(player) || CasinoRules.natural(dealer)) {
            settleBlackjack();
        }
    }

    public void hit() {
        if (active) {
            player.add(draw());
            if (CasinoRules.total(player) >= 21) {
                stand();
            }
        }
    }

    public void stand() {
        if (!active) {
            return;
        }
        while (CasinoRules.total(player) <= 21 && CasinoRules.total(dealer) < 17) {
            dealer.add(draw());
        }
        settleBlackjack();
    }

    public void doubleDown() {
        if (!active || player.size() != 2) {
            return;
        }
        addStake(stake);
        player.add(draw());
        stand();
    }

    private int draw() {
        return deck.get(nextCardIndex++);
    }

    private void settleBlackjack() {
        settle(CasinoRules.blackjackPayout(stake, player, dealer));
    }
}
