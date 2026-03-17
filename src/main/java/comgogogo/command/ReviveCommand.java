package comgogogo.command;

import comgogogo.MyPlugin;
import comgogogo.game.GameRoom;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;


public class ReviveCommand implements CommandExecutor {
    private final MyPlugin plugin;
    // 复活所需钻石数量
    private static final int REQUIRED_DIAMONDS = 36;

    public ReviveCommand(MyPlugin plugin) {
        this.plugin = plugin;
        // 注册命令
        plugin.getCommand("fh").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查是否为玩家执行命令
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用复活命令！");
            return true;
        }

        Player reviver = (Player) sender;
        // 检查命令参数是否完整
        if (args.length < 1) {
            reviver.sendMessage("§c用法: /fh <幸存者玩家名>");
            return true;
        }

        // 检查执行者是否在游戏房间中
        GameRoom room = plugin.getGameManager().getPlayerRoom(reviver);
        if (room == null) {
            reviver.sendMessage("§c你不在游戏房间中，无法使用复活命令！");
            return true;
        }

        // 检查执行者是否为幸存者
        if (room.getPlayers().getOrDefault(reviver, false)) { // true为猎人
            reviver.sendMessage("§c只有幸存者可以使用复活命令！");
            return true;
        }

        // 查找目标玩家
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            reviver.sendMessage("§c目标玩家不在线或不存在！");
            return true;
        }

        // 检查目标是否在同一个游戏房间
        if (plugin.getGameManager().getPlayerRoom(target) != room) {
            reviver.sendMessage("§c目标玩家不在你的游戏房间中！");
            return true;
        }

        // 检查目标是否为幸存者
        if (room.getPlayers().getOrDefault(target, false)) { // true为猎人
            reviver.sendMessage("§c只能复活幸存者玩家！");
            return true;
        }

        // 检查目标是否需要复活（处于旁观者模式）
        if (target.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            reviver.sendMessage("§c目标玩家不需要复活！");
            return true;
        }

        // 检查执行者是否有足够的钻石
        int diamondCount = countDiamonds(reviver);
        if (diamondCount < REQUIRED_DIAMONDS) {
            reviver.sendMessage("§c复活需要" + REQUIRED_DIAMONDS + "个钻石，你只有" + diamondCount + "个！");
            return true;
        }

        // 扣除钻石
        removeDiamonds(reviver, REQUIRED_DIAMONDS);

        // 执行复活逻辑
        room.reviveSurvivor(target, reviver);
        
        // 发送提示信息
        reviver.sendMessage("§a成功消耗" + REQUIRED_DIAMONDS + "个钻石复活" + target.getName() + "！");
        target.sendMessage("§a你被" + reviver.getName() + "复活了！");
        room.broadcastMessage("§e" + target.getName() + "被" + reviver.getName() + "复活了！并对TA说 你的使命还没结束！");

        return true;
    }

    // 计算玩家拥有的钻石数量
    private int countDiamonds(Player player) {
        int count = 0;
        // 检查背包
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                count += item.getAmount();
            }
        }
        // 检查副手
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.DIAMOND) {
            count += offHand.getAmount();
        }
        return count;
    }

    // 从玩家物品栏中移除指定数量的钻石
    private void removeDiamonds(Player player, int amount) {
        int remaining = amount;
        // 从背包中移除
        for (int i = 0; i < player.getInventory().getContents().length && remaining > 0; i++) {
            ItemStack item = player.getInventory().getContents()[i];
            if (item != null && item.getType() == Material.DIAMOND) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        // 从副手移除（如果还需要）
        if (remaining > 0) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType() == Material.DIAMOND) {
                if (offHand.getAmount() <= remaining) {
                    remaining -= offHand.getAmount();
                    player.getInventory().setItemInOffHand(null);
                } else {
                    offHand.setAmount(offHand.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }
}
    
