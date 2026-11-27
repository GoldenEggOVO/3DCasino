package GoldenEggOVO.TDCasino.machine;

import java.util.LinkedHashMap;
import java.util.Map;
public final class MachineResultState {
    public enum Outcome { LOSS, EVEN, WIN, BIG_WIN }
    public record Result(long stake, long returned) {
        public long net() { return returned - stake; }
        public Outcome outcome() {
            if (net() < 0) return Outcome.LOSS;
            if (net() == 0) return Outcome.EVEN;
            return stake > 0 && returned / stake >= 5 ? Outcome.BIG_WIN : Outcome.WIN;
        }
    }
    private final Map<Object, Long> pending = new LinkedHashMap<>();
    private final LinkedHashMap<Object, Result> settled = new LinkedHashMap<>();
    private Result last;
    private long totalNet;

    public void launch(Object id, long stake) {
        if (stake < 0) throw new IllegalArgumentException("Negative stake");
        if (pending.putIfAbsent(id, stake) == null) last = null;
    }
    public void reviseStake(Object id, long stake) {
        if (pending.containsKey(id) && !settled.containsKey(id)) pending.put(id, stake);
    }
    public boolean settle(Object id, long returned) {
        Long stake = pending.get(id);
        if (stake == null || settled.containsKey(id)) return false;
        settled.put(id, new Result(stake, returned));
        return true;
    }
    public long accountedNet() {
        long total = totalNet;
        for (Result result : settled.values()) total += result.net();
        return total;
    }
    public Result accountedLast() { return settled.isEmpty() ? last : settled.lastEntry().getValue(); }
    public Result reveal(Object id, long returned) {
        settle(id, returned);
        Result result = settled.remove(id);
        if (result == null) return null;
        pending.remove(id);
        last = result;
        totalNet += last.net();
        return last;
    }
    void restore(long total, Result last) { totalNet = total; this.last = last; pending.clear(); settled.clear(); }
    public Result last() { return last; }
    public int pending() { return pending.size(); }
    public long totalNet() { return totalNet; }
}
