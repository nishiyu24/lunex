package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.chemistry.model.Compound;
import com.nishiyu.lunex.chemistry.model.Molecule;

import java.util.ArrayList;
import java.util.List;

public class ReactionManager {
    private static final List<ChemicalReaction> reactions = new ArrayList<>();

    public static void register(ChemicalReaction reaction) {
        reactions.add(reaction);
    }

    public static List<ChemicalReaction> getReactions() {
        return reactions;
    }

    /**
     * 組成と環境条件（温度）を与え、適用可能な反応を1つ実行する
     */
    public static boolean tryProcessReaction(Molecule molecule, int temperature) {
        for (ChemicalReaction reaction : reactions) {
            if (reaction.apply(molecule, temperature)) {
                return true; // 反応成功
            }
        }
        return false;
    }

    /**
     * 現実の代表的な化学反応の登録
     */
    public static void registerDefaultReactions() {
        // 1. 赤鉄鉱の高炉還元製錬: Fe2O3 + 3CO -> 2Fe + 3CO2 (1000℃以上)
        register(new ChemicalReaction("blast_furnace_iron", "赤鉄鉱の還元製錬", 1000)
                .addReactant(Compound.HEMATITE, 1)
                .addReactant(Compound.CARBON_MONOXIDE, 3)
                .addProduct(Compound.IRON_PURE, 2)
                .addProduct(Compound.CARBON_DIOXIDE, 3));

        // 2. 石灰石の熱分解: CaCO3 -> CaO + CO2 (800℃以上)
        register(new ChemicalReaction("calcination_limestone", "石灰石のか焼", 800)
                .addReactant(Compound.CALCITE, 1)
                .addProduct(Compound.QUICKLIME, 1)
                .addProduct(Compound.CARBON_DIOXIDE, 1));

        // 3. 生石灰の水和反応: CaO + H2O -> Ca(OH)2 (常温で激しく発熱)
        register(new ChemicalReaction("slaking_lime", "生石灰の消和反応", 0)
                .addReactant(Compound.QUICKLIME, 1)
                .addReactant(Compound.WATER, 1)
                .addProduct(Compound.SLAKED_LIME, 1));

        // 4. 水の電気分解 / 高温熱分解: 2H2O -> 2H2 + O2
        register(new ChemicalReaction("water_splitting", "水分解反応", 1500)
                .addReactant(Compound.WATER, 2)
                .addProduct(Compound.HYDROGEN_GAS, 2)
                .addProduct(Compound.OXYGEN_GAS, 1));

        // 5. 木炭と二酸化炭素の還元(ブードワ平衡): C + CO2 -> 2CO (700℃以上)
        register(new ChemicalReaction("boudouard_reaction", "一酸化炭素生成", 700)
                .addReactant(Compound.CARBON_PURE, 1)
                .addReactant(Compound.CARBON_DIOXIDE, 1)
                .addProduct(Compound.CARBON_MONOXIDE, 2));
    }
}