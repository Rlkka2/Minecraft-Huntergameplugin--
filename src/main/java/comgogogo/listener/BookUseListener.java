package comgogogo.listener;

import comgogogo.MyPlugin;
import comgogogo.game.GameRoom;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BookUseListener implements Listener {
    private final MyPlugin plugin;
    private final NamespacedKey killerBookKey;

    public BookUseListener(MyPlugin plugin) {
        this.plugin = plugin;
        this.killerBookKey = new NamespacedKey(plugin, "killer_book");
    }

    @EventHandler
    public void onKillerBookUse(PlayerInteractEvent event) {
        // 只处理主手右键点击
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().toString().contains("RIGHT")) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // 检查是否是KILLER书籍
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return;
        }

        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(killerBookKey, PersistentDataType.STRING)) {
            return;
        }

        // 获取玩家所在房间
        GameRoom room = plugin.getGameManager().getPlayerRoom(player);
        if (room == null) {
            player.sendMessage("§c你不在游戏房间中，无法使用此物品！");
            return;
        }

        // 检查玩家是否是幸存者
        if (room.getPlayers().getOrDefault(player, true)) {
            player.sendMessage("§c只有幸存者可以使用此物品！");
            return;
        }

        // 寻找所有存活的猎人
        List<Player> hunters = new ArrayList<>();
        for (Player p : room.getPlayers().keySet()) {
            if (p != null && room.getPlayers().get(p) && p.isOnline() && p.getGameMode() == GameMode.SURVIVAL) {
                hunters.add(p);
            }
        }

        if (hunters.isEmpty()) {
            player.sendMessage("§c没有可攻击的猎人！");
            return;
        }

        // 随机选择一名猎人使其死亡
        Player target = hunters.get(new Random().nextInt(hunters.size()));
        target.setHealth(0.0);
        
        // 确保书籍一次性使用 - 直接移除物品
        player.getInventory().removeItem(item);
        
        // 修复：正确使用粒子效果
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLAME, 
                                       target.getLocation().getX(), 
                                       target.getLocation().getY(), 
                                       target.getLocation().getZ(), 
                                       50, 0.5, 1, 0.5, 0.1);
        
        // 修复：使用插件广播方法替代房间广播
        for (Player p : room.getPlayers().keySet()) {
            if (p != null && p.isOnline()) {
                p.sendMessage("§c" + player.getName() + " KILLER？，" + target.getName() + " 因心脏麻痹死亡！");
            }
        }
        
        event.setCancelled(true);
    }
}
    