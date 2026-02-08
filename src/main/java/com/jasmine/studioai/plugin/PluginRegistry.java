package com.jasmine.studioai.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class PluginRegistry {

    private final List<ToolPlugin> plugins = new CopyOnWriteArrayList<>();
    private final Map<String, ToolPlugin> pluginMap = new ConcurrentHashMap<>();

    public void register(ToolPlugin plugin) {
        plugins.add(plugin);
        pluginMap.put(plugin.name(), plugin);
        log.info("Plugin registered: {} - {}", plugin.name(), plugin.description());
    }

    public void unregister(String name) {
        ToolPlugin plugin = pluginMap.remove(name);
        if (plugin != null) {
            plugins.remove(plugin);
            log.info("Plugin unregistered: {}", name);
        }
    }

    public List<ToolPlugin> listPlugins() {
        return plugins.stream().filter(ToolPlugin::enabled).toList();
    }

    public ToolPlugin getPlugin(String name) {
        return pluginMap.get(name);
    }
}
