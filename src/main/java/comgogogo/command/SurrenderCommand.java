package comgogogo.command;

import comgogogo.MyPlugin;
import comgogogo.game.GameRoom;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SurrenderCommand implements CommandExecutor {
    private final MyPlugin plugin;

    public SurrenderCommand(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用投降指令！");
            return true;
        }

        Player player = (Player) sender;
        GameRoom room = plugin.getGameManager().getPlayerRoom(player);

        if (room == null) {
            player.sendMessage("§c你不在任何游戏房间中，无法投降！");
            return true;
        }

        room.handleSurrender(player);
        return true;
    }
}
    