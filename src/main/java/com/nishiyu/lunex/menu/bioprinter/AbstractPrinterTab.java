package com.nishiyu.lunex.menu.bioprinter;

import com.nishiyu.lunex.entity.BioMobGenerator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

import java.util.Map;

public abstract class AbstractPrinterTab {
    protected final BioPrinterScreen screen;
    protected final BioPrinterMenu menu;

    protected final int visibleRows = 7;
    protected final int rowHeight = 18;
    protected final int listWidth = 230;

    public AbstractPrinterTab(BioPrinterScreen screen, BioPrinterMenu menu) {
        this.screen = screen;
        this.menu = menu;
    }

    public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int leftPos, int topPos);

    public abstract boolean mouseClicked(double mouseX, double mouseY, int button, int leftPos, int topPos);

    public abstract boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);

    public abstract boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int leftPos, int topPos);

    public abstract boolean mouseReleased(double mouseX, double mouseY, int button);

    protected boolean hasRequiredBaseMaterials(Map<String, Integer> mats) {
        if (mats == null) return false;
        int bones = BioMobGenerator.getCount(mats, Items.BONE);
        int meats = BioMobGenerator.getCount(mats, Items.ROTTEN_FLESH)
                + BioMobGenerator.getCount(mats, Items.BEEF)
                + BioMobGenerator.getCount(mats, Items.PORKCHOP)
                + BioMobGenerator.getCount(mats, Items.CHICKEN)
                + BioMobGenerator.getCount(mats, Items.MUTTON)
                + BioMobGenerator.getCount(mats, Items.RABBIT);
        return bones >= 5 && meats >= 5;
    }
}