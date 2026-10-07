package com.nishiyu.lunex.chemistry;

import java.util.EnumMap;
import java.util.Map;

public class Molecule {
    private final Map<Element, Integer> elements = new EnumMap<>(Element.class);

    public Molecule() {}

    public Molecule(Molecule other) {
        this.elements.putAll(other.elements);
    }

    public void addElement(Element element, int count) {
        elements.put(element, elements.getOrDefault(element, 0) + count);
    }

    public void addMolecule(Molecule other, int multiplier) {
        for (Map.Entry<Element, Integer> entry : other.elements.entrySet()) {
            this.addElement(entry.getKey(), entry.getValue() * multiplier);
        }
    }

    public void divideBy(int divisor) {
        if (divisor <= 1) return;
        Map<Element, Integer> newElements = new EnumMap<>(Element.class);
        for (Map.Entry<Element, Integer> entry : elements.entrySet()) {
            int newAmount = entry.getValue() / divisor;
            if (newAmount > 0) {
                newElements.put(entry.getKey(), newAmount);
            }
        }
        elements.clear();
        elements.putAll(newElements);
    }

    public void degrade(double rate) {
        Map<Element, Integer> newElements = new EnumMap<>(Element.class);
        for (Map.Entry<Element, Integer> entry : elements.entrySet()) {
            int newAmount = (int) (entry.getValue() * rate);
            if (newAmount > 0) {
                newElements.put(entry.getKey(), newAmount);
            }
        }
        elements.clear();
        elements.putAll(newElements);
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    // --- 追加: ツールチップ描画用のゲッター ---
    public Map<Element, Integer> getElements() {
        return elements;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Molecule)) return false;
        Molecule other = (Molecule) obj;
        return this.elements.equals(other.elements);
    }

    @Override
    public String toString() {
        if (elements.isEmpty()) return "Empty";
        StringBuilder sb = new StringBuilder();
        elements.forEach((k, v) -> sb.append(k.name()).append(":").append(v).append(" "));
        return sb.toString().trim();
    }
}