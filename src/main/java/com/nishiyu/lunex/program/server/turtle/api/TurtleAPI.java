package com.nishiyu.lunex.program.server.turtle.api;

import com.nishiyu.lunex.block.AdvancedMachineBlock;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class TurtleAPI {
    private final ServerLuaVM vm;

    public TurtleAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    @LuaFunction(
            value = "このマシンがタートル（移動可能なロボット）かどうかを判定します。",
            en = "Determines whether this machine is a Turtle (mobile robot).",
            args = {},
            rets = {"bool:isTurtle"},
            isAsync = false
    )
    public boolean isTurtle() {
        return vm.hardware instanceof TurtleBotBlockEntity;
    }

    @LuaFunction(
            value = "タートルを向いている方向に1マス前進させます。",
            en = "Moves the Turtle forward by 1 block in the direction it is facing.",
            args = {},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean forward() {
        return executeMove(Direction.NORTH, true);
    }

    @LuaFunction(
            value = "タートルを1マス後退させます。",
            en = "Moves the Turtle backward by 1 block.",
            args = {},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean back() {
        return executeMove(Direction.SOUTH, true);
    }

    @LuaFunction(
            value = "タートルを1マス上昇させます。",
            en = "Moves the Turtle up by 1 block.",
            args = {},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean up() {
        return executeMove(Direction.UP, false);
    }

    @LuaFunction(
            value = "タートルを1マス下降させます。",
            en = "Moves the Turtle down by 1 block.",
            args = {},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean down() {
        return executeMove(Direction.DOWN, false);
    }

    @LuaFunction(
            value = "タートルを左に90度回転させます。",
            en = "Turns the Turtle 90 degrees to the left.",
            args = {},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean turnLeft() {
        return executeTurn(true);
    }

    @LuaFunction(
            value = "タートルを右に90度回転させます。",
            en = "Turns the Turtle 90 degrees to the right.",
            args = {},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean turnRight() {
        return executeTurn(false);
    }

    @LuaFunction(
            value = "指定した方向のブロックをツールで破壊し、回収スロットにアイテムを収納します。",
            en = "Breaks the block in the specified direction with the tool and stores the item in the recovery slot.",
            args = {"num:toolSlot", "num:recoverySlot", "str:direction"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean dig(int toolSlot, int recoverySlot, String dirStr) {
        if (!isTurtle()) return false;
        if (!vm.hardware.consumeActionEnergy(150)) return false;

        long baseSleepTime = vm.executeInMainThreadSync(() -> {
            TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
            if (turtle.isRemoved() || turtle.hasPendingMove || turtle.hasPendingTurn) return -1L;

            Direction dir = parseDirection(turtle, dirStr);
            if (dir == null) return -1L;

            BlockPos targetPos = turtle.getBlockPos().relative(dir);
            Level level = turtle.getLevel();
            BlockState state = level.getBlockState(targetPos);

            if (state.isAir()) return -1L;

            float hardness = state.getDestroySpeed(level, targetPos);
            if (hardness < 0) return -1L;

            ItemStack tool = turtle.itemHandler.getStackInSlot(toolSlot);
            boolean isCorrect = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
            if (!isCorrect) return -1L;

            float toolSpeed = tool.getDestroySpeed(state);
            int baseTicks = (int) Math.ceil((hardness * 30.0f) / toolSpeed);

            return (long) baseTicks * 50L;
        }, 0, false);

        if (baseSleepTime < 0) return false;

        vm.applyDelay((int) baseSleepTime, false);

        if (!vm.isRunning) return false;

        return vm.executeInMainThreadSync(() -> {
            TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
            if (turtle.isRemoved()) return false;

            Direction dir = parseDirection(turtle, dirStr);
            BlockPos targetPos = turtle.getBlockPos().relative(dir);
            Level level = turtle.getLevel();
            BlockState state = level.getBlockState(targetPos);

            if (state.isAir()) return false;

            ItemStack tool = turtle.itemHandler.getStackInSlot(toolSlot);

            if (level instanceof ServerLevel serverLevel) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, targetPos, level.getBlockEntity(targetPos), null, tool);

                level.removeBlock(targetPos, false);
                SoundType soundtype = state.getSoundType(level, targetPos, null);
                level.playSound(null, targetPos, soundtype.getBreakSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);

                if (tool.isDamageableItem()) {
                    tool.hurtAndBreak(1, serverLevel, null, (item) -> {
                    });
                }

                for (ItemStack drop : drops) {
                    ItemStack remainder = turtle.itemHandler.insertItem(recoverySlot, drop, false);
                    if (!remainder.isEmpty()) {
                        Containers.dropItemStack(level, targetPos.getX(), targetPos.getY(), targetPos.getZ(), remainder);
                    }
                }
                return true;
            }
            return false;
        }, 0, false);
    }

    @LuaFunction(
            value = "指定した方向へ、特定のスロットに入っているブロックを設置します。",
            en = "Places the block from the specified slot in the given direction.",
            args = {"num:slot", "str:direction"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean place(int slot, String dirStr) {
        if (!isTurtle()) return false;
        if (!vm.hardware.consumeActionEnergy(50)) return false;

        return vm.executeInMainThreadSync(() -> {
            TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
            if (turtle.isRemoved() || turtle.hasPendingMove || turtle.hasPendingTurn) return false;

            Direction dir = parseDirection(turtle, dirStr);
            if (dir == null) return false;

            BlockPos targetPos = turtle.getBlockPos().relative(dir);
            Level level = turtle.getLevel();
            BlockState targetState = level.getBlockState(targetPos);

            if (!targetState.canBeReplaced()) return false;

            ItemStack stack = turtle.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;

            BlockState newState = blockItem.getBlock().defaultBlockState();
            level.setBlockAndUpdate(targetPos, newState);
            stack.shrink(1);

            SoundType soundtype = newState.getSoundType(level, targetPos, null);
            level.playSound(null, targetPos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);

            return true;
        }, 500, false);
    }

    private Direction parseDirection(TurtleBotBlockEntity turtle, String str) {
        if (str == null) return null;
        str = str.toLowerCase();
        if (str.equals("up")) return Direction.UP;
        if (str.equals("down")) return Direction.DOWN;

        Direction facing = turtle.getBlockState().getValue(AdvancedMachineBlock.FACING);
        return switch (str) {
            case "front", "forward" -> facing;
            case "back", "backward" -> facing.getOpposite();
            case "left" -> facing.getCounterClockWise();
            case "right" -> facing.getClockWise();
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    private boolean executeMove(Direction moveDir, boolean isRelative) {
        if (!isTurtle()) return false;
        if (!vm.hardware.consumeActionEnergy(200)) return false;

        long animMs = vm.executeInMainThreadSync(() -> {
            TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
            if (turtle.isRemoved() || turtle.hasPendingMove || turtle.hasPendingTurn) return -1L;

            Direction facing = turtle.getBlockState().getValue(AdvancedMachineBlock.FACING);
            Direction actualDir = moveDir;

            if (isRelative) {
                if (moveDir == Direction.NORTH) actualDir = facing;
                else if (moveDir == Direction.SOUTH) actualDir = facing.getOpposite();
            }

            BlockPos targetPos = turtle.getBlockPos().relative(actualDir);
            if (!turtle.getLevel().getBlockState(targetPos).canBeReplaced()) return -1L;

            turtle.pendingMoveTarget = targetPos;
            turtle.hasPendingMove = true;
            turtle.startAnimation(actualDir.getStepX(), actualDir.getStepY(), actualDir.getStepZ(), 0);
            turtle.getLevel().sendBlockUpdated(turtle.getBlockPos(), turtle.getBlockState(), turtle.getBlockState(), 3);

            return (long) turtle.getAnimationDurationMs();
        }, 0, false);

        if (animMs > 0) {
            vm.applyDelay((int) animMs, false);

            if (!vm.isRunning) return false;

            vm.executeInMainThreadSync(() -> {
                TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
                if (!turtle.isRemoved()) {
                    turtle.executePendingActions(turtle.getLevel(), turtle.getBlockPos());
                }
                return null;
            }, 0, false);
            return true;
        }
        return false;
    }

    private boolean executeTurn(boolean isLeft) {
        if (!isTurtle()) return false;
        if (!vm.hardware.consumeActionEnergy(50)) return false;

        long animMs = vm.executeInMainThreadSync(() -> {
            TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
            if (turtle.isRemoved() || turtle.hasPendingMove || turtle.hasPendingTurn) return -1L;

            Direction facing = turtle.getBlockState().getValue(AdvancedMachineBlock.FACING);
            Direction newFacing = isLeft ? facing.getCounterClockWise() : facing.getClockWise();

            turtle.pendingTurnFacing = newFacing;
            turtle.hasPendingTurn = true;

            turtle.startAnimation(0, 0, 0, isLeft ? 90 : -90);
            turtle.getLevel().sendBlockUpdated(turtle.getBlockPos(), turtle.getBlockState(), turtle.getBlockState(), 3);

            return (long) turtle.getAnimationDurationMs();
        }, 0, false);

        if (animMs > 0) {
            vm.applyDelay((int) animMs, false);

            if (!vm.isRunning) return false;

            vm.executeInMainThreadSync(() -> {
                TurtleBotBlockEntity turtle = (TurtleBotBlockEntity) vm.hardware;
                if (!turtle.isRemoved()) {
                    turtle.executePendingActions(turtle.getLevel(), turtle.getBlockPos());
                }
                return null;
            }, 0, false);
            return true;
        }
        return false;
    }
}