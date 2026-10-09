package com.nishiyu.lunex.chemistry.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class Molecule {
    private final Map<Compound, Integer> compounds = new EnumMap<>(Compound.class);
    private final Map<Element, Integer> standaloneElements = new EnumMap<>(Element.class);

    public Molecule() {}

    public Molecule(Molecule other) {
        this.compounds.putAll(other.compounds);
        this.standaloneElements.putAll(other.standaloneElements);
    }

    public void addCompound(Compound compound, int count) {
        if (count <= 0) return;
        compounds.put(compound, compounds.getOrDefault(compound, 0) + count);
    }

    public boolean removeCompound(Compound compound, int count) {
        int current = compounds.getOrDefault(compound, 0);
        if (current < count) return false;
        if (current == count) {
            compounds.remove(compound);
        } else {
            compounds.put(compound, current - count);
        }
        return true;
    }

    public int getCompoundAmount(Compound compound) {
        return compounds.getOrDefault(compound, 0);
    }

    public void addElement(Element element, int count) {
        if (count <= 0) return;
        standaloneElements.put(element, standaloneElements.getOrDefault(element, 0) + count);
    }

    public void addMolecule(Molecule other, int multiplier) {
        if (multiplier <= 0) return;
        for (Map.Entry<Compound, Integer> entry : other.compounds.entrySet()) {
            this.addCompound(entry.getKey(), entry.getValue() * multiplier);
        }
        for (Map.Entry<Element, Integer> entry : other.standaloneElements.entrySet()) {
            this.addElement(entry.getKey(), entry.getValue() * multiplier);
        }
    }

    public void divideBy(int divisor) {
        if (divisor <= 1) return;
        compounds.replaceAll((k, v) -> v / divisor);
        compounds.values().removeIf(v -> v <= 0);

        standaloneElements.replaceAll((k, v) -> v / divisor);
        standaloneElements.values().removeIf(v -> v <= 0);
    }

    public boolean isEmpty() {
        return compounds.isEmpty() && standaloneElements.isEmpty();
    }

    public Map<Compound, Integer> getCompounds() {
        return Collections.unmodifiableMap(compounds);
    }

    public Map<Element, Integer> getElements() {
        Map<Element, Integer> total = new EnumMap<>(Element.class);

        for (Map.Entry<Compound, Integer> entry : compounds.entrySet()) {
            Compound compound = entry.getKey();
            int count = entry.getValue();
            for (Map.Entry<Element, Integer> fEntry : compound.getFormula().entrySet()) {
                total.put(fEntry.getKey(), total.getOrDefault(fEntry.getKey(), 0) + fEntry.getValue() * count);
            }
        }

        for (Map.Entry<Element, Integer> entry : standaloneElements.entrySet()) {
            total.put(entry.getKey(), total.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }

        return total;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Molecule other)) return false;
        return this.compounds.equals(other.compounds) && this.standaloneElements.equals(other.standaloneElements);
    }

    @Override
    public String toString() {
        return "Compounds: " + compounds + " | Elements: " + standaloneElements;
    }
}