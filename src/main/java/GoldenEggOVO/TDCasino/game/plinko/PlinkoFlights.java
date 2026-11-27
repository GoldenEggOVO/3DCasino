package GoldenEggOVO.TDCasino.game.plinko;

import java.util.ArrayList;
import java.util.List;

public final class PlinkoFlights {
    public static final class Flight {
        final int path;
        private final long returned;
        int frame;

        Flight(int path, long returned) {
            this.path = path;
            this.returned = returned;
        }

        public int path() {
            return path;
        }

        public int frame() {
            return frame;
        }

        public long returned() {
            return returned;
        }
    }

    private final List<Flight> flights = new ArrayList<>();
    private int tick;
    private int lastLaunch = -4;

    public boolean canLaunch() {
        return flights.size() < 25 && tick - lastLaunch >= 4;
    }

    public Flight launch(int path, long returned) {
        if (!canLaunch()) {
            return null;
        }
        Flight flight = new Flight(path, returned);
        flights.add(flight);
        lastLaunch = tick;
        return flight;
    }

    public List<Flight> active() {
        return List.copyOf(flights);
    }

    public List<Flight> advance() {
        tick++;
        List<Flight> ended = new ArrayList<>();
        flights.removeIf(
                flight -> {
                    if (++flight.frame <= 96) {
                        return false;
                    }
                    ended.add(flight);
                    return true;
                });
        return ended;
    }
}
