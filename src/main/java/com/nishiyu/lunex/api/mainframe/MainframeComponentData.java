package com.nishiyu.lunex.api.mainframe;

import java.util.HashSet;
import java.util.Set;

public class MainframeComponentData {
    private final int maxCount;
    private final Set<String> features;
    private final Set<String> apis;
    private final Set<String> placements; // ★追加

    private MainframeComponentData(int maxCount, Set<String> features, Set<String> apis, Set<String> placements) {
        this.maxCount = maxCount;
        this.features = features;
        this.apis = apis;
        this.placements = placements;
    }

    public int getMaxCount() { return maxCount; }
    public Set<String> getFeatures() { return features; }
    public Set<String> getApis() { return apis; }
    public Set<String> getPlacements() { return placements; } // ★追加

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxCount = -1;
        private final Set<String> features = new HashSet<>();
        private final Set<String> apis = new HashSet<>();
        private final Set<String> placements = new HashSet<>(); // ★追加

        public Builder maxCount(int count) { this.maxCount = count; return this; }
        public Builder addFeature(String feature) { this.features.add(feature); return this; }
        public Builder addApi(String api) { this.apis.add(api); return this; }

        // ★追加: 許可する配置箇所を指定
        public Builder addPlacement(String placement) { this.placements.add(placement); return this; }

        public MainframeComponentData build() {
            return new MainframeComponentData(maxCount, features, apis, placements);
        }
    }
}