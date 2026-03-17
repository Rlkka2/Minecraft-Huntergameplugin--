package comgogogo.listener;

import comgogogo.MyPlugin;
import comgogogo.game.GameManager;
import comgogogo.game.GameRoom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final GameManager gameManager;

    public PlayerJoinListener(MyPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    // 玩家加入服务器时触发
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 重置玩家状态（防止残留旁观者模式或Buff）
        event.getPlayer().setGameMode(org.bukkit.GameMode.SURVIVAL);
        event.getPlayer().getActivePotionEffects().forEach(effect -> 
            event.getPlayer().removePotionEffect(effect.getType())
        );
        event.getPlayer().getInventory().clear(); // 清空背包

        // 发送欢迎信息
        event.getPlayer().sendMessage("§6===== 欢迎来到猎人游戏 =====");
        event.getPlayer().sendMessage("§a输入 /hg help 查看指令帮助");
        event.getPlayer().sendMessage("§a输入 /hunter game list 查看房间");

        // 检查是否有进行中的游戏（可选：提示玩家加入）
        long runningRooms = gameManager.getRooms().values().stream()
                .filter(room -> room.getState() == comgogogo.game.GameState.WAITING)
                .count();
        if (runningRooms > 0) {
            event.getPlayer().sendMessage("§e当前有 " + runningRooms + " 个等待中的房间，快来加入吧！");
        }
    }

    // 玩家加入房间后的额外处理（如同步记分牌）
    public void onPlayerJoinRoom(GameRoom room, org.bukkit.entity.Player player) {
        // 同步房间记分牌
        player.setScoreboard(room.getScoreboard());
        // 广播房间内玩家加入信息
        room.getPlayers().keySet().forEach(p -> 
            p.sendMessage("§a玩家 " + player.getName() + " 加入了房间（当前人数：" + room.getPlayers().size() + "/15）")
        );
    }
}