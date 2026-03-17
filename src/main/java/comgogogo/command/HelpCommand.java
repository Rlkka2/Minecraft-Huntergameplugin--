package comgogogo.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HelpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以查看帮助信息！");
            return true;
        }

        Player player = (Player) sender;
        player.sendMessage("§6===== 猎人游戏帮助 =====");
        player.sendMessage("§7这是一个猎人 vs 幸存者的对抗游戏");
        player.sendMessage("§7猎人需要追捕并消灭所有幸存者");
        player.sendMessage("§7幸存者需要生存并击败Boss获得胜利");
        player.sendMessage("");
        player.sendMessage("§a基本指令:");
        player.sendMessage("§a/hg create <名称> §7- 创建新游戏房间");
        player.sendMessage("§a/hg join <名称> §7- 加入指定房间");
        player.sendMessage("§a/hg leave §7- 离开当前房间");
        player.sendMessage("§a/hg start §7- 开始当前房间游戏");
        player.sendMessage("§a/hg delete <名称> §7- 删除房间（管理员）");
        player.sendMessage("§a/hg list §7- 查看所有房间");
        player.sendMessage("§a/ggbo §7- 发起本阵营投降投票");
        player.sendMessage("");
        player.sendMessage("§a胜利条件:");
        player.sendMessage("§7- 猎人: 消灭所有幸存者");
        player.sendMessage("§7- 幸存者: 击败末影龙或凋零");
        player.sendMessage("§6======================");
        return true;
    }
}
    