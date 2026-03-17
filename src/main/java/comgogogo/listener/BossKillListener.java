package comgogogo.listener;

import comgogogo.MyPlugin;
import comgogogo.game.GameManager;
import comgogogo.game.GameRoom;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class BossKillListener implements Listener {
    private final GameManager gameManager;

    public BossKillListener(MyPlugin plugin) {
        this.gameManager = plugin.getGameManager();
    }

    // 检测末影龙/凋零击杀事件（幸存者胜利条件）
    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        // 检查是否为目标Boss
        if (!(event.getEntity() instanceof EnderDragon) && !(event.getEntity() instanceof Wither)) {
            return;
        }

        // 检查击杀者是否为玩家且在游戏房间内
        if (event.getEntity().getKiller() == null) {
            return;
        }

        // 查找击杀者所在的房间
        GameRoom room = gameManager.getRooms().values().stream()
                .filter(r -> r.getPlayers().containsKey(event.getEntity().getKiller()))
                .findFirst()
                .orElse(null);

        if (room == null || room.getState() != comgogogo.game.GameState.RUNNING) {
            return; // 不在进行中的游戏房间内
        }

        // 标记Boss被击杀，触发幸存者胜利
        if (event.getEntity() instanceof EnderDragon) {
            room.setEnderDragonKilled(true);
            room.getPlayers().keySet().forEach(p -> 
                p.sendMessage("§a末影龙被击杀！幸存者阵营胜利！")
            );
        } else {
            room.setWitherKilled(true);
            room.getPlayers().keySet().forEach(p -> 
                p.sendMessage("§a凋零被击杀！幸存者阵营胜利！")
            );
        }
    }
}