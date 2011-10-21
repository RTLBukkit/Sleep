package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;
import edgruberman.bukkit.sleep.activity.ActivityManager;
import edgruberman.bukkit.sleep.commands.Sleep;

public final class Main extends JavaPlugin {
    
    /**
     * Prefix for all permissions used in this plugin.
     */
    public static final String PERMISSION_PREFIX = "sleep";
    
    /**
     * Base path, relative to plugin data folder, to look for world specific
     * configuration overrides in.
     */
    private static final String WORLD_SPECIFICS = "Worlds";

    public static MessageManager messageManager;
    
    private static ConfigurationFile configurationFile;
    static Plugin plugin;
    
    public void onLoad() {
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
        
        Main.configurationFile = new ConfigurationFile(this);
        
        // Static loading of world specific configuration files requires reference to owning plugin.
        Main.plugin = this;
        
        // Prepare list of supported activity monitors.
        new ActivityManager(this);
    }
    
    public void onEnable() {
        // Load configuration file and load initial sleep states.
        this.loadConfiguration();
        
        // Start monitoring for activities listed in configuration.
        ActivityManager.registerEvents();
        
        // Track sleep state for new worlds as appropriate.
        new StateLoader(this);
        
        // Monitor for creature spawns caused by sleep.
        new NightmareTracker(this);
        
        // Start monitoring events related to players sleeping.
        new PlayerMonitor(this);
        
        // Register commands.
        new Sleep(this);
        
        Main.messageManager.log("Plugin Enabled");
    }
    
    public void onDisable() {
        ActivityManager.monitors.clear();
        State.tracked.clear();
        State.defaultNether = null;
        
        Main.messageManager.log("Plugin Disabled");
    }
    
    /**
     * Load plugin's configuration file and reset sleep states for each world.
     */
    @SuppressWarnings("unchecked")
    public void loadConfiguration() {
        Main.configurationFile.load();
        
        State.defaultNether = this.findDefaultNether();
        Main.messageManager.log("Default Nether: " + (State.defaultNether != null ? State.defaultNether.getName() : "<Not found>"), MessageLevel.CONFIG);
        
        State.excluded.clear();
        State.excluded.addAll(Main.configurationFile.getConfig().getList("excluded", Collections.<String>emptyList()));
        Main.messageManager.log("Excluded Worlds: " + State.excluded, MessageLevel.CONFIG);

        StateLoader.reset();
    }
    
    /**
     * Track sleep state for a specified world. (Worlds explicitly excluded in
     * the configuration file and the default nether world will be cancelled.) 
     * 
     * @param world world to track sleep state for
     */
    static void loadState(final World world) {
        // Cancel this function for explicitly excluded worlds and the default nether.
        if (State.excluded.contains(world.getName()) || world.equals(State.defaultNether)) {
            Main.messageManager.log("Sleep state for [" + world.getName() + "] will not be tracked.", MessageLevel.CONFIG);
            return;
        }
        
        // Discard any existing state tracking.
        State.tracked.remove(world);
        
        // Load configuration values using defaults defined in code, overridden
        // by defaults in the configuration file, overridden by world specific
        // settings in the Worlds folder.
        FileConfiguration pluginMain = Main.configurationFile.getConfig();
        FileConfiguration worldSpecific = (new ConfigurationFile(Main.plugin, WORLD_SPECIFICS + "/" + world.getName() + ".yml")).getConfig();
        
        boolean sleep = Main.loadBoolean(worldSpecific, pluginMain, "sleep", State.DEFAULT_SLEEP);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Sleep Enabled: " + sleep, MessageLevel.CONFIG);
        
        boolean safe = Main.loadBoolean(worldSpecific, pluginMain, "safe", State.DEFAULT_SAFE);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Safe (No nightmares): " + safe, MessageLevel.CONFIG);
        
        int inactivityLimit = Main.loadInt(worldSpecific, pluginMain, "inactivityLimit", State.DEFAULT_INACTIVITY_LIMIT);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Inactivity Limit (seconds): " + inactivityLimit, MessageLevel.CONFIG);
        
        Set<String> ignoredAlways = new HashSet<String>(Main.loadStringList(worldSpecific, pluginMain, "ignoredAlways", new ArrayList<String>()));
        ignoredAlways.addAll(Main.loadStringList(worldSpecific, pluginMain, "ignoredAlwaysAlso", new ArrayList<String>()));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Always Ignored Players (Configuration File): " + ignoredAlways, MessageLevel.CONFIG);
        
        int forceCount = Main.loadInt(worldSpecific, pluginMain, "force.count", State.DEFAULT_FORCE_COUNT);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Count: " + forceCount, MessageLevel.CONFIG);
        
        int forcePercent = Main.loadInt(worldSpecific, pluginMain, "force.percent", State.DEFAULT_FORCE_PERCENT); 
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percent: " + forcePercent, MessageLevel.CONFIG);
        
        Set<Event.Type> monitoredActivity = new HashSet<Event.Type>();
        for (String type : Main.loadStringList(worldSpecific, pluginMain, "activity", Collections.<String>emptyList()))
            if (ActivityManager.isSupported(Event.Type.valueOf(type))) {
                monitoredActivity.add(Event.Type.valueOf(type));
            } else {
                Main.messageManager.log("Event not supported for monitoring activity: " + type, MessageLevel.WARNING);
            }
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Monitored Activity: " + monitoredActivity.toString(), MessageLevel.CONFIG);
        
        State state = new State(world, sleep, safe, inactivityLimit, ignoredAlways, forceCount, forcePercent, monitoredActivity);
        
        for (Notification.Type type : Notification.Type.values()) {
            Notification notification = Main.loadNotification(type, worldSpecific, pluginMain);
            if (notification != null) {
                Main.messageManager.log("Sleep state for [" + world.getName() + "] " + notification.description(), MessageLevel.CONFIG);
                state.addNotification(notification);
            }
        }
    }
    
    /**
     * Load notification settings from configuration file.
     * 
     * @param type notification type to load
     * @param override settings preferred over main
     * @param main base settings
     * @return notification defined according to configuration
     */
    private static Notification loadNotification(final Notification.Type type, final FileConfiguration override, final FileConfiguration main) {
        String format = Main.loadString(override, main, "notifications." + type.name() + ".format", Notification.DEFAULT_FORMAT);
        if (format == null || format.length() == 0) return null;
        
        int maxFrequency = Main.loadInt(override, main, "notifications." + type.name() + ".maxFrequency", Notification.DEFAULT_MAX_FREQUENCY);
        boolean isTimestamped = Main.loadBoolean(override, main, "notifications." + type.name() + ".timestamp", Notification.DEFAULT_TIMESTAMP);
        
        return new Notification(type, format, maxFrequency, isTimestamped);
    }
    
    /**
     * Load integer from configuration file.
     * 
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static int loadInt(final FileConfiguration override, final FileConfiguration main, final String path, final int codeDefault) {
        return override.getInt(path, main.getInt(path, codeDefault));
    }
    
    /**
     * Load list of strings from configuration file.
     * 
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    @SuppressWarnings("unchecked")
    private static List<String> loadStringList(final FileConfiguration override, final FileConfiguration main, final String path, final List<String> codeDefault) {
        return override.getList(path, main.getList(path, codeDefault));
    }
    
    /**
     * Load string from configuration file.
     * 
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static String loadString(final FileConfiguration override, final FileConfiguration main, final String path, final String codeDefault) {
        return override.getString(path, main.getString(path, codeDefault));
    }
    
    /**
     * Load boolean from configuration file.
     * 
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static boolean loadBoolean(final FileConfiguration override, final FileConfiguration main, final String path, final boolean codeDefault) {
        return override.getBoolean(path, main.getBoolean(path, codeDefault));
    }
    
    /**
     * Determine default nether world.
     * 
     * @return world that is associated as default nether
     */
    private World findDefaultNether() {
        // Find first nether world which should be the nether world the server associates by default.
        for (int i = 0; i < this.getServer().getWorlds().size(); ++i)
            if (this.getServer().getWorlds().get(i).getEnvironment().equals(Environment.NETHER))
                return this.getServer().getWorlds().get(i);
        
        return null;
    }
}