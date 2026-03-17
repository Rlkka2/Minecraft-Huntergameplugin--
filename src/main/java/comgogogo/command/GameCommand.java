package comgogogo.command;

import comgogogo.MyPlugin;
import comgogogo.game.GameManager;
import comgogogo.game.GameRoom;
import comgogogo.game.GameState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameCommand implements CommandExecutor {
    private final MyPlugin plugin;

    public GameCommand(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用游戏指令！");
            return true;
        }

        Player player = (Player) sender;
        GameManager manager = plugin.getGameManager();

        if (args.length == 0) {
            player.sendMessage("§c请使用正确的指令格式！输入 /hghelp 查看帮助");
            return true;
        }

        // 创建房间
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                player.sendMessage("§c请指定房间名称！用法: /hg create <房间名称>");
                return true;
            }
            
            String roomName = args[1];
            if (manager.getRooms().containsKey(roomName)) {
                player.sendMessage("§c该房间名称已存在！请选择其他名称");
                return true;
            }
            
            GameRoom newRoom = new GameRoom(roomName, manager);
            manager.getRooms().put(roomName, newRoom);
            newRoom.addPlayer(player);
            player.sendMessage("§a房间创建成功！名称: " + roomName);
            return true;
        }

        // 加入房间
        if (args[0].equalsIgnoreCase("join")) {
            if (args.length < 2) {
                player.sendMessage("§c请指定要加入的房间名称！用法: /hg join <房间名称>");
                return true;
            }
            
            String roomName = args[1];
            GameRoom room = manager.getRooms().get(roomName);
            if (room == null) {
                player.sendMessage("§c找不到名称为 " + roomName + " 的房间！");
                return true;
            }
            
            if (room.getState() != GameState.WAITING) {
                player.sendMessage("§c该房间当前无法加入（可能正在游戏中）");
                return true;
            }
            
            if (room.getPlayers().size() >= room.getMaxPlayers()) {
                player.sendMessage("§c该房间已满人（" + room.getPlayers().size() + "/" + room.getMaxPlayers() + "）");
                return true;
            }
            
            room.addPlayer(player);
            player.sendMessage("§a成功加入房间: " + roomName);
            return true;
        }

        // 离开房间
        if (args[0].equalsIgnoreCase("leave")) {
            GameRoom currentRoom = manager.getPlayerRoom(player);
            if (currentRoom == null) {
                player.sendMessage("§c你当前不在任何房间中！");
                return true;
            }
            
            String roomName = currentRoom.getRoomName();
            currentRoom.removePlayer(player);
            player.sendMessage("§a已成功离开房间: " + roomName);
            return true;
        }

        // 开始游戏
        if (args[0].equalsIgnoreCase("start")) {
            GameRoom currentRoom = manager.getPlayerRoom(player);
            if (currentRoom == null) {
                player.sendMessage("§c你当前不在任何房间中！");
                return true;
            }
            
            if (currentRoom.getState() != GameState.WAITING) {
                player.sendMessage("§c游戏已经开始或已结束，无法重复开始！");
                return true;
            }
            
            if (currentRoom.getPlayers().size() < 2) {
                player.sendMessage("§c人数不足，至少需要2名玩家才能开始游戏！");
                return true;
            }
            
            currentRoom.startGame();
            return true;
        }

        // 删除房间（管理员）
        if (args[0].equalsIgnoreCase("delete")) {
            if (!player.hasPermission("huntergame.admin")) {
                player.sendMessage("§c你没有权限执行此操作！");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§c请指定要删除的房间名称！用法: /hg delete <房间名称>");
                return true;
            }
            
            String roomName = args[1];
            GameRoom room = manager.getRooms().remove(roomName);
            if (room == null) {
                player.sendMessage("§c找不到名称为 " + roomName + " 的房间！");
                return true;
            }
            
            // 移出房间内所有玩家
            room.getPlayers().keySet().forEach(p -> {
                room.removePlayer(p);
                p.sendMessage("§c你所在的房间 " + roomName + " 已被管理员删除");
            });
            
            player.sendMessage("§a房间 " + roomName + " 已成功删除");
            return true;
        }

        // 房间列表
        if (args[0].equalsIgnoreCase("list")) {
            player.sendMessage("§6===== 房间列表 =====");
            if (manager.getRooms().isEmpty()) {
                player.sendMessage("§c当前没有可用房间");
            } else {
                for (GameRoom room : manager.getRooms().values()) {
                    String stateStr;
                    switch (room.getState()) {
                        case WAITING:
                            stateStr = "§a等待中";
                            break;
                        case RUNNING:
                            stateStr = "§e进行中";
                            break;
                        case ENDED:
                            stateStr = "§c已结束";
                            break;
                        default:
                            stateStr = "§7未知";
                    }
                    player.sendMessage(String.format("§7- %s: %d/%d人 %s",
                            room.getRoomName(),
                            room.getPlayers().size(),
                            room.getMaxPlayers(),
                            stateStr));
                }
            }
            player.sendMessage("§6======================");
            return true;
        }

        // 未知指令
        player.sendMessage("§c未知指令！输入 /hghelp 查看所有可用指令");
        return true;
    }
}
    