package comgogogo.listener;

import comgogogo.MyPlugin;
import comgogogo.game.GameRoom;
import comgogogo.util.LocationUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CompassTrackListener implements Listener {
    private final MyPlugin plugin;
    // 新增：存储每个猎人的指南针冷却时间（毫秒）
    private final Map<Player, Long> compassCooldowns = new HashMap<>();
    // 冷却时间：15秒（15*1000毫秒）
    private static final long COOLDOWN_TIME = 15000;

    public CompassTrackListener(MyPlugin plugin) {
        this.plugin = plugin;
    }

    // 修复：确保指南针正确指向幸存者
    @EventHandler
    public void onCompassUse(PlayerInteractEvent event) {
        // 只处理主手右键
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().toString().contains("RIGHT")) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 检查是否是猎人专用指南针
        if (item == null || !LocationUtil.isHunterCompass(item)) {
            return;
        }

        // 新增：检查冷却时间
        long currentTime = System.currentTimeMillis();
        if (compassCooldowns.containsKey(player)) {
            long cooldownEnd = compassCooldowns.get(player);
            if (currentTime < cooldownEnd) {
                long remainingSeconds = (cooldownEnd - currentTime) / 1000;
                player.sendMessage("§c指南针冷却中，还剩 " + remainingSeconds + " 秒！");
                event.setCancelled(true);
                return;
            }
        }

        // 获取玩家所在房间
        GameRoom room = plugin.getGameManager().getPlayerRoom(player);
        if (room == null) {
            player.sendMessage("§c你不在游戏房间中，无法使用猎人指南针！");
            return;
        }

        // 检查玩家是否是猎人
        if (!room.getPlayers().getOrDefault(player, false)) {
            player.sendMessage("§c只有猎人可以使用这个指南针！");
            return;
        }

        // 寻找存活的幸存者
        List<Player> survivors = new ArrayList<>();
        for (Player p : room.getPlayers().keySet()) {
            // 筛选存活的幸存者
            if (p != null && !room.getPlayers().get(p) && p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                survivors.add(p);
            }
        }

        // 设置指南针目标
        if (survivors.isEmpty()) {
            player.sendMessage("§c没有可追踪的幸存者！");
        } else {
            // 随机选择一个幸存者追踪
            Player target = survivors.get(new Random().nextInt(survivors.size()));
            // 修复：确保目标位置有效
            if (target.getLocation().getWorld() != null) {
                LocationUtil.setCompassTarget(item, target.getLocation());
                
                // 新增：计算并显示距离
                int distance = (int) player.getLocation().distance(target.getLocation());
                player.sendMessage("§6指南针已锁定目标：" + target.getName() + " §7(距离: " + distance + " 格)");
                
                // 新增：设置冷却时间
                compassCooldowns.put(player, currentTime + COOLDOWN_TIME);
            } else {
                player.sendMessage("§c目标位置无效，无法追踪！");
            }
        }
        
        event.setCancelled(true);
    }
}
    