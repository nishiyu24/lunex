package com.nishiyu.lunex.machine.turtle;

import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.program.server.turtle.TurtleServerLuaVM;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TurtleCore {
    public final UUID machineId;
    public TurtleServerLuaVM vm;
    public final TurtleTaskManager tasks = new TurtleTaskManager();

    // ▼ 修正: 配置時からフル充電状態にする
    public int energy = 0;

    public final ItemStackHandler itemHandler;
    public CompoundTag persistentData = new CompoundTag();
    public String workspaceId = "";
    public String programName = "";
    public boolean isPrivateMode = false;
    public boolean wakeOnRedstone = false;
    public boolean debugChat = true;
    public UUID ownerUUID = null;
    public List<String> installedPrograms = new ArrayList<>();
    public int[] lastItemCounts = new int[16];
    public boolean wasPowered = false;
    public Map<Direction, Integer> redstoneOutputs = new ConcurrentHashMap<>();

    private TurtleBotBlockEntity boundEntity;
    private boolean disposed = false;
    public boolean wasRunning = false;
    public boolean isRelocating = false;

    public TurtleCore(UUID machineId) {
        this.machineId = machineId != null ? machineId : UUID.randomUUID();
        this.vm = new TurtleServerLuaVM(this);
        this.itemHandler = new ItemStackHandler(16) {
            @Override
            protected void onContentsChanged(int slot) {
                int currentCount = this.getStackInSlot(slot).getCount();
                if (currentCount > lastItemCounts[slot]) {
                    ItemStack stack = this.getStackInSlot(slot);
                    String itemName = stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (vm != null && !disposed) vm.triggerEvent("on_item_in", slot, itemName, currentCount);
                }
                lastItemCounts[slot] = currentCount;
                if (boundEntity != null) boundEntity.setChanged();
            }
        };
    }

    public void bind(TurtleBotBlockEntity entity) {
        this.boundEntity = entity;
    }

    public TurtleBotBlockEntity getBoundEntity() {
        return this.boundEntity;
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