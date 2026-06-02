package com.zerohexer.paperlithography.item;

import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-component custom head textures (base64 "Value" strings from head sites). These point at
 * Mojang-hosted textures, so heads render with no client download. Persisted to heads.yml.
 */
public class HeadTextures {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<MiniBlockType, String> map = new EnumMap<>(MiniBlockType.class);

    public HeadTextures(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "heads.yml");
        load();
    }

    public void load() {
        map.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (MiniBlockType t : MiniBlockType.values()) {
            String v = y.getString(t.name().toLowerCase(Locale.ROOT));
            if (v != null && !v.isEmpty()) map.put(t, v);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<MiniBlockType, String> e : map.entrySet()) {
            y.set(e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save heads.yml: " + ex);
        }
    }

    public String get(MiniBlockType type) {
        return map.get(type);
    }

    public void set(MiniBlockType type, String base64) {
        if (base64 == null || base64.isEmpty()) {
            map.remove(type);
        } else {
            map.put(type, base64);
        }
        save();
    }
}
