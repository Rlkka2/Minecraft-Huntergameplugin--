package comgogogo;

import comgogogo.command.GameCommand;
import comgogogo.command.HelpCommand;
import comgogogo.command.ReviveCommand;
import comgogogo.command.SurrenderCommand;
import comgogogo.game.GameManager;
import comgogogo.listener.BookUseListener;
import comgogogo.listener.BossKillListener;
import comgogogo.listener.CompassTrackListener;
import comgogogo.listener.PlayerDeathListener;
import comgogogo.listener.PlayerJoinListener;
import comgogogo.listener.PlayerQuitListener;
import comgogogo.util.LocationUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {
    private GameManager gameManager;

    @Override
    public void onEnable() {
        // 初始化游戏管理器
        gameManager = new GameManager(this);
        
        // 初始化位置工具类
        LocationUtil.initialize(this);
        
        // 注册命令
        getCommand("hunter").setExecutor(new GameCommand(this));
        getCommand("hghelp").setExecutor(new HelpCommand());
        // 注册投降命令：将 "ggbo" 命令绑定到 SurrenderCommand 类
        this.getCommand("ggbo").setExecutor(new SurrenderCommand(this));
        new ReviveCommand(this);
        
        // 注册监听器
        getServer().getPluginManager().registerEvents(new CompassTrackListener(this), this);
        getServer().getPluginManager().registerEvents(new BookUseListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        
        // 新增：注册 BossKillListener
        getServer().getPluginManager().registerEvents(new BossKillListener(this), this);
        // 新增：注册 PlayerJoinListener
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        // 新增：注册 PlayerQuitListener
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        
        getLogger().info("猎人游戏插件已启用！作者：bhttps://space.bilibili.com/84453463?spm_id_from=333.1007.0.0");
    }

    @Override
    public void onDisable() {
        getLogger().info("猎人游戏插件已禁用！作者：bhttps://space.bilibili.com/84453463?spm_id_from=333.1007.0.0");
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}