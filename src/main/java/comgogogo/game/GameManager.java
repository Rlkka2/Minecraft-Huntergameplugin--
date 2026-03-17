package comgogogo.game;

import comgogogo.MyPlugin;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {
    private final MyPlugin plugin;
    private final Map<String, GameRoom> rooms = new HashMap<>();

    public GameManager(MyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 创建新房间
     */
    public boolean createRoom(String roomName, Player creator) {
        if (rooms.containsKey(roomName)) {
            creator.sendMessage("§c错误：房间名称已存在！");
            return false;
        }
        
        GameRoom newRoom = new GameRoom(roomName, this);
        rooms.put(roomName, newRoom);
        newRoom.addPlayer(creator);
        creator.sendMessage("§a成功创建房间：" + roomName);
        return true;
    }

    /**
     * 删除房间
     */
    public boolean deleteRoom(String roomName, Player operator) {
        if (!rooms.containsKey(roomName)) {
            operator.sendMessage("§c错误：房间不存在！");
            return false;
        }
        
        GameRoom room = rooms.get(roomName);
        // 移出所有玩家
        room.getPlayers().keySet().forEach(player -> {
            room.removePlayer(player);
            player.sendMessage("§c房间 " + roomName + " 已被管理员删除");
        });
        
        rooms.remove(roomName);
        operator.sendMessage("§a成功删除房间：" + roomName);
        return true;
    }

    /**
     * 获取玩家所在房间
     */
    public GameRoom getPlayerRoom(Player player) {
        for (GameRoom room : rooms.values()) {
            if (room.getPlayers().containsKey(player)) {
                return room;
            }
        }
        return null;
    }

    /**
     * 游戏结束后清理房间
     */
    public void cleanupRoomAfterGame(GameRoom room) {
        String roomName = room.getRoomName();
        // 10秒后自动删除房间
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (rooms.containsKey(roomName)) {
                rooms.remove(roomName);
                plugin.getLogger().info("房间 " + roomName + " 已自动删除");
            }
        }, 200); // 200tick = 10秒
    }

    // Getter方法
    public MyPlugin getPlugin() {
        return plugin;
    }

    public Map<String, GameRoom> getRooms() {
        return rooms;
    }
}
    