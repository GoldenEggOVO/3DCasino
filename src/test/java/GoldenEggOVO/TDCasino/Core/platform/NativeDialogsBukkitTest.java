package GoldenEggOVO.TDCasino.Core.platform;

import GoldenEggOVO.TDCasino.Core.menu.MenuView;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeDialogsBukkitTest {
    @Test
    void nativeFormatPreservesHeaderInputActionsAndExit() {
        var header = new MenuView.Button(Component.text("Settings", NamedTextColor.AQUA),
                Component.text("Configure games"), 350, "settings-action", "preferences");
        var choice = new MenuView.Button(Component.text("Play"), null, 330, "play-action", "play");
        var back = new MenuView.Button(Component.text("Back"), null, 330, "back-action", "back");
        var exit = new MenuView.Button(Component.text("Close"), null, 330, "close-action", "close");
        var view = new MenuView(Component.text("Casino"),
                List.of(new MenuView.Body(Component.empty(), 370), new MenuView.Body(Component.text("Choose"), 370)),
                List.of(new MenuView.Input("amount", Component.text("Amount"), "10", 16)),
                List.of(header, choice, back), exit, 2, "casino");
        UUID token = UUID.randomUUID();
        var json = NativeDialogsBukkit.json(view, token);
        assertEquals("minecraft:multi_action", json.get("type").getAsString());
        assertEquals("close", json.get("after_action").getAsString());
        assertFalse(json.get("pause").getAsBoolean());
        assertEquals(2, json.get("columns").getAsInt());
        var body = json.getAsJsonArray("body");
        assertEquals(3, body.size());
        var caption = body.get(0).getAsJsonObject().getAsJsonObject("contents");
        assertEquals("3dcasino:dialog/" + token + "/0", caption.getAsJsonObject("click_event").get("id").getAsString());
        assertTrue(caption.has("hover_event"));
        assertEquals(300, body.get(1).getAsJsonObject().get("width").getAsInt());
        var input = json.getAsJsonArray("inputs").get(0).getAsJsonObject();
        assertEquals("amount", input.get("key").getAsString());
        assertEquals("10", input.get("initial").getAsString());
        assertEquals(16, input.get("max_length").getAsInt());
        var action = json.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals(144, action.get("width").getAsInt());
        assertEquals("minecraft:dynamic/custom", action.getAsJsonObject("action").get("type").getAsString());
        assertEquals("3dcasino:dialog/" + token + "/1", action.getAsJsonObject("action").get("id").getAsString());
        assertTrue(json.get("can_close_with_escape").getAsBoolean());
        assertEquals(1, json.getAsJsonArray("actions").size());
        var returnLink = body.get(2).getAsJsonObject().getAsJsonObject("contents");
        assertEquals("[Back]", returnLink.get("text").getAsString());
        assertEquals("yellow", returnLink.get("color").getAsString());
        assertEquals("3dcasino:dialog/" + token + "/2", returnLink.getAsJsonObject("click_event").get("id").getAsString());
        assertEquals("3dcasino:dialog/" + token + "/3", json.getAsJsonObject("exit_action").getAsJsonObject("action").get("id").getAsString());
    }

    @Test
    void emptyChoiceListUsesTheSameNoticeExitAndWidth() {
        var exit = new MenuView.Button(Component.text("Close"), null, 170, null, "close");
        var json = NativeDialogsBukkit.json(new MenuView(Component.text("Rules"), List.of(), List.of(),
                List.of(), exit, 1), UUID.randomUUID());
        assertEquals("minecraft:notice", json.get("type").getAsString());
        assertEquals(170, json.getAsJsonObject("action").get("width").getAsInt());
        assertFalse(json.getAsJsonObject("action").has("action"));
        assertFalse(json.has("exit_action"));
    }

    @Test
    void nativeAmountRequiresTheCurrentPlayersTokenAndAStringWithinTheInputLimit() {
        var player = mock(Player.class);
        var other = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        var save = new MenuView.Button(Component.text("Save"), null, 160, "save");
        var back = new MenuView.Button(Component.text("Back"), null, 160, "back");
        var view = new MenuView(Component.text("Amount"), List.of(),
                List.of(new MenuView.Input("amount", Component.text("Amount"), "1", 5)), List.of(save), back, 1);
        var received = new ArrayList<Map<String, String>>();
        var token = UUID.randomUUID();
        var sessions = new HashMap<UUID, NativeDialogsBukkit.Session>();
        sessions.put(player.getUniqueId(), new NativeDialogsBukkit.Session(token, view, (action, values) -> {
            assertEquals("save", action);
            received.add(values);
        }));
        String id = "3dcasino:dialog/" + token + "/0";
        String valid = "{\"amount\":\"12.34\"}";
        NativeDialogsBukkit.click(new BukkitClick(other, id, valid), sessions);
        NativeDialogsBukkit.click(new BukkitClick(player, "3dcasino:dialog/" + UUID.randomUUID() + "/0", valid), sessions);
        NativeDialogsBukkit.click(new BukkitClick(player, id, "{\"amount\":123}"), sessions);
        NativeDialogsBukkit.click(new BukkitClick(player, id, "{\"amount\":\"123456\"}"), sessions);
        assertTrue(received.isEmpty());
        var click = new BukkitClick(player, id, valid);
        NativeDialogsBukkit.click(click, sessions);
        NativeDialogsBukkit.click(click, sessions);
        assertEquals(List.of(Map.of("amount", "12.34")), received);
    }

    public static final class BukkitClick extends PlayerEvent {
        private final NamespacedKey id;
        private final JsonElement data;

        BukkitClick(Player player, String id, String json) {
            super(player);
            this.id = NamespacedKey.fromString(id);
            this.data = JsonParser.parseString(json);
        }

        public NamespacedKey getId() { return id; }
        public JsonElement getData() { return data; }
        @Override public HandlerList getHandlers() { throw new UnsupportedOperationException(); }
    }
}
