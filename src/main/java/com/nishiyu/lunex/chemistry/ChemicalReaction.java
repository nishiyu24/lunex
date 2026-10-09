package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.chemistry.model.Compound;
import com.nishiyu.lunex.chemistry.model.Molecule;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 化学反応の定義 (Reactants -> Products)
 */
public class ChemicalReaction {
    private final String id;
    private final String displayName;
    private final Map<Compound, Integer> reactants = new EnumMap<>(Compound.class);
    private final Map<Compound, Integer> products = new EnumMap<>(Compound.class);
    private final int requiredTemperature; // 例: 必要温度(℃) 0なら常温

    public ChemicalReaction(String id, String displayName, int requiredTemperature) {
        this.id = id;
        this.displayName = displayName;
        this.requiredTemperature = requiredTemperature;
    }

    public ChemicalReaction addReactant(Compound compound, int count) {
        reactants.put(compound, count);
        return this;
    }

    public ChemicalReaction addProduct(Compound compound, int count) {
        products.put(compound, count);
        return this;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<Compound, Integer> getReactants() {
        return Collections.unmodifiableMap(reactants);
    }

    public Map<Compound, Integer> getProducts() {
        return Collections.unmodifiableMap(products);
    }

    public int getRequiredTemperature() {
        return requiredTemperature;
    }

    /**
     * 与えられた組成がこの反応を起こせるか判定
     */
    public boolean matches(Molecule molecule, int currentTemperature) {
        if (currentTemperature < requiredTemperature) {
            return false;
        }
        for (Map.Entry<Compound, Integer> entry : reactants.entrySet()) {
            if (molecule.getCompoundAmount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 組成に対して反応を1サイクル適用する
     * @return 反応が実行されたら true
     */
    public boolean apply(Molecule molecule, int currentTemperature) {
        if (!matches(molecule, currentTemperature)) {
            return false;
        }

        // 反応物を消費
        for (Map.Entry<Compound, Integer> entry : reactants.entrySet()) {
            molecule.removeCompound(entry.getKey(), entry.getValue());
        }

        // 生成物を付与
        for (Map.Entry<Compound, Integer> entry : products.entrySet()) {
            molecule.addCompound(entry.getKey(), entry.getValue());
        }

        return true;
    }
}