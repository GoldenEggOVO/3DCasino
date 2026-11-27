package GoldenEggOVO.TDCasino.game.plinko;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlinkoFlightsTest {
    @Test
    void acceptsNextBallBeforeFirstLandsButRejectsRapidDuplicates() {
        var flights = new PlinkoFlights();
        var first = flights.launch(0, 0);
        assertNotNull(first);
        assertNull(flights.launch(1, 0));
        for (int i = 0; i < 3; i++) flights.advance();
        assertNull(flights.launch(1, 0));
        flights.advance();
        assertNotNull(flights.launch(4095, 0));
        assertEquals(2, flights.active().size());
        assertEquals(4, first.frame);
    }

    @Test
    void ballsFinishIndependentlyAndCapacityIsBounded() {
        var flights = new PlinkoFlights();
        for (int i = 0; i < 25; i++) {
            assertNotNull(flights.launch(i, 0));
            if (i < 24) for (int j = 0; j < 4; j++) flights.advance();
        }
        assertEquals(25, flights.active().size());
        assertNull(flights.launch(33, 0));
        var ended = flights.advance();
        assertEquals(1, ended.size());
        assertEquals(0, ended.getFirst().path);
        assertEquals(24, flights.active().size());
        for (int i = 0; i < 100; i++) flights.advance();
        assertTrue(flights.active().isEmpty());
        assertNotNull(flights.launch(4095, 0));
    }
}
