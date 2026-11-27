package GoldenEggOVO.TDCasino;

public final class CasinoTestSupport {
    private CasinoTestSupport() {}
    public static void module(Object object, String name, Object value) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
