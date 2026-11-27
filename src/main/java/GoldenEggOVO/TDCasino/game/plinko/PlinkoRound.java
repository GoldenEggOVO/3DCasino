package GoldenEggOVO.TDCasino.game.plinko;

import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.rules.CasinoRules;
import java.util.List;
import java.util.Random;

public final class PlinkoRound extends GameRound {
    private final PlinkoFlights flights = new PlinkoFlights();

    public PlinkoRound(Random random) {
        super(random);
    }

    public boolean canLaunch() {
        return flights.canLaunch();
    }

    public PlinkoFlights.Flight launch() {
        if (!canLaunch()) return null;
        var transaction = funding().begin(stake);
        if (transaction == null) return null;
        int path = random.nextInt(4096);
        long returned = normalizePayout(CasinoRules.plinkoPayout(stake, PlinkoPath.slot(path)));
        funding().settle(transaction, returned);
        return flights.launch(path, returned);
    }

    public List<PlinkoFlights.Flight> activeFlights() {
        return flights.active();
    }

    public List<PlinkoFlights.Flight> advance() {
        var landed = flights.advance();
        for (var flight : landed) payout = flight.returned();
        return landed;
    }
}
