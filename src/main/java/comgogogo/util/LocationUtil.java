package comgogogo.util;

import comgogogo.MyPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

public class LocationUtil {
    private static MyPlugin plugin;
    private static NamespacedKey compassKey;

    /**
     * 初始化工具类
     */
    public static void initialize(MyPlugin pluginInstance) {
        plugin = pluginInstance;
        compassKey = new NamespacedKey(plugin, "hunter_compass");
    }

    /**
     * 获取安全的Y坐标（地面位置）
     */
    public static int getSafeY(World world, int x, int z) {
        if (world == null) return 64; // 默认高度
        
        int highestY = world.getHighestBlockYAt(x, z);
        // 确保不是空气方块
        while (highestY > 0 && world.getBlockAt(x, highestY, z).getType() == Material.AIR) {
            highestY--;
        }
        return highestY + 1; // 站在方块上方
    }

    /**
     * 创建猎人专用指南针
     */
    public static ItemStack createCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        
        if (meta != null && compassKey != null) {
            meta.setDisplayName("§6猎人追踪指南针");
            meta.getPersistentDataContainer().set(
                compassKey,
                PersistentDataType.STRING,
                "hunter_track"
            );
            compass.setItemMeta(meta);
        }
        
        return compass;
    }

    /**
     * 设置指南针跟踪目标
     */
    public static void setCompassTarget(ItemStack compass, Location target) {
        if (compass == null || !(compass.getItemMeta() instanceof CompassMeta) || target == null) {
            return;
        }
        
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestone(target);
        meta.setLodestoneTracked(false); // 不跟踪自然磁石
        compass.setItemMeta(meta);
    }

    /**
     * 检查是否是猎人专用指南针
     */
    public static boolean isHunterCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !(item.getItemMeta() instanceof CompassMeta) || compassKey == null) {
            return false;
        }
        
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        return meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }

    /**
     * 获取插件实例
     */
    public static MyPlugin getPlugin() {
        return plugin;
    }
}
    