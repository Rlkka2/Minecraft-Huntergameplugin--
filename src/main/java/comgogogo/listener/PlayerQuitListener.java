package comgogogo.listener;

import comgogogo.MyPlugin;
import comgogogo.game.GameRoom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final MyPlugin plugin;

    public PlayerQuitListener(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameRoom room = plugin.getGameManager().getPlayerRoom(event.getPlayer());
        
        if (room != null) {
            room.removePlayer(event.getPlayer());
        }
    }
}
    