package comgogogo.game;

import comgogogo.MyPlugin;
import comgogogo.util.LocationUtil;

// 确保导入这个类
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoom {
    private final String roomName;
    private final int maxPlayers = 15;
    private final GameManager gameManager;
    private GameState state = GameState.WAITING;
    private final Map<Player, Boolean> players = new ConcurrentHashMap<>();
    private int countdown = 45; // 游戏开始倒计时（秒）
    private Timer gameTimer; // 倒计时专用计时器
    private int gameDuration = 0;
    private Scoreboard scoreboard;
    private Objective objective;
    private boolean enderDragonKilled = false;
    private boolean witherKilled = false;
    
    // 庇护系统相关变量
    private final Map<Player, BlessingType> playerBlessings = new ConcurrentHashMap<>();
    private boolean blessingEffectGiven = false; // 是否已发放30分钟效果

    // 庇护类型枚举
    public enum BlessingType {
        HEAVEN_GUIDE("上天的指引"),
        HELL_MESSENGER("地狱的使者");
        
        private final String displayName;
        
        BlessingType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    public GameRoom(String roomName, GameManager gameManager) {
        this.roomName = roomName;
        this.gameManager = gameManager;
        initScoreboard();
    }

    // 获取最大玩家数
    public int getMaxPlayers() {
        return maxPlayers;
    }

    // 房间内广播消息
    public void broadcastMessage(String message) {
        players.keySet().forEach(player -> {
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }

    private void initScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            scoreboard = manager.getNewScoreboard();
            objective = scoreboard.registerNewObjective("hunterGame", "dummy", "§6猎人游戏");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
    }

    public void updateScoreboard() {
        if (objective == null) return;
        
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int minutes = gameDuration / 60;
        int seconds = gameDuration % 60;
        String timeStr = String.format("§7时长: %02d:%02d", minutes, seconds);
        objective.getScore(timeStr).setScore(10);
        int score = 9;
        for (Map.Entry<Player, Boolean> entry : players.entrySet()) {
            if (!entry.getValue()) {
                Player p = entry.getKey();
                if (p != null && p.isOnline()) {
                    String status = p.getGameMode() == GameMode.SURVIVAL ? "§a存活" : "§c死亡";
                    objective.getScore("§7" + p.getName() + ": " + status).setScore(score--);
                }
            }
        }

        players.keySet().forEach(p -> {
            if (p != null && p.isOnline()) {
                p.setScoreboard(scoreboard);
            }
        });
    }

    // 玩家加入房间 - 设置为冒险模式
    public void addPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        
        // 从其他房间移除该玩家
        gameManager.getRooms().values().forEach(room -> room.removePlayer(player));
        
        players.put(player, false);
        player.setScoreboard(scoreboard);
        clearPlayerInventory(player);
        applyInvincibility(player);
        
        // 加入房间后设置为冒险模式
        player.setGameMode(GameMode.ADVENTURE);
        
        gameManager.getPlugin().getLogger().info(player.getName() + " 加入房间 " + roomName);
        broadcastMessage("§a玩家 " + player.getName() + " 已加入房间，当前人数: " + players.size() + "/" + maxPlayers);
        checkStartConditions();
        updateScoreboard();
    }
    
    public void removePlayer(Player player) { if (player == null || !players.containsKey(player)) return;
        // 记录玩家退出前的阵营（是否为猎人）
        boolean wasHunter = players.get (player);

        players.remove (player);
        playerBlessings.remove (player); // 移除玩家的庇护记录
        gameManager.getPlugin ().getLogger ().info (player.getName () + "退出房间" + roomName);
        broadcastMessage ("§c 玩家" + player.getName () + "已退出房间，当前人数:" + players.size () + "/" + maxPlayers);

        // 退出房间后恢复为生存模式
        player.setGameMode (GameMode.SURVIVAL);
        player.getActivePotionEffects ().forEach (e -> player.removePotionEffect (e.getType ()));
        player.setScoreboard (Bukkit.getScoreboardManager ().getNewScoreboard ());
        clearPlayerInventory (player);

        // 新增：游戏进行中才检查阵营平衡（避免房间未开始 / 已结束时误判）
        if (state == GameState.RUNNING) {
        checkFactionBalance ();
        }
        
        checkStartConditions();
        updateScoreboard();
        }

        // 新增：检查阵营人数是否为空
        private void checkFactionBalance () {
        // 统计当前双方阵营人数
        long hunters = players.values ().stream ().filter (Boolean::booleanValue).count ();
        long survivors = players.size () - hunters;

        // 猎人全部退出 → 幸存者胜利
        if (hunters <= 0) {
        endGame (false);
        broadcastMessage ("§a 猎人阵营已全部退出，幸存者阵营胜利！");
        }
        // 幸存者全部退出 → 猎人胜利
        else if (survivors <= 0) {
        endGame (true);
        broadcastMessage ("§c 幸存者阵营已全部退出，猎人阵营胜利！");
        }
        }

    private void checkFactionBalanceAfterQuit() {
    // 统计当前双方阵营人数
    long hunters = players.values().stream().filter(Boolean::booleanValue).count();
    long survivors = players.size() - hunters;
    
    // 猎人全部退出 → 幸存者胜利
    if (hunters <= 0) {
        endGame(false); // false 表示幸存者胜利
        broadcastMessage("§a猎人阵营已全部退出，幸存者阵营胜利！");
    }
    // 幸存者全部退出 → 猎人胜利（已有的逻辑，确保保留）
    else if (survivors <= 0) {
        endGame(true); // true 表示猎人胜利
        broadcastMessage("§c幸存者阵营已全部退出，猎人阵营胜利！");
    }
    }
    private void assignTeams() {
        int total = players.size();
        int survivorCount;

        if (total <= 2) survivorCount = 1;
        else if (total <= 4) survivorCount = 2;
        else if (total <= 6) survivorCount = 3;
        else if (total <= 7) survivorCount = 4;
        else if (total <= 9) survivorCount = 5;
        else if (total <= 11) survivorCount = 6;
        else survivorCount = 7;

        List<Player> playerList = new ArrayList<>(players.keySet());
        Collections.shuffle(playerList);
        
        for (int i = 0; i < playerList.size(); i++) {
            Player p = playerList.get(i);
            if (p != null && p.isOnline()) {
                // 前survivorCount个为幸存者，其余为猎人
                players.put(p, i >= survivorCount);
                
                // 为幸存者分配庇护类型
                if (i < survivorCount) {
                    BlessingType type = new Random().nextBoolean() ? 
                        BlessingType.HEAVEN_GUIDE : BlessingType.HELL_MESSENGER;
                    playerBlessings.put(p, type);
                    p.sendMessage("§6你获得了【" + type.getDisplayName() + "】的庇护！");
                    p.sendMessage("§6游戏进行30分钟时将获得特殊效果！");
                }
            }
        }

        players.forEach((p, isHunter) -> {
            if (p != null && p.isOnline()) {
                p.sendTitle(
                        isHunter ? "§c猎人阵营" : "§a幸存者阵营",
                        "阵营已分配",
                        10, 60, 20
                );
                p.sendMessage("§6你属于 " + (isHunter ? "§c猎人阵营" : "§a幸存者阵营"));
            }
        });
    }

    private void checkStartConditions() {
        if (state != GameState.WAITING) return;
        if (players.size() >= 2) {
            startCountdown();
        } else {
            cancelCountdown();
        }
    }

    private void startCountdown() {
        if (gameTimer != null) return;
        
        gameTimer = new Timer();
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 确保在主线程执行
                Bukkit.getScheduler().runTask(gameManager.getPlugin(), () -> {
                    if (countdown <= 0) {
                        startGame();
                        if (gameTimer != null) {
                            gameTimer.cancel();
                            gameTimer = null;
                        }
                        return;
                    }
                    
                    String message;
                    if (countdown <= 10) {
                        message = "§c游戏将在 " + countdown + " 秒后开始！";
                    } else {
                        message = "§e游戏将在 " + countdown + " 秒后开始！";
                    }
                    broadcastMessage(message);
                    countdown--;
                });
            }
        }, 0, 1000);
    }

    private void cancelCountdown() {
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
            countdown = 90;
            broadcastMessage("§c倒计时已取消：房间人数不足2人");
        }
    }

    
    public void reviveSurvivor(Player survivor, Player reviver) {
        // 设置为生存模式
        survivor.setGameMode(org.bukkit.GameMode.SURVIVAL);
        
        // 恢复生命值和饥饿值
        survivor.setHealth(survivor.getMaxHealth());
        survivor.setFoodLevel(20);
        
        // 传送到复活者旁边（偏移1格避免重叠）
        Location reviverLoc = reviver.getLocation();
        // 计算旁边的安全位置（向复活者前方偏移1格）
        Location spawnLoc = reviverLoc.clone().add(
            reviverLoc.getDirection().multiply(1).getX(),  // X轴偏移
            0,  // Y轴不变
            reviverLoc.getDirection().multiply(1).getZ()   // Z轴偏移
        );
        
        // 确保Y轴是安全高度（避免卡在方块里）
        spawnLoc.setY(LocationUtil.getSafeY(
            spawnLoc.getWorld(), 
            spawnLoc.getBlockX(), 
            spawnLoc.getBlockZ()
        ));
        
        survivor.teleport(spawnLoc);
    }
    
    public void startGame() {
        if (state != GameState.WAITING) return;
        
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        
        state = GameState.RUNNING;
        assignTeams();
        clearAllPlayersInventory();
        
        // 在主线程执行关键操作
        Bukkit.getScheduler().runTask(gameManager.getPlugin(), () -> {
            // 1. 传送所有玩家
            teleportPlayers();
            
            // 2. 清除所有药水效果（包括无敌）
            removeAllPotionEffects();
            
            // 3. 应用初始增益
            applyInitialBuffs();
            
            // 4. 明确移除无敌效果
            removeInvincibility();
            
            // 5. 设置为生存模式
            setAllPlayersToSurvivalMode();
            
            // 6. 开始游戏循环
            startGameLoop();

            // 7.执行初始游戏世界设定,初始玩家状态
            gamerules();
            
            broadcastMessage("§6游戏开始！猎人开始追捕幸存者吧！");
            gameManager.getPlugin().getLogger().info("房间 " + roomName + " 游戏开始！");
        });
    }

    // 将所有玩家设置为生存模式
    private void setAllPlayersToSurvivalMode() {
        players.keySet().forEach(player -> {
            if (player != null && player.isOnline()) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        });
    }

    // 清除所有玩家的所有药水效果
    private void removeAllPotionEffects() {
        players.keySet().forEach(player -> {
            if (player != null && player.isOnline()) {
                for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                    player.removePotionEffect(effect.getType());
                }
                player.sendMessage("§7所有状态效果已重置");
            }
        });
    }

    private void gamerules() {
        // 主要更新：关闭经验条下的玩家定位器 适用1.21.5+
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "gamerule locatorBar false");
        // 游戏开始时设置为白天
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "time set day");
        // 游戏开始时设置为晴天
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "weather clear");
        
        

        for (Player player : players.keySet()) {  // 遍历房间内所有玩家
            if (player != null && player.isOnline()) {
                // 恢复满血（设置为最大生命值）
                player.setHealth(player.getMaxHealth());
                
                // 恢复满饥饿值（20为最大值）
                player.setFoodLevel(20);
                
                player.sendMessage("§a你已恢复至最佳状态！");
            }
         }
    }

    private void teleportPlayers() {
        players.forEach((p, isHunter) -> {
            if (p == null || !p.isOnline()) return;
            
            // 获取主世界
            World world = Bukkit.getWorlds().get(0);
            if (world == null) {
                p.sendMessage("§c错误：无法获取游戏世界！");
                return;
            }
            
            Location loc;
            if (isHunter) {
            // 猎人随机出生点（保持不变）
            int x = new Random().nextInt(2001) - 50;
            int z = new Random().nextInt(2001) - 50;
            int y = LocationUtil.getSafeY(world, x, z);
            loc = new Location(world, x, y, z);
        } else {
            // 幸存者出生点：扩大搜索范围避免在海中间
            int baseX = 1000;
            int baseZ = 1000;
            int y = LocationUtil.getSafeY(world, baseX, baseZ);
            
            // 检查脚下方块是否为液体（水/岩浆）
            Block feetBlock = new Location(world, baseX, y, baseZ).getBlock();
            Block belowBlock = world.getBlockAt(baseX, y - 1, baseZ);
            boolean isLiquid = feetBlock.isLiquid() || belowBlock.isLiquid();
            
            if (isLiquid) {
                // 扩大搜索范围到±1000格（原±10），增加找到陆地的概率
                boolean foundSafe = false;
                // 先搜索远处，再查近处（优先找离0,0较近的陆地）
                for (int distance = 1; distance <= 1000 && !foundSafe; distance++) {
                    // 环形搜索，覆盖更大范围
                    for (int angle = 0; angle < 360; angle += 8) { // 更多方向
                        double radians = Math.toRadians(angle);
                        int dx = (int)(Math.cos(radians) * distance);
                        int dz = (int)(Math.sin(radians) * distance);
                        
                        int checkX = baseX + dx;
                        int checkZ = baseZ + dz;
                        int checkY = LocationUtil.getSafeY(world, checkX, checkZ);
                        Block checkBlock = world.getBlockAt(checkX, checkY - 1, checkZ);
                        
                        if (!checkBlock.isLiquid()) {
                            // 找到非液体区域
                            baseX = checkX;
                            baseZ = checkZ;
                            y = checkY;
                            foundSafe = true;
                            break;
                        }
                    }
                }
            }
            
            loc = new Location(world, baseX, y, baseZ);
        }
            
            // 执行传送并验证结果
            boolean success = p.teleport(loc);
            if (success) {
                p.sendMessage("§a已传送至出生点！");
                // 游戏开始传送后所有玩家[一般为猎人]默认出生点，从而避免猎人开局自杀导致回到默认服务器出生点，从而缩短猎人与幸存者距离
                

            } else {
                p.sendMessage("§c传送失败，请联系管理员！");
            }
        });
    }

    private void applyInitialBuffs() {
        players.forEach((p, isHunter) -> {
            if (p == null || !p.isOnline()) return;
            
            // 获取玩家所在世界
            World world = p.getWorld();
            if (world == null) {
                p.sendMessage("§c错误：无法获取你的当前世界！");
                return;
            }
            
            if (isHunter) {
                // 猎人获得指南针
                ItemStack compass = LocationUtil.createCompass();
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(compass);
                
                if (leftover.isEmpty()) {
                    p.sendMessage("§6你获得了猎人专用指南针，右键可以追踪幸存者！");
                    // 设置初始目标
                    setFirstCompassTarget(p, compass);
                } else {
                    p.sendMessage("§c背包空间不足，指南针已掉落至地面！");
                    world.dropItem(p.getLocation(), compass);
                }
            } else {
                // 幸存者获得临时速度加成
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 
                        40 * 20,  // 30秒
                        1
                ));
                p.sendMessage("§6你获得了40秒的速度加成！");
            }
        });
    }

    private void regive() {
        players.forEach((p, isHunter) -> {
            if (p == null || !p.isOnline()) return;
            
            // 获取玩家所在世界
            World world = p.getWorld();
            if (world == null) {
                p.sendMessage("§c错误：无法获取你的当前世界！");
                return;
            }
            
            if (isHunter) {
                // 猎人获得指南针
                ItemStack compass = LocationUtil.createCompass();
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(compass);
                
                if (leftover.isEmpty()) {
                    // 设置初始目标
                    setFirstCompassTarget(p, compass);
                } else {
                    world.dropItem(p.getLocation(), compass);
                }
            } 
        });
    }
       
    private void setFirstCompassTarget(Player hunter, ItemStack compass) {
        List<Player> survivors = new ArrayList<>();
        for (Player p : players.keySet()) {
            if (p != null && !players.get(p) && p.isOnline() && p.getGameMode() == GameMode.SURVIVAL) {
                survivors.add(p);
            }
        }
        
        if (!survivors.isEmpty()) {
            Player target = survivors.get(new Random().nextInt(survivors.size()));
            if (target.getLocation().getWorld() != null) {
                LocationUtil.setCompassTarget(compass, target.getLocation());
            }
        }
    }

    private void startGameLoop() {
        // 猎人每5分钟获得加速增益
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTask(gameManager.getPlugin(), () -> {
                    if (state == GameState.RUNNING) {
                        applyRandomHunterBuff();
                    }
                });
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000);

        // 游戏计时和状态检查
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTask(gameManager.getPlugin(), () -> {
                    if (state != GameState.RUNNING) {
                        cancel();
                        return;
                    }
                    gameDuration++;
                    updateScoreboard();
                    checkVictoryConditions();
                    
                    // 检查是否达到30分钟，发放庇护效果
                    if (gameDuration >= 30 * 60 && !blessingEffectGiven) {
                        giveBlessingEffects();
                        blessingEffectGiven = true;
                    }
                    
                    // 30分钟前的提示
                    if (gameDuration == 25 * 60) {
                        broadcastMessage("§65分钟后，幸存者将获得庇护效果！");
                    }
                    if (gameDuration == 29 * 60) {
                        broadcastMessage("§61分钟后，幸存者将获得庇护效果！");
                    }
                });
            }
        }, 0, 1000);
    }

    // 发放30分钟庇护效果
    private void giveBlessingEffects() {
        broadcastMessage("§6===== 庇护效果激活 ======");
        broadcastMessage("§6所有幸存者获得了其庇护的特殊效果！");
        broadcastMessage("======================");
        
        for (Player survivor : players.keySet()) {
            // 只给存活的幸存者发放效果
            if (survivor == null || !survivor.isOnline() || 
                players.get(survivor) || survivor.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            
            BlessingType type = playerBlessings.get(survivor);
            if (type == null) continue;
            
            // 根据庇护类型发放效果
            if (type == BlessingType.HEAVEN_GUIDE) {
                giveHeavenGuideEffect(survivor);
            } else {
                giveHellMessengerEffect(survivor);
            }
        }
    }

    // "上天的庇护"效果（已修复附魔属性）
    private void giveHeavenGuideEffect(Player player) {
    Random random = new Random();
    double chance = random.nextDouble() * 100; // 0-100的随机数
    
    if (chance <= 1.0) {
        // 1% 神降凡间（获得鞘翅一个）
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        player.getInventory().addItem(elytra);
        player.sendMessage("§b【神降凡间】你获得了鞘翅！似乎你才是真正的...？");
    } else if (chance <= 2.0) {
        // 1% 你已弑神（获得附魔金苹果三个）
        ItemStack godApple = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3);
        player.getInventory().addItem(godApple);
        player.sendMessage("§b【你已弑神？】你获得了3个附魔金苹果！");
    } else if (chance <= 5.0) {
        // 3% 丘比特之弓（获得一把弓 附魔属性为火失1，无限1以及一根箭）
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.setDisplayName("§6丘比特之弓");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        bow.setItemMeta(meta);
        
        player.getInventory().addItem(bow);
        player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        player.sendMessage("§b【丘比特之弓】你获得了一把特殊的弓！");
    } else if (chance <= 8.0) {
        // 3% 穷途末路（获得一把钻石剑，附魔属性为锋利3，抢夺2）
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 3, true);
        meta.addEnchant(Enchantment.LOOTING, 2, true);
        meta.setDisplayName("§6穷途末路");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        sword.setItemMeta(meta);
        
        player.getInventory().addItem(sword);
        player.sendMessage("§b【穷途末路】你获得了一把强力钻石剑！你似乎已经走投无路....");
    } else if (chance <= 10.0) {
        // 2% 王的皇冠（获得下届合金头盔 附魔属性为保护1）
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        meta.addEnchant(Enchantment.PROTECTION, 1, true);
        meta.setDisplayName("§6王的皇冠");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        helmet.setItemMeta(meta);
        
        player.getInventory().addItem(helmet);
        player.sendMessage("§b【王的皇冠】你获得了一顶下界合金头盔！原来...我才是./王吗？");
    } else if (chance <= 25.0) {
        // 15% 神的庇护（获得一个不死图腾）
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        player.getInventory().addItem(totem);
        player.sendMessage("§b【神的庇护】你获得了不死图腾！我！永远不死....");
    } else if (chance <= 40.0) {
        // 15% 贪吃的猪（获得64个金胡萝卜）
        ItemStack carrots = new ItemStack(Material.GOLDEN_CARROT, 64);
        player.getInventory().addItem(carrots);
        player.sendMessage("§b【贪吃的猪】你获得了64个金胡萝卜！");
    } else if (chance <= 70.0) {
        // 30% 曼巴奥特（获得32个烤鸡肉 名字为：TACO）
        ItemStack tacos = new ItemStack(Material.COOKED_CHICKEN, 32);
        ItemMeta meta = tacos.getItemMeta();
        meta.setDisplayName("§6TACO");
        tacos.setItemMeta(meta);
        player.getInventory().addItem(tacos);
        player.sendMessage("§b【曼巴奥特】你获得了32个TACO！24+8=32？");
    } else if (chance <= 99.8) {
        // 29.8% 被神遗弃（获得黑曜石3个）
        ItemStack obsidian = new ItemStack(Material.OBSIDIAN, 3);
        player.getInventory().addItem(obsidian);
        player.sendMessage("§b【被神遗弃】你获得了3个黑曜石...吗？");
    } else if (chance <= 99.9) {
        // 0.1% 隐藏：与岷同在 (获得10个橡木树树苗以及10个骨粉)
        ItemStack saplings = new ItemStack(Material.OAK_SAPLING, 10);
        ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 10);
        player.getInventory().addItem(saplings);
        player.getInventory().addItem(boneMeal);
        player.sendMessage("§b【隐藏：与岷同在】你获得了10个橡树苗和10个骨粉！爱护大籽然");
    } else {
        // 0.1% 隐藏：欸嘿....战斗桶 (获得1个水桶1个岩浆桶)
        ItemStack waterBucket = new ItemStack(Material.WATER_BUCKET);
        ItemStack lavaBucket = new ItemStack(Material.LAVA_BUCKET);
        player.getInventory().addItem(waterBucket);
        player.getInventory().addItem(lavaBucket);
        player.sendMessage("§b【隐藏：欸嘿....战斗桶】你获得了水桶和岩浆桶！马桶中的....");
    }
}

// "地狱的使者"效果（已修复附魔属性）
private void giveHellMessengerEffect(Player player) {
    Random random = new Random();
    double chance = random.nextDouble() * 100; // 0-100的随机数
    
    if (chance <= 0.1) {
        // 0.1% KILLER（获得一本书，点击下对方阵营随机一人直接死亡）
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("§cKILLER");
        meta.setAuthor("撒旦");
        meta.addPage("§4点击使用此书籍\n§4随机杀死一名猎人");
        book.setItemMeta(meta);
        
        // 为书籍添加特殊标记，用于监听器识别
        ItemMeta itemMeta = book.getItemMeta();
        itemMeta.getPersistentDataContainer().set(
            new NamespacedKey(gameManager.getPlugin(), "killer_book"),
            PersistentDataType.STRING,
            "true"
        );
        book.setItemMeta(itemMeta);
        
        player.getInventory().addItem(book);
        player.sendMessage("§c【KILLER】死亡笔记！我决定要这个世界的所有有罪的的人..都死去？！");
    } else if (chance <= 1.1) {
        // 1% 恭迎撒旦！（获得下届合金甲 附魔属性为保护4以及一把下届合金剑 附魔属性为抢夺2）
        // 下界合金胸甲
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta chestMeta = chestplate.getItemMeta();
        chestMeta.addEnchant(Enchantment.PROTECTION, 4, true);
        chestMeta.setDisplayName("§c撒旦的庇护");
        chestMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        chestplate.setItemMeta(chestMeta);
        
        // 下界合金剑
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.addEnchant(Enchantment.LOOTING, 2, true);
        swordMeta.setDisplayName("§c撒旦之刃");
        swordMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        sword.setItemMeta(swordMeta);
        
        player.getInventory().addItem(chestplate);
        player.getInventory().addItem(sword);
        player.sendMessage("§c【恭迎撒旦！】你获得了撒旦的恩赐！");
    } else if (chance <= 2.1) {
        // 1% 双子塔破坏者（获得鞘翅一个）
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.setDisplayName("§c双子塔破坏者");
        elytra.setItemMeta(meta);
        player.getInventory().addItem(elytra);
        player.sendMessage("§c【双子塔破坏者】你获得了鞘翅！我也可以劫持....？");
    } else if (chance <= 5.1) {
        // 3% 本拉登假死(获得一把弓 附魔属性为火失1，无限1以及一根箭)
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.setDisplayName("§c本拉登的弓");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        bow.setItemMeta(meta);
        
        player.getInventory().addItem(bow);
        player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        player.sendMessage("§c【本拉登假死】你获得了一把特殊的弓！不是黄金AK吗？？？？");
    } else if (chance <= 8.1) {
        // 3% 日轮刀？（获得下届合金剑一个）
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§c日轮刀？");
        sword.setItemMeta(meta);
        player.getInventory().addItem(sword);
        player.sendMessage("§c【日轮刀？】为什么别人的都有颜色，而我的是黑色.....");
    } else if (chance <= 13.1) {
        // 5% 憋佬仔（获得一个不死图腾和12个金锭）
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemStack goldIngots = new ItemStack(Material.GOLD_INGOT, 12);
        player.getInventory().addItem(totem);
        player.getInventory().addItem(goldIngots);
        player.sendMessage("§c【憋佬仔】你获得了不死图腾和12个金锭！玉牌和来财？");
    } else if (chance <= 24.9) {
        // 11.8% 五灵之力！（获得金苹果五个）
        ItemStack goldenApples = new ItemStack(Material.GOLDEN_APPLE, 5);
        player.getInventory().addItem(goldenApples);
        player.sendMessage("§c【五灵之力！】你获得了5个金苹果！变身！");
    } else if (chance <= 39.9) {
        // 15% 梦之泪殇（获得一个不死图腾）
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        player.getInventory().addItem(totem);
        player.sendMessage("§c【梦之泪殇】你获得了不死图腾！他出了个名刀司命？");
    } else if (chance <= 54.9) {
        // 15% 地狱行者（获得金靴子 附魔属性为火焰保护4）
        ItemStack boots = new ItemStack(Material.GOLDEN_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        meta.addEnchant(Enchantment.FIRE_PROTECTION, 4, true);
        meta.setDisplayName("§c地狱行者");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        boots.setItemMeta(meta);
        
        player.getInventory().addItem(boots);
        player.sendMessage("§c【地狱行者】你获得了一双防火靴！");
    } else if (chance <= 69.9) {
        // 15% 电锯英雄（获得一把钻石剑，附魔属性为耐久3，亡灵杀手3）
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.SMITE, 3, true);
        meta.setDisplayName("§c电锯英雄");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        sword.setItemMeta(meta);
        
        player.getInventory().addItem(sword);
        player.sendMessage("§c【电锯英雄】你获得了一把特制钻石剑！似乎用它杀恶魔，恶魔就会永远消失....");
    } else if (chance <= 99.9) {
        // 30% 沙克也....（获得32个烤鸡肉 ）
        ItemStack chicken = new ItemStack(Material.COOKED_CHICKEN, 32);
        player.getInventory().addItem(chicken);
        player.sendMessage("§c【沙克也....】你获得了32个烤鸡肉！什么劳大你居然....");
    } else {
        // 0.1% 隐藏：东北雨（获得一个1个恶魂之泪）
        ItemStack tear = new ItemStack(Material.GHAST_TEAR, 1);
        player.getInventory().addItem(tear);
        player.sendMessage("§c【隐藏：东北雨】你获得了1个恶魂之泪！带派不老铁");
    }
}

    private void applyRandomHunterBuff() {
        players.forEach((p, isHunter) -> {
            if (!isHunter || p == null || !p.isOnline()) return;
            
            double rand = Math.random();
            PotionEffect effect;
            String buffInfo;
            
            if (rand < 0.5) {
                effect = new PotionEffect(PotionEffectType.SPEED, 30 * 20, 0);
                buffInfo = "速度 I (30秒)";
            } else if (rand < 0.8) {
                effect = new PotionEffect(PotionEffectType.SPEED, 15 * 20, 1);
                buffInfo = "速度 II (15秒)";
            } else {
                effect = new PotionEffect(PotionEffectType.SPEED, 10 * 20, 2);
                buffInfo = "速度 III (10秒)";
            }
            
            p.removePotionEffect(PotionEffectType.SPEED);
            p.addPotionEffect(effect);
            p.sendMessage("§6[猎人增益] 获得" + buffInfo + "！");
        });
    }

    private void checkVictoryConditions() {
    // 统计“存活的幸存者”：必须是幸存者、在线、且处于生存模式
    long aliveSurvivors = players.entrySet().stream()
            .filter(entry -> !entry.getValue()) // 是幸存者（非猎人）
            .filter(entry -> entry.getKey() != null && entry.getKey().isOnline()) // 玩家有效且在线
            .filter(entry -> entry.getKey().getGameMode() == GameMode.SURVIVAL) // 处于生存模式（未死亡）
            .count();

    // 所有幸存者死亡 → 猎人胜利
    if (aliveSurvivors <= 0) {
        endGame(true);
        return;
    }

    // Boss击杀胜利条件（保持不变）
    if (enderDragonKilled || witherKilled) {
        endGame(false);
    }
}

    public void endGame(boolean hunterWin) {
        state = GameState.ENDED;
        String title = hunterWin ? "§c猎人阵营胜利！" : "§a幸存者阵营胜利！";
        String subtitle = hunterWin ? "所有幸存者已被消灭" : "成功击败Boss";
        
        players.keySet().forEach(player -> {
            if (player != null && player.isOnline()) {
                player.sendTitle(title, subtitle, 10, 60, 10);
                player.sendMessage(title + " " + subtitle);
                player.setGameMode(GameMode.SURVIVAL);
                player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                clearPlayerInventory(player);
            }
        });
        
        gameManager.getPlugin().getLogger().info("房间 " + roomName + " 游戏结束：" + title);
        
        // 游戏结束后10秒自动删除房间
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTask(gameManager.getPlugin(), () -> {
                    gameManager.getRooms().remove(roomName);
                    gameManager.getPlugin().getLogger().info("房间 " + roomName + " 已自动删除");
                });
            }
        }, 10 * 1000);
    }

    public void onPlayerDeath(Player player) {
    if (state != GameState.RUNNING || player == null || !player.isOnline()) return;
    
    World world = player.getWorld();
    if (world == null) {
        player.sendMessage("§c错误：无法获取你的当前世界！");
        return;
    }
    
    boolean isHunter = players.getOrDefault(player, false);
    clearPlayerInventory(player);
    
    if (isHunter) {
        // 猎人复活逻辑保持不变
        player.setGameMode(GameMode.SURVIVAL);
        
        ItemStack compass = LocationUtil.createCompass();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(compass);
        
        if (leftover.isEmpty()) {
            player.sendMessage("§c你已复活！继续狩猎！");
            regive();
            setFirstCompassTarget(player, compass);
        } else {
            player.sendMessage("§c背包空间不足，指南针已掉落至地面！");
            world.dropItem(player.getLocation(), compass);
        }
    } else {
        // 幸存者死亡逻辑强化
        player.setGameMode(GameMode.SPECTATOR); // 强制旁观者模式
        player.sendMessage("§c你已死亡，进入旁观者模式。");
        broadcastMessage("§c幸存者 " + player.getName() + " 已被消灭！");
        
        // 关键：立即从玩家列表中标记为“非生存状态”（辅助胜利检查）
        players.put(player, false); // 确保状态同步
    }
    
    updateScoreboard();
    // 强制检查胜利条件（确保最后一名幸存者死亡时触发）
    checkVictoryConditions();
    }

    public boolean handleSurrender(Player initiator) {
        if (state != GameState.RUNNING || initiator == null || !initiator.isOnline()) return false;
        
        boolean isHunter = players.getOrDefault(initiator, false);
        long total = players.values().stream().filter(b -> b == isHunter).count();
        long yesVotes = 1;

        for (Player p : players.keySet()) {
            if (p != null && p.isOnline() && players.get(p) == isHunter && p != initiator) {
                yesVotes++;
            }
        }

        if (yesVotes == total) {
            endGame(!isHunter);
            broadcastMessage("§c" + (isHunter ? "猎人" : "幸存者") + "阵营全体同意投降！");
            return true;
        }
        initiator.sendMessage("§c投降需要同阵营全票同意！当前：" + yesVotes + "/" + total);
        return false;
    }

    private void applyInvincibility(Player player) {
        if (player == null || !player.isOnline()) return;
        
        // 等待阶段给予高等级生命回复作为无敌效果
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.REGENERATION,
            Integer.MAX_VALUE,
            255,
            false,
            false
        ));
    }

    private void removeInvincibility() {
        players.keySet().forEach(player -> {
            if (player != null && player.isOnline()) {
                // 先移除再添加0级效果确保彻底清除
                player.removePotionEffect(PotionEffectType.REGENERATION);
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    1,
                    0,
                    false,
                    false
                ));
                player.sendMessage("§7无敌状态已解除！");
            }
        });
    }

    private void clearPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;
        
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.setItemOnCursor(null);
    }

    private void clearAllPlayersInventory() {
        players.keySet().forEach(this::clearPlayerInventory);
    }

    // Getter方法
    public String getRoomName() { return roomName; }
    public GameState getState() { return state; }
    public Map<Player, Boolean> getPlayers() { return players; }
    public Scoreboard getScoreboard() { return scoreboard; }
    public void setEnderDragonKilled(boolean flag) { this.enderDragonKilled = flag; }
    public void setWitherKilled(boolean flag) { this.witherKilled = flag; }
    public MyPlugin getPlugin() { return gameManager.getPlugin(); }
}
    