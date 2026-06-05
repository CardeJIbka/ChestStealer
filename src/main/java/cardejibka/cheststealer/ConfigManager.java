package cardejibka.cheststealer;

import java.io.*;
import java.util.*;

public class ConfigManager {
    private static final File CONFIG_FILE = new File("config/cheststealer.properties");
    private static final Properties props = new Properties();

    public static int itemsPerTick = 3;
    public static boolean isWhitelist = false;
    public static Set<String> itemFilter = new HashSet<>();

    public static void load() {
        try {
            if (!CONFIG_FILE.exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
                String comments = "ChestStealer Configuration\n" +
                        "itemsPerTick: Number of items to steal per tick (higher = faster, but riskier for anti-cheat)\n" +
                        "mode: 'blacklist' (ignore items below) or 'whitelist' (steal only items below)\n" +
                        "items: Comma-separated list of item IDs (e.g., minecraft:diamond,minecraft:gold_ingot)";

                props.setProperty("itemsPerTick", "3");
                props.setProperty("mode", "blacklist");
                props.setProperty("items", "");
                props.store(new FileWriter(CONFIG_FILE), comments);
            } else {
                props.load(new FileReader(CONFIG_FILE));
                itemsPerTick = Integer.parseInt(props.getProperty("itemsPerTick", "3"));
                isWhitelist = "whitelist".equalsIgnoreCase(props.getProperty("mode"));

                String items = props.getProperty("items", "");
                if (!items.isEmpty()) {
                    itemFilter.addAll(Arrays.asList(items.split(",")));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isItemAllowed(String itemId) {
        boolean contains = itemFilter.contains(itemId);
        return isWhitelist ? contains : !contains;
    }
}