package com.jasmine.studioai.plugin;

public interface ToolPlugin {
    String name();
    String description();
    Object execute(String input);
    default boolean enabled() { return true; }
}
