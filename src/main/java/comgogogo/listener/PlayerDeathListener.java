package comgogogo.listener;

import comgogogo.MyPlugin;
import comgogogo.game.GameRoom;
import comgogogo.game.GameState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerDeathListener implements Listener {
    private final MyPlugin plugin;

    public PlayerDeathListener(MyPlugin plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("PlayerDeathListener 已初始化");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getLogger().info("监听到玩家死亡: " + player.getName());
        
        GameRoom room = plugin.getGameManager().getPlayerRoom(player);
        
        if (room != null) {
            plugin.getLogger().info(player.getName() + " 在房间 " + room.getRoomName() + " 内死亡");
            event.setDeathMessage(null);
            room.onPlayerDeath(player);
        } else {
            plugin.getLogger().info(player.getName() + " 不在游戏房间内，不处理死亡事件");
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameRoom room = plugin.getGameManager().getPlayerRoom(player);
        
        if (room != null) {
            boolean isHunter = room.getPlayers().getOrDefault(player, false);
            if (isHunter && room.getState() == GameState.RUNNING) {
            giveCompassToRespawnedHunter(player);
        }
            if (!isHunter && room.getState() == GameState.RUNNING) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§c你已死亡，只能观察队友视角等待复活");
                event.setRespawnLocation(player.getLocation());

                // 强制绑定到第一个存活队友
                Player firstTeammate = getFirstAliveTeammate(room, player);
                if (firstTeammate != null) {
                    // 初始绑定
                    player.setSpectatorTarget(firstTeammate);
                    player.sendMessage("§e当前观察: " + firstTeammate.getName());
                    
                    // 持续监控，防止切换到非队友视角
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // 玩家已复活或离线，停止监控
                            if (player.getGameMode() != GameMode.SPECTATOR || !player.isOnline()) {
                                this.cancel();
                                return;
                            }
                            
                            // 检查当前观察目标是否为有效队友
                            Player currentTarget = player.getSpectatorTarget() instanceof Player ? 
                                                  (Player) player.getSpectatorTarget() : null;
                            
                            // 寻找最新的存活队友
                            Player newTarget = getFirstAliveTeammate(room, player);
                            
                            if (newTarget == null) {
                                // 无存活队友时保持在死亡点
                                if (player.getLocation().distance(event.getRespawnLocation()) > 10) {
                                    player.teleport(event.getRespawnLocation());
                                }
                                return;
                            }
                            
                            // 目标不是队友或目标已死亡，强制切换
                            if (currentTarget == null || !isValidTeammate(room, player, currentTarget)) {
                                player.setSpectatorTarget(newTarget);
                                player.sendMessage("§e已切换到队友视角: " + newTarget.getName());
                            }
                        }
                    }.runTaskTimer(plugin, 0, 10); // 每0.5秒检查一次，防止切换
                } else {
                    // 无队友时限制在死亡点
                    player.sendMessage("§e无存活队友，限制在死亡点附近观察");
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.getGameMode() != GameMode.SPECTATOR || !player.isOnline()) {
                                this.cancel();
                                return;
                            }
                            if (player.getLocation().distance(event.getRespawnLocation()) > 10) {
                                player.teleport(event.getRespawnLocation());
                            }
                        }
                    }.runTaskTimer(plugin, 0, 20);
                }
            }
        }
    }
    
    // 检查是否为有效的队友（同阵营且存活）
    private boolean isValidTeammate(GameRoom room, Player observer, Player target) {
        return target != null 
               && target.isOnline() 
               && target != observer 
               && !room.getPlayers().getOrDefault(target, false)  // 目标是幸存者
               && target.getGameMode() == GameMode.SURVIVAL;     // 目标存活
    }
    
    // 获取第一个存活的队友
    private Player getFirstAliveTeammate(GameRoom room, Player observer) {
        for (Player p : room.getPlayers().keySet()) {
            if (isValidTeammate(room, observer, p)) {
                return p;
            }
        }
        return null;
    }

    private void giveCompassToRespawnedHunter(Player p) {
        if (p == null || !p.isOnline()) return;

        // 获取世界
        org.bukkit.World world = p.getWorld();
        if (world == null) return;
            // 创建指南针（你真实存在的方法）
            org.bukkit.inventory.ItemStack compass = comgogogo.util.LocationUtil.createCompass();
            // 尝试放入背包
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover = p.getInventory().addItem(compass);
             // 放不进去就掉地上（和你 regive() 完全一样）
        if (!leftover.isEmpty()) {
            world.dropItem(p.getLocation(), compass);
        }
}
}
    