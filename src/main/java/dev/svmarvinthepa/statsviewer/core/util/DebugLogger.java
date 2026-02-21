package dev.svmarvinthepa.statsviewer.core.util;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DebugLogger {

    private static Logger logger;
    private static boolean debug;

    public static void init(Plugin plugin) {
        logger = plugin.getLogger();
        debug = plugin.getConfig().getBoolean("debug.enabled", true);
    }

    public static void info(String msg) {
        logger.info(msg);
    }

    public static void debug(String msg) {
        if (debug) logger.info("[DEBUG] " + msg);
    }

    public static void warn(String msg) {
        logger.warning(msg);
    }

    public static void error(String msg, Throwable t) {
        logger.severe(msg);
        if (t != null) {
            t.printStackTrace();
        }
    }
}
