package com.nishiyu.lunex.machine;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainframeCore {
    public UUID machineId;
    public CoreMachineServerLuaVM vm;
    public CompoundTag persistentData = new CompoundTag();

    // クライアント同期用のログ・サジェスト
    public final List<String> clientTerminalLog = new ArrayList<>();
    public final List<String> clientSuggestions = new ArrayList<>();

    // マルチブロックによって有効化された機能とAPI
    public final Set<String> activeFeatures = new HashSet<>();
    public final Set<String> activeApis = new HashSet<>();

    // リソース管理とプロバイダー
    public final Map<String, Long> resourceCapacities = new ConcurrentHashMap<>();
    public final Map<String, Long> resourceUsages = new ConcurrentHashMap<>();
    public final Map<String, IResourceProvider> resourceProviders = new ConcurrentHashMap<>();

    // 仮想ストレージ (ネットワークルーティングなど)
    // ※もしVirtualStorageのコンストラクタがSimpleMachineBlockEntityを要求している場合は、
    // 将来的にMainframeCoreを受け取るように変更することをお勧めします。
    public VirtualStorage virtualStorage;

    private SimpleMachineBlockEntity boundEntity;
    private boolean disposed = false;

    public MainframeCore(UUID machineId) {
        this.machineId = machineId != null ? machineId : UUID.randomUUID();
    }

    public void bind(SimpleMachineBlockEntity entity) {
        this.boundEntity = entity;
        if (this.virtualStorage == null) {
            this.virtualStorage = new VirtualStorage(entity);
        }
    }

    public SimpleMachineBlockEntity getBoundEntity() {
        return this.boundEntity;
    }

    public void registerResourceProvider(String resourceName, IResourceProvider provider) {
        this.resourceProviders.put(resourceName, provider);
    }

    public long getResourceAmount(String resourceName) {
        IResourceProvider provider = this.resourceProviders.get(resourceName);
        return provider != null ? provider.getAmount() : 0;
    }

    public long getResourceCapacity(String resourceName) {
        IResourceProvider provider = this.resourceProviders.get(resourceName);
        return provider != null ? provider.getCapacity() : 0;
    }

    public String getMachineLabel() { return this.persistentData.getString("NetworkTag"); }
    public void setMachineLabel(String label) {
        this.persistentData.putString("NetworkTag", label != null ? label : "");
        if (boundEntity != null) {
            boundEntity.setChanged();
            boundEntity.sync();
        }
    }

    public String getProgramName() { return this.persistentData.getString("BootProgram"); }
    public void setProgramName(String name) {
        this.persistentData.putString("BootProgram", name != null ? name : "");
        if (boundEntity != null) boundEntity.setChanged();
    }

    public String getWorkspaceId() { return this.persistentData.getString("WorkspaceId"); }
    public void setWorkspaceId(String id) {
        this.persistentData.putString("WorkspaceId", id != null ? id : "");
        if (boundEntity != null) boundEntity.setChanged();
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void dispose() {
        if (this.disposed) return;
        this.disposed = true;
        if (this.vm != null) {
            this.vm.stopProgram();
            this.vm = null;
        }
        this.boundEntity = null;
    }
}