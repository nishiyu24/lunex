package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.enderframelib.client.gui.AbstractWebContainerScreen;
import com.nishiyu.enderframelib.client.gui.AbstractWebScreen;
import com.nishiyu.enderframelib.core.model.UIElement;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class TurtleBotScreen extends AbstractWebContainerScreen<TurtleBotMenu> {

    @Translatable(en = "No Program", ja = "プログラムなし")
    public static final String KEY_NO_PROGRAM = "gui.lunex.machine.no_program";
    @Translatable(en = "Boot Program: ", ja = "起動プログラム: ")
    public static final String KEY_BOOT_PROGRAM = "gui.lunex.machine.boot_program";
    @Translatable(en = "Energy: %s / %s", ja = "エネルギー: %s / %s")
    public static final String KEY_ENERGY = "tooltip.lunex.energy";

    private static final int VIRTUAL_WIDTH = 176;
    private static final int VIRTUAL_HEIGHT = 222;
    private static final ResourceLocation INV_HTML_LOC = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "ui/turtle_inventory.html");

    private boolean cachedRunning = false;
    private int cachedEnergy = -1;
    private String cachedProgram = "";

    public TurtleBotScreen(TurtleBotMenu menu, Inventory playerInventory, Component title) {
        // スロットと1:1で重ねるため 176x222 の固定サイズモードで初期化
        super(menu, playerInventory, title,
                "turtle_inv_" + (menu.getBlockEntity() != null ? menu.getBlockEntity().getBlockPos().asLong() : 0L),
                VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
    }

    @Override
    protected String buildInitialHtml() {
        String rawHtml = AbstractWebScreen.loadHtmlResource(INV_HTML_LOC);
        return rawHtml
                .replace("{{TITLE}}", this.title.getString())
                .replace("{{SLOTS_HTML}}", generateSlotsHtml());
    }

    @Override
    protected void init() {
        super.init();
        this.cachedEnergy = -1;
        this.cachedProgram = "";
        syncToDom(true);
    }

    private String generateSlotsHtml() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int x = 52 + col * 18;
                int y = 50 + row * 18;
                sb.append("<div class='slot-box' style='left:").append(x).append("px; top:").append(y).append("px;'></div>");
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = 7 + col * 18;
                int y = 139 + row * 18;
                sb.append("<div class='slot-box' style='left:").append(x).append("px; top:").append(y).append("px;'></div>");
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = 7 + col * 18;
            int y = 197;
            sb.append("<div class='slot-box' style='left:").append(x).append("px; top:").append(y).append("px;'></div>");
        }
        return sb.toString();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        syncToDom(false);
    }

    private void syncToDom(boolean force) {
        boolean running = this.menu.isRunning();
        if (force || running != cachedRunning) {
            cachedRunning = running;
            setElementText("btn_run", running ? "■" : "▶");
            toggleClass("btn_run", "btn-stop", running);
            toggleClass("btn_run", "btn-start", !running);
        }

        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();
        if (force || energy != cachedEnergy) {
            cachedEnergy = energy;
            setAttribute("energy_bar", "value", String.valueOf(energy));
            setAttribute("energy_bar", "max", String.valueOf(maxEnergy));
            String tip = Component.translatable(KEY_ENERGY, energy, maxEnergy).getString();
            setAttribute("energy_bar", "title", tip);
        }

        String programName = null;
        if (this.menu.getBlockEntity() != null) {
            programName = this.menu.getBlockEntity().getCore().programName;
        }
        if (programName == null || programName.isEmpty()) {
            programName = Component.translatable(KEY_NO_PROGRAM).getString();
        }
        String fullProgText = Component.translatable(KEY_BOOT_PROGRAM).getString() + programName;
        if (force || !fullProgText.equals(cachedProgram)) {
            cachedProgram = fullProgText;
            setElementText("lbl_program", fullProgText);
        }
    }

    @Override
    protected void onWebAction(String action, UIElement clickedElement, int mouseButton) {
        if (mouseButton == 0 && "toggle_run".equals(action) && this.menu.getBlockEntity() != null) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", this.menu.getBlockEntity().getBlockPos().asLong());
            if (this.menu.isRunning()) {
                tag.putString("command", "stop");
                tag.putString("arg", "");
            } else {
                String programName = this.menu.getBlockEntity().getCore().programName;
                tag.putString("command", "boot");
                tag.putString("arg", programName != null ? programName : "");
            }
            PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "machine_command", tag));
        }
    }
}