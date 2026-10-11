package com.nishiyu.lunex.chemistry.model;

import java.util.Arrays;

public class ItemDNA {
    public static final int DNA_LENGTH = 6;
    private final InkGene[] sequence;

    public ItemDNA() {
        this.sequence = new InkGene[DNA_LENGTH];
        Arrays.fill(this.sequence, InkGene.EMPTY);
    }

    public ItemDNA(ItemDNA other) {
        this.sequence = Arrays.copyOf(other.sequence, DNA_LENGTH);
    }

    public void setGene(int index, BaseInk ink, InkState state) {
        if (index >= 0 && index < DNA_LENGTH) {
            this.sequence[index] = new InkGene(ink, state);
        }
    }

    public void setGene(int index, InkGene gene) {
        if (index >= 0 && index < DNA_LENGTH) {
            this.sequence[index] = gene != null ? gene : InkGene.EMPTY;
        }
    }

    public InkGene getGene(int index) {
        if (index >= 0 && index < DNA_LENGTH) {
            return sequence[index];
        }
        return InkGene.EMPTY;
    }

    public InkGene[] getSequence() {
        return Arrays.copyOf(sequence, DNA_LENGTH);
    }

    public boolean isEmpty() {
        for (InkGene gene : sequence) {
            if (!gene.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemDNA other)) return false;
        return Arrays.equals(this.sequence, other.sequence);
    }

    @Override
    public String toString() {
        return Arrays.toString(sequence);
    }
}