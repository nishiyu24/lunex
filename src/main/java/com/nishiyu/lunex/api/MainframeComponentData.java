package com.nishiyu.lunex.api;

import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.api.mainframe.IMainframeExtension;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class MainframeComponentData {
    private final int maxCount;
    private final Set<String> features;
    private final Set<String> apis;
    private final Set<String> placements;
    private final Map<String, Long> resourceCapacities;

    private final Set<String> blockTabs;
    private final Set<String> bottomTabs;

    // ★ サーバー側の処理
    private final IMainframeActionProvider<?> actionProvider;
    private final Map<ResourceLocation, Supplier<IMainframeExtension>> extensions;
    private final List<IMainframeAPI> luaAPIs;

    private MainframeComponentData(int maxCount, Set<String> features, Set<String> apis, Set<String> placements, Map<String, Long> resourceCapacities, Set<String> blockTabs, Set<String> bottomTabs, IMainframeActionProvider<?> actionProvider, Map<ResourceLocation, Supplier<IMainframeExtension>> extensions, List<IMainframeAPI> luaAPIs) {
        this.maxCount = maxCount;
        this.features = features;
        this.apis = apis;
        this.placements = placements;
        this.resourceCapacities = resourceCapacities;
        this.blockTabs = blockTabs;
        this.bottomTabs = bottomTabs;
        this.actionProvider = actionProvider;
        this.extensions = extensions;
        this.luaAPIs = luaAPIs;
    }

    public int getMaxCount() { return maxCount; }
    public Set<String> getFeatures() { return features; }
    public Set<String> getApis() { return apis; }
    public Set<String> getPlacements() { return placements; }
    public Map<String, Long> getResourceCapacities() { return resourceCapacities; }
    public Set<String> getBlockTabs() { return blockTabs; }
    public Set<String> getBottomTabs() { return bottomTabs; }
    public IMainframeActionProvider<?> getActionProvider() { return actionProvider; }
    public Map<ResourceLocation, Supplier<IMainframeExtension>> getExtensions() { return extensions; }
    public List<IMainframeAPI> getLuaAPIs() { return luaAPIs; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxCount = -1;
        private final Set<String> features = new HashSet<>();
        private final Set<String> apis = new HashSet<>();
        private final Set<String> placements = new HashSet<>();
        private final Map<String, Long> resourceCapacities = new HashMap<>();

        private final Set<String> blockTabs = new HashSet<>();
        private final Set<String> bottomTabs = new HashSet<>();
        private IMainframeActionProvider<?> actionProvider = null;
        private final Map<ResourceLocation, Supplier<IMainframeExtension>> extensions = new HashMap<>();
        private final List<IMainframeAPI> luaAPIs = new ArrayList<>();

        public Builder maxCount(int count) { this.maxCount = count; return this; }
        public Builder addFeature(String feature) { this.features.add(feature); return this; }
        public Builder addApi(String api) { this.apis.add(api); return this; }
        public Builder addPlacement(String placement) { this.placements.add(placement); return this; }
        public Builder addResourceCapacity(String type, long amount) { this.resourceCapacities.put(type, amount); return this; }
        public Builder addBlockTab(String className) { this.blockTabs.add(className); return this; }
        public Builder addBottomTab(String className) { this.bottomTabs.add(className); return this; }

        // ★ 各種プロバイダ・拡張の登録メソッド群
        public Builder setActionProvider(IMainframeActionProvider<?> provider) { this.actionProvider = provider; return this; }
        public Builder addExtension(ResourceLocation id, Supplier<IMainframeExtension> factory) { this.extensions.put(id, factory); return this; }
        public Builder addLuaAPI(IMainframeAPI api) { this.luaAPIs.add(api); return this; }

        public MainframeComponentData build() {
            return new MainframeComponentData(maxCount, features, apis, placements, resourceCapacities, blockTabs, bottomTabs, actionProvider, extensions, luaAPIs);
        }
    }
}