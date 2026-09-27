package com.nishiyu.lunex.entity.traits;

import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ModTraits implements ITranslationGatherer {

    // 翻訳の自動生成に利用するためにリスト化して保持する
    public static final List<TraitDef> TRAITS = new ArrayList<>();

    private static void registerTrait(TraitDef def) {
        TRAITS.add(def);
        TraitRegistry.registerTrait(def);
    }

    public static void register() {
        // --- 基礎ステータス系 ---
        registerTrait(new TraitDef(
                "bio_core", "Bio Core", "バイオコア", "Source of life. The starting point of everything.", "生命の源。すべての起点。",
                TraitCategory.BASE_ENHANCEMENT, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.APPLE, 1)),
                builder -> {
                    builder.addHealth(10.0D);
                    builder.addAttack(2.0D);
                }
        ));

        registerTrait(new TraitDef(
                "vitality", "Vitality", "生命力", "Increases max health.", "最大体力が上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("bio_core"),
                List.of(new TraitDef.ItemRequirement(Items.BEEF, 2)),
                builder -> {
                    builder.addHealth(5.0D);
                    builder.multiplyHealth(1.2D);
                }
        ));

        registerTrait(new TraitDef(
                "hyper_metabolism", "Hyper Metabolism", "超代謝", "Greatly increases max health.", "最大体力が大きく上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("vitality"),
                List.of(new TraitDef.ItemRequirement(Items.GOLDEN_CARROT, 2)),
                builder -> {
                    builder.addHealth(10.0D);
                    builder.multiplyHealth(1.1D);
                }
        ));

        registerTrait(new TraitDef(
                "thick_fat", "Thick Fat", "厚い脂肪", "Increases max health and armor.", "最大体力とアーマー値が上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("hyper_metabolism"),
                List.of(new TraitDef.ItemRequirement(Items.PORKCHOP, 4)),
                builder -> {
                    builder.addHealth(5.0D);
                    builder.addArmor(2.0D);
                }
        ));

        registerTrait(new TraitDef(
                "immortal_cells", "Immortal Cells", "不死の細胞", "Dramatically increases max health.", "最大体力が劇的に上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("thick_fat"),
                List.of(new TraitDef.ItemRequirement(Items.TOTEM_OF_UNDYING, 1)),
                builder -> {
                    builder.addHealth(20.0D);
                    builder.multiplyHealth(1.5D);
                }
        ));

        registerTrait(new TraitDef(
                "fragile", "Fragile", "虚弱", "Decreases max health.", "最大体力が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("vitality"),
                List.of(),
                builder -> builder.multiplyHealth(0.7D)
        ));

        registerTrait(new TraitDef(
                "tough_skin", "Tough Skin", "硬い皮膚", "Increases armor.", "アーマー値が上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("bio_core"),
                List.of(new TraitDef.ItemRequirement(Items.LEATHER, 2)),
                builder -> {
                    builder.addArmor(4.0D);
                    builder.multiplyArmor(1.5D);
                }
        ));

        registerTrait(new TraitDef(
                "hardened_scales", "Hardened Scales", "硬質鱗", "Further increases armor.", "アーマー値がさらに上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("tough_skin"),
                List.of(new TraitDef.ItemRequirement(Items.PRISMARINE_SHARD, 2)),
                builder -> {
                    builder.addArmor(2.0D);
                    builder.multiplyArmor(1.2D);
                }
        ));

        registerTrait(new TraitDef(
                "shock_absorption", "Shock Absorption", "衝撃吸収", "Increases max health and armor.", "最大体力とアーマー値が同時に上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("hardened_scales"),
                List.of(new TraitDef.ItemRequirement(Items.SLIME_BALL, 4)),
                builder -> {
                    builder.addHealth(5.0D);
                    builder.addArmor(2.0D);
                }
        ));

        registerTrait(new TraitDef(
                "titanium_carapace", "Titanium Carapace", "チタンの甲殻", "Greatly increases armor and health, but slightly reduces speed.", "アーマー値と最大体力が大幅に上昇するが、移動速度が少し低下する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("shock_absorption"),
                List.of(new TraitDef.ItemRequirement(Items.IRON_BLOCK, 2)),
                builder -> {
                    builder.addArmor(6.0D);
                    builder.multiplyArmor(1.5D);
                    builder.multiplyHealth(1.2D);
                    builder.multiplySpeed(0.85D);
                }
        ));

        registerTrait(new TraitDef(
                "weak_skin", "Weak Skin", "脆い皮膚", "Decreases armor.", "アーマー値が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("tough_skin"),
                List.of(),
                builder -> builder.multiplyArmor(0.5D)
        ));

        registerTrait(new TraitDef(
                "agile", "Agile", "俊敏", "Increases movement speed.", "移動速度が上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("bio_core"),
                List.of(new TraitDef.ItemRequirement(Items.SUGAR, 2)),
                builder -> {
                    builder.addSpeed(0.1D);
                    builder.multiplySpeed(1.2D);
                }
        ));

        registerTrait(new TraitDef(
                "flexible_joints", "Flexible Joints", "柔軟な関節", "Further increases movement speed.", "移動速度がさらに上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("agile"),
                List.of(new TraitDef.ItemRequirement(Items.RABBIT_HIDE, 4)),
                builder -> {
                    builder.addSpeed(0.05D);
                    builder.multiplySpeed(1.1D);
                }
        ));

        registerTrait(new TraitDef(
                "light_weight", "Light Weight", "軽量化", "Increases speed but slightly reduces armor.", "移動速度が上がるが、アーマー値が少し低下する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("flexible_joints"),
                List.of(new TraitDef.ItemRequirement(Items.FEATHER, 4)),
                builder -> {
                    builder.multiplySpeed(1.2D);
                    builder.multiplyArmor(0.8D);
                }
        ));

        registerTrait(new TraitDef(
                "aerodynamic", "Aerodynamic", "流線型の体", "Extremely increases movement speed.", "移動速度が極めて高くなる。",
                TraitCategory.BASE_ENHANCEMENT, List.of("light_weight"),
                List.of(new TraitDef.ItemRequirement(Items.PHANTOM_MEMBRANE, 2)),
                builder -> {
                    builder.addSpeed(0.1D);
                    builder.multiplySpeed(1.3D);
                }
        ));

        registerTrait(new TraitDef(
                "lethargic", "Lethargic", "無気力", "Decreases movement speed.", "移動速度が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("agile"),
                List.of(),
                builder -> builder.multiplySpeed(0.8D)
        ));

        registerTrait(new TraitDef(
                "photosensitive", "Photosensitive", "光過敏症", "Burns when exposed to sunlight.", "日光を浴びると炎上する。",
                TraitCategory.WEAKNESS_TRAIT, List.of("weak_skin"),
                List.of()
        ));

        registerTrait(new TraitDef(
                "sturdy", "Sturdy", "頑強", "Has high durability and knockback resistance.", "高い耐久力とノックバック耐性を持つ。",
                TraitCategory.BASE_ENHANCEMENT, List.of("tough_skin"),
                List.of(new TraitDef.ItemRequirement(Items.IRON_INGOT, 2)),
                builder -> {
                    builder.addHealth(10.0D);
                    builder.multiplyHealth(1.5D);
                }
        ));

        registerTrait(new TraitDef(
                "heavy", "Heavy", "重量級", "Greatly decreases movement speed.", "体が重く、移動速度が大きく低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("sturdy"),
                List.of(),
                builder -> builder.multiplySpeed(0.6D)
        ));

        registerTrait(new TraitDef(
                "sharp_claws", "Sharp Claws", "鋭い爪", "Slightly increases attack power.", "攻撃力が少し上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("bio_core"),
                List.of(new TraitDef.ItemRequirement(Items.FLINT, 2)),
                builder -> {
                    builder.addAttack(3.0D);
                    builder.multiplyAttack(1.2D);
                }
        ));

        registerTrait(new TraitDef(
                "serrated_teeth", "Serrated Teeth", "鋸歯", "Further increases attack power.", "攻撃力がさらに上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("sharp_claws"),
                List.of(new TraitDef.ItemRequirement(Items.IRON_NUGGET, 4)),
                builder -> {
                    builder.addAttack(2.0D);
                    builder.multiplyAttack(1.1D);
                }
        ));

        registerTrait(new TraitDef(
                "muscle_fiber", "Muscle Fiber", "強化筋繊維", "Increases attack power and movement speed.", "攻撃力と移動速度がバランスよく上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("serrated_teeth"),
                List.of(new TraitDef.ItemRequirement(Items.LEATHER, 4)),
                builder -> {
                    builder.addAttack(2.0D);
                    builder.multiplySpeed(1.1D);
                }
        ));

        registerTrait(new TraitDef(
                "bone_crusher", "Bone Crusher", "骨砕き", "Greatly increases attack power but decreases speed.", "攻撃力が大きく跳ね上がるが、少し動きが重くなる。",
                TraitCategory.BASE_ENHANCEMENT, List.of("muscle_fiber"),
                List.of(new TraitDef.ItemRequirement(Items.IRON_BLOCK, 1)),
                builder -> {
                    builder.addAttack(4.0D);
                    builder.multiplyAttack(1.3D);
                    builder.multiplySpeed(0.9D);
                }
        ));

        registerTrait(new TraitDef(
                "dull_claws", "Dull Claws", "鈍い爪", "Decreases attack power.", "爪が鈍り、攻撃力が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("sharp_claws"),
                List.of(),
                builder -> builder.multiplyAttack(0.7D)
        ));

        registerTrait(new TraitDef(
                "lethal_claws", "Lethal Claws", "致命の爪", "Greatly increases attack power.", "極めて鋭い爪。攻撃力が大幅に上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("dull_claws"),
                List.of(new TraitDef.ItemRequirement(Items.DIAMOND, 2), new TraitDef.ItemRequirement(Items.IRON_SWORD, 1)),
                builder -> {
                    builder.addAttack(5.0D);
                    builder.multiplyAttack(1.5D);
                }
        ));

        registerTrait(new TraitDef(
                "regeneration", "Regeneration", "自己再生", "Gradually restores health.", "体力が自然に回復するようになる。",
                TraitCategory.COMBAT_ABILITY, List.of("vitality"),
                List.of(new TraitDef.ItemRequirement(Items.GHAST_TEAR, 1), new TraitDef.ItemRequirement(Items.GOLDEN_APPLE, 1))
        ));

        registerTrait(new TraitDef(
                "brittle_bones", "Brittle Bones", "脆い骨", "Decreases armor.", "骨がもろくなり、アーマー値が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("agile"),
                List.of(),
                builder -> builder.multiplyArmor(0.6D)
        ));

        registerTrait(new TraitDef(
                "steel_bones", "Steel Bones", "鋼鉄の骨", "Greatly increases armor and durability.", "鋼鉄の骨格。防御力と耐久力が大幅に上昇する。",
                TraitCategory.COMBAT_ABILITY, List.of("brittle_bones"),
                List.of(new TraitDef.ItemRequirement(Items.NETHERITE_SCRAP, 1), new TraitDef.ItemRequirement(Items.IRON_BLOCK, 2)),
                builder -> {
                    builder.addArmor(8.0D);
                    builder.multiplyArmor(2.0D);
                    builder.multiplyHealth(1.3D);
                }
        ));

        // --- コア・属性系 ---
        registerTrait(new TraitDef(
                "core_fragment", "Core Fragment", "コアの欠片", "Slightly increases all stats.", "微弱な属性エネルギー。全ステータスが少し上昇。",
                TraitCategory.BASE_ENHANCEMENT, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.REDSTONE, 2)),
                builder -> {
                    builder.addHealth(5.0D);
                    builder.addAttack(2.0D);
                    builder.addSpeed(0.05D);
                    builder.multiplyHealth(1.05D);
                    builder.multiplyAttack(1.05D);
                    builder.multiplySpeed(1.05D);
                }
        ));

        registerTrait(new TraitDef(
                "energy_condensation", "Energy Condensation", "エネルギー圧縮", "Slightly increases all stats.", "全ステータスがバランスよく上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("core_fragment"),
                List.of(new TraitDef.ItemRequirement(Items.REDSTONE_BLOCK, 1)),
                builder -> {
                    builder.addHealth(2.0D);
                    builder.addAttack(1.0D);
                    builder.multiplyHealth(1.05D);
                    builder.multiplyAttack(1.05D);
                    builder.multiplySpeed(1.05D);
                }
        ));

        registerTrait(new TraitDef(
                "mana_infused", "Mana Infused", "魔力付与", "Further increases all stats.", "さらに全ステータスが上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("energy_condensation"),
                List.of(new TraitDef.ItemRequirement(Items.LAPIS_BLOCK, 1)),
                builder -> {
                    builder.addHealth(2.0D);
                    builder.addArmor(1.0D);
                    builder.multiplyHealth(1.05D);
                    builder.multiplyArmor(1.05D);
                    builder.multiplyAttack(1.05D);
                    builder.multiplySpeed(1.05D);
                }
        ));

        registerTrait(new TraitDef(
                "reactor_core", "Reactor Core", "リアクターコア", "A powerful core that enhances everything. Leads to elemental cores.", "全てを強化する強力なコア。各属性コアへの派生元となる。",
                TraitCategory.BASE_ENHANCEMENT, List.of("mana_infused"),
                List.of(new TraitDef.ItemRequirement(Items.DIAMOND_BLOCK, 1)),
                builder -> {
                    builder.addHealth(5.0D);
                    builder.addAttack(2.0D);
                    builder.multiplyHealth(1.1D);
                    builder.multiplyAttack(1.1D);
                    builder.multiplySpeed(1.1D);
                    builder.multiplyArmor(1.1D);
                }
        ));

        registerTrait(new TraitDef(
                "unstable_energy", "Unstable Energy", "不安定なエネルギー", "Decreases health due to instability.", "エネルギーが不安定になり、体力が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("core_fragment"),
                List.of(),
                builder -> builder.multiplyHealth(0.8D)
        ));

        registerTrait(new TraitDef(
                "overcharged", "Overcharged", "オーバーチャージ", "Greatly increases attack power.", "過剰なエネルギーで攻撃力が大幅に上昇する。",
                TraitCategory.BASE_ENHANCEMENT, List.of("unstable_energy"),
                List.of(new TraitDef.ItemRequirement(Items.GLOWSTONE_DUST, 2)),
                builder -> {
                    builder.addAttack(4.0D);
                    builder.multiplyAttack(1.3D);
                }
        ));

        registerTrait(new TraitDef(
                "fire_core", "Fire Core", "ファイアコア", "Grants immunity to fire/lava and sets attackers on fire.", "炎やマグマを無効化し、攻撃者に発火を付与する。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.MAGMA_CREAM, 2), new TraitDef.ItemRequirement(Items.BLAZE_ROD, 2))
        ));

        registerTrait(new TraitDef(
                "ice_core", "Ice Core", "アイスコア", "Grants immunity to freezing and slows attackers.", "凍結を無効化し、攻撃者に移動低下を付与する。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.SNOWBALL, 2), new TraitDef.ItemRequirement(Items.ICE, 2))
        ));

        registerTrait(new TraitDef(
                "electric_core", "Electric Core", "エレクトリックコア", "Grants immunity to lightning and shocks nearby enemies.", "雷を無効化し、周囲の敵を感電させる。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.COPPER_INGOT, 2), new TraitDef.ItemRequirement(Items.LIGHTNING_ROD, 2))
        ));

        registerTrait(new TraitDef(
                "venom_core", "Venom Core", "ヴェノムコア", "Inflicts poison and thorns damage to attackers.", "攻撃者に毒と反射ダメージを付与する。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.SPIDER_EYE, 2), new TraitDef.ItemRequirement(Items.PUFFERFISH, 2))
        ));

        registerTrait(new TraitDef(
                "void_core", "Void Core", "ヴォイドコア", "Grants immunity to projectiles and a chance to teleport when hit.", "飛び道具を無効化し、被弾時に確率でワープする。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.ENDER_PEARL, 2))
        ));

        registerTrait(new TraitDef(
                "energy_leak", "Energy Leak", "エネルギー漏出", "Leaking energy decreases all stats.", "エネルギーが漏れ出し、全ステータスが低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("core_fragment"),
                List.of(),
                builder -> {
                    builder.multiplyHealth(0.85D);
                    builder.multiplyAttack(0.85D);
                    builder.multiplySpeed(0.85D);
                }
        ));

        registerTrait(new TraitDef(
                "wind_core", "Wind Core", "ウィンドコア", "Manipulates wind to increase movement speed.", "風を操り、移動速度が向上する。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.FEATHER, 4), new TraitDef.ItemRequirement(Items.PHANTOM_MEMBRANE, 2)),
                builder -> {
                    builder.addSpeed(0.1D);
                    builder.multiplySpeed(1.4D);
                }
        ));

        registerTrait(new TraitDef(
                "earth_core", "Earth Core", "アースコア", "Greatly increases durability but decreases speed.", "大地の力で耐久力が跳ね上がるが、動きは鈍くなる。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.OBSIDIAN, 4), new TraitDef.ItemRequirement(Items.DIAMOND_BLOCK, 1)),
                builder -> {
                    builder.addHealth(15.0D);
                    builder.addArmor(10.0D);
                    builder.multiplyHealth(1.6D);
                    builder.multiplyArmor(1.6D);
                    builder.multiplySpeed(0.8D);
                }
        ));

        registerTrait(new TraitDef(
                "light_core", "Light Core", "ライトコア", "Deals extra damage to undead mobs.", "聖なる光を放ち、アンデッドに対して追加ダメージを与える。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.GLOWSTONE, 4), new TraitDef.ItemRequirement(Items.AMETHYST_SHARD, 4))
        ));

        registerTrait(new TraitDef(
                "dark_core", "Dark Core", "ダークコア", "Inflicts blindness on attackers.", "闇の力で攻撃者に盲目を付与する。",
                TraitCategory.ELEMENTAL_CORE, List.of("reactor_core"),
                List.of(new TraitDef.ItemRequirement(Items.SCULK, 4), new TraitDef.ItemRequirement(Items.ECHO_SHARD, 1))
        ));

        // --- 変異系 ---
        registerTrait(new TraitDef(
                "amphibious", "Amphibious", "水陸両用", "Can breathe underwater and will not drown.", "水中でも呼吸ができ、溺れない。",
                TraitCategory.ENVIRONMENTAL, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.KELP, 2))
        ));

        registerTrait(new TraitDef(
                "muscle_mass", "Muscle Mass", "筋骨隆々", "Increases attack power.", "筋肉量が増加し、攻撃力が上がる。",
                TraitCategory.MORPHOLOGY, List.of("amphibious"),
                List.of(new TraitDef.ItemRequirement(Items.PORKCHOP, 2)),
                builder -> {
                    builder.addAttack(3.0D);
                    builder.multiplyAttack(1.2D);
                }
        ));

        registerTrait(new TraitDef(
                "gigantism", "Gigantism", "巨大化", "Increases size, health, and attack power.", "巨大化し、高い体力と攻撃力を持つ。",
                TraitCategory.MORPHOLOGY, List.of("muscle_mass"),
                List.of(new TraitDef.ItemRequirement(Items.SLIME_BALL, 2)),
                builder -> {
                    builder.addHealth(15.0D);
                    builder.addAttack(4.0D);
                    builder.multiplyHealth(1.2D);
                    builder.multiplyAttack(1.2D);
                    builder.setScale(1.5D);
                }
        ));

        registerTrait(new TraitDef(
                "sluggish", "Sluggish", "鈍重", "Decreases movement speed.", "動作が鈍くなり、移動速度が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("gigantism"),
                List.of(),
                builder -> builder.multiplySpeed(0.6D)
        ));

        registerTrait(new TraitDef(
                "dwarfism", "Dwarfism", "小型化", "Decreases size and increases movement speed.", "小型化し、素早く移動する。",
                TraitCategory.MORPHOLOGY, List.of("amphibious"),
                List.of(new TraitDef.ItemRequirement(Items.RABBIT, 2)),
                builder -> {
                    builder.addSpeed(0.1D);
                    builder.multiplySpeed(1.2D);
                    builder.setScale(0.75D);
                }
        ));

        registerTrait(new TraitDef(
                "feeble", "Feeble", "非力", "Decreases health and attack power.", "攻撃力と体力が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("dwarfism"),
                List.of(),
                builder -> {
                    builder.multiplyHealth(0.8D);
                    builder.multiplyAttack(0.8D);
                }
        ));

        registerTrait(new TraitDef(
                "hollow_bones", "Hollow Bones", "空洞骨", "Decreases armor.", "骨がスカスカになり、アーマーが低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("dwarfism"),
                List.of(),
                builder -> builder.multiplyArmor(0.7D)
        ));

        registerTrait(new TraitDef(
                "wings", "Wings", "翼", "Negates fall damage and grants slow falling.", "落下ダメージを無効化し、ゆっくり落下する。",
                TraitCategory.MORPHOLOGY, List.of("hollow_bones"),
                List.of(new TraitDef.ItemRequirement(Items.PHANTOM_MEMBRANE, 2))
        ));

        registerTrait(new TraitDef(
                "wall_climbing", "Wall Climbing", "壁登り", "Can climb walls like a spider.", "クモのように壁を登ることができる。",
                TraitCategory.MORPHOLOGY, List.of("feeble"),
                List.of(new TraitDef.ItemRequirement(Items.STRING, 2))
        ));

        registerTrait(new TraitDef(
                "extra_eyes", "Extra Eyes", "複眼", "Grants night vision.", "視界が広がり、暗闇でも目が見えるようになる。",
                TraitCategory.MORPHOLOGY, List.of("amphibious"),
                List.of(new TraitDef.ItemRequirement(Items.SPIDER_EYE, 4))
        ));

        registerTrait(new TraitDef(
                "blindness", "Blindness", "盲目", "Greatly reduces vision range.", "目が退化し、視界が極端に狭まる。",
                TraitCategory.WEAKNESS_TRAIT, List.of("extra_eyes"),
                List.of()
        ));

        registerTrait(new TraitDef(
                "echolocation", "Echolocation", "反響定位", "Senses nearby entities using sound.", "音で周囲を感知し、見えない敵も捕捉する。",
                TraitCategory.COMBAT_ABILITY, List.of("blindness"),
                List.of(new TraitDef.ItemRequirement(Items.SCULK_SENSOR, 1), new TraitDef.ItemRequirement(Items.ECHO_SHARD, 2))
        ));

        registerTrait(new TraitDef(
                "long_legs", "Long Legs", "長脚", "Increases step height.", "足が長くなり、高い段差を飛び越えられる。",
                TraitCategory.MORPHOLOGY, List.of("gigantism"),
                List.of(new TraitDef.ItemRequirement(Items.RABBIT_FOOT, 2), new TraitDef.ItemRequirement(Items.SLIME_BLOCK, 2))
        ));

        registerTrait(new TraitDef(
                "short_legs", "Short Legs", "短脚", "Decreases movement speed.", "足が極端に短くなり、移動速度が大きく低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("dwarfism"),
                List.of(),
                builder -> builder.multiplySpeed(0.7D)
        ));

        registerTrait(new TraitDef(
                "thorns_skin", "Thorns Skin", "茨の皮膚", "Deals thorns damage to attackers.", "皮膚に棘が生え、攻撃者にダメージを返す。",
                TraitCategory.COMBAT_ABILITY, List.of("muscle_mass"),
                List.of(new TraitDef.ItemRequirement(Items.CACTUS, 4), new TraitDef.ItemRequirement(Items.POINTED_DRIPSTONE, 4))
        ));

        // --- 特殊・実用系 ---
        registerTrait(new TraitDef(
                "buoyant", "Buoyant", "浮力", "Naturally floats upwards in water.", "水に入ると自然に浮き上がりやすくなる。",
                TraitCategory.ENVIRONMENTAL, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.KELP, 4))
        ));

        registerTrait(new TraitDef(
                "soft_body", "Soft Body", "軟体", "Decreases armor.", "体が柔らかくなり、アーマーが低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("buoyant"),
                List.of(),
                builder -> builder.multiplyArmor(0.8D)
        ));

        registerTrait(new TraitDef(
                "bouncy", "Bouncy", "弾力", "Negates fall damage and bounces upon landing.", "落下ダメージを無効化し、着地時に弾む。",
                TraitCategory.ENVIRONMENTAL, List.of("soft_body"),
                List.of(new TraitDef.ItemRequirement(Items.SLIME_BLOCK, 2))
        ));

        registerTrait(new TraitDef(
                "glowing", "Glowing", "発光", "Emits light in the dark.", "暗闇で光を放ち、周囲を照らす。",
                TraitCategory.ENVIRONMENTAL, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.GLOW_INK_SAC, 2))
        ));

        registerTrait(new TraitDef(
                "volatile", "Volatile", "揮発性", "Takes more damage from explosions.", "爆発耐性が下がり、ダメージを受けやすくなる。",
                TraitCategory.WEAKNESS_TRAIT, List.of("glowing"),
                List.of()
        ));

        registerTrait(new TraitDef(
                "explosive_death", "Explosive Death", "爆発四散", "Explodes violently upon death.", "死亡時に大爆発を起こす。",
                TraitCategory.COMBAT_ABILITY, List.of("volatile"),
                List.of(new TraitDef.ItemRequirement(Items.TNT, 2))
        ));

        registerTrait(new TraitDef(
                "photosynthesis", "Photosynthesis", "光合成", "Periodically generates seeds when exposed to sunlight.", "日光を浴びると定期的に種を生成する。",
                TraitCategory.ENVIRONMENTAL, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.OAK_LEAVES, 8))
        ));

        registerTrait(new TraitDef(
                "magnetic", "Magnetic", "磁力", "Automatically pulls nearby dropped items.", "周囲に落ちているアイテムを自動で引き寄せる。",
                TraitCategory.UTILITY, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.IRON_BLOCK, 1))
        ));

        registerTrait(new TraitDef(
                "dry_skin", "Dry Skin", "乾燥肌", "Takes more damage from fire.", "極度の乾燥肌。炎によるダメージが増加する。",
                TraitCategory.WEAKNESS_TRAIT, List.of("buoyant"),
                List.of()
        ));

        registerTrait(new TraitDef(
                "absorbent", "Absorbent", "吸水性", "Heals when touching water.", "水を吸収する体質。水に触れると体力が回復する。",
                TraitCategory.ENVIRONMENTAL, List.of("dry_skin"),
                List.of(new TraitDef.ItemRequirement(Items.SPONGE, 2), new TraitDef.ItemRequirement(Items.HEART_OF_THE_SEA, 1))
        ));

        registerTrait(new TraitDef(
                "sticky", "Sticky", "粘着質", "Slows down attackers.", "粘着質の体液で、攻撃してきた相手の動きを遅くする。",
                TraitCategory.COMBAT_ABILITY, List.of("soft_body"),
                List.of(new TraitDef.ItemRequirement(Items.SLIME_BALL, 4), new TraitDef.ItemRequirement(Items.COBWEB, 2))
        ));

        registerTrait(new TraitDef(
                "ender_blood", "Ender Blood", "エンダーの血", "Takes damage when touching water.", "エンダーの血が混ざり、水に触れるとダメージを受ける。",
                TraitCategory.WEAKNESS_TRAIT, List.of("glowing"),
                List.of()
        ));

        registerTrait(new TraitDef(
                "teleportation", "Teleportation", "瞬間移動", "Randomly teleports when hit.", "空間を歪め、被弾時にランダムにテレポートする。",
                TraitCategory.COMBAT_ABILITY, List.of("ender_blood"),
                List.of(new TraitDef.ItemRequirement(Items.CHORUS_FRUIT, 4), new TraitDef.ItemRequirement(Items.ENDER_EYE, 2))
        ));

        registerTrait(new TraitDef(
                "fragrance", "Fragrance", "芳香", "Emits a sweet scent, growing flowers and attracting bees.", "甘い香りを放ち、周囲に花を咲かせたりミツバチを引き寄せる。",
                TraitCategory.ENVIRONMENTAL, List.of("photosynthesis"),
                List.of(new TraitDef.ItemRequirement(Items.SPORE_BLOSSOM, 1), new TraitDef.ItemRequirement(Items.TALL_GRASS, 4))
        ));

        registerTrait(new TraitDef(
                "docile", "Docile", "温厚", "Decreases attack power.", "大人しくなり、攻撃力が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of(),
                List.of(),
                builder -> builder.multiplyAttack(0.8D)
        ));

        registerTrait(new TraitDef(
                "milkable", "Milkable", "乳分泌", "Can be milked with a bucket.", "バケツでミルクを採取できる。",
                TraitCategory.UTILITY, List.of("docile"),
                List.of(new TraitDef.ItemRequirement(Items.MILK_BUCKET, 1), new TraitDef.ItemRequirement(Items.BEEF, 2))
        ));

        registerTrait(new TraitDef(
                "shearable", "Shearable", "羊毛体質", "Can be sheared for wool.", "ハサミで羊毛を採取できる。",
                TraitCategory.UTILITY, List.of("docile"),
                List.of(new TraitDef.ItemRequirement(Items.WHITE_WOOL, 2), new TraitDef.ItemRequirement(Items.MUTTON, 2))
        ));

        registerTrait(new TraitDef(
                "mount", "Mount", "乗騎", "Can be ridden with a saddle.", "サドルをつけて乗ることができるようになる。",
                TraitCategory.UTILITY, List.of("docile"),
                List.of(new TraitDef.ItemRequirement(Items.SADDLE, 1))
        ));

        registerTrait(new TraitDef(
                "pack_mule", "Pack Mule", "荷運び", "Can carry items in its inventory (9 slots).", "インベントリを持ち、アイテムを運搬できる（9スロット）。",
                TraitCategory.UTILITY, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.CHEST, 1)),
                builder -> builder.addInventorySize(1)
        ));

        registerTrait(new TraitDef(
                "expanded_storage", "Expanded Storage", "拡張インベントリ", "Expands inventory space (27 slots).", "インベントリが拡張され、より多くのアイテムを運搬できる（27スロット）。",
                TraitCategory.UTILITY, List.of("pack_mule"),
                List.of(new TraitDef.ItemRequirement(Items.CHEST, 2)),
                builder -> builder.addInventorySize(2)
        ));

        registerTrait(new TraitDef(
                "massive_storage", "Massive Storage", "大容量インベントリ", "Greatly expands inventory space (54 slots).", "大容量インベントリを持ち、大量のアイテムを運搬できる（54スロット）。",
                TraitCategory.UTILITY, List.of("expanded_storage"),
                List.of(new TraitDef.ItemRequirement(Items.CHEST, 4), new TraitDef.ItemRequirement(Items.IRON_BLOCK, 1)),
                builder -> builder.addInventorySize(3)
        ));

        registerTrait(new TraitDef(
                "heavy_load", "Heavy Load", "重荷", "Decreases movement speed.", "荷物の重さで移動速度が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("expanded_storage"),
                List.of(),
                builder -> builder.multiplySpeed(0.85D)
        ));

        registerTrait(new TraitDef(
                "upright_posture", "Upright Posture", "直立姿勢", "Slightly increases movement speed.", "直立姿勢になり、移動速度が少し上がる。",
                TraitCategory.MORPHOLOGY, List.of(),
                List.of(new TraitDef.ItemRequirement(Items.BONE, 2)),
                builder -> builder.multiplySpeed(1.1D)
        ));

        registerTrait(new TraitDef(
                "weak_arms", "Weak Arms", "腕力低下", "Decreases attack power.", "腕の力が弱く、攻撃力が低下する。",
                TraitCategory.WEAKNESS_STAT, List.of("upright_posture"),
                List.of(),
                builder -> builder.multiplyAttack(0.8D)
        ));

        registerTrait(new TraitDef(
                "equipable", "Equipable", "武装可能", "Can equip armor.", "防具を装備できるようになる。",
                TraitCategory.UTILITY, List.of("weak_arms"),
                List.of(new TraitDef.ItemRequirement(Items.IRON_CHESTPLATE, 1), new TraitDef.ItemRequirement(Items.LEATHER_CHESTPLATE, 1))
        ));

        registerTrait(new TraitDef(
                "merchant", "Merchant", "交易可能", "Can trade with players.", "プレイヤーと交易できるようになる。",
                TraitCategory.UTILITY, List.of("weak_arms"),
                List.of(new TraitDef.ItemRequirement(Items.EMERALD, 2))
        ));

        registerTrait(new TraitDef(
                "noisy", "Noisy", "騒音", "Attracts nearby hostile mobs.", "常に騒音を立て、周囲の敵対モブを引き寄せてしまう。",
                TraitCategory.WEAKNESS_TRAIT, List.of("docile"),
                List.of()
        ));

        registerTrait(new TraitDef(
                "luminous_lure", "Luminous Lure", "発光ルアー", "Attracts friendly mobs with beautiful light and sound.", "美しい光と音で、友好的なモブを引き寄せる。",
                TraitCategory.UTILITY, List.of("noisy"),
                List.of(new TraitDef.ItemRequirement(Items.GLOW_BERRIES, 4), new TraitDef.ItemRequirement(Items.SEA_LANTERN, 1))
        ));

        registerTrait(new TraitDef(
                "shapeshifter", "Shapeshifter", "変幻自在", "Can change its appearance freely. Unlocks the Model tab.", "自身の姿を自由に変えることができるようになる。専用のModelタブが解放される。",
                TraitCategory.UTILITY, List.of("bio_core"),
                List.of(new TraitDef.ItemRequirement(Items.SLIME_BALL, 4), new TraitDef.ItemRequirement(Items.MAGMA_CREAM, 4))
        ));
    }

    @Override
    public void gatherTranslations(AutoLanguageProvider provider, String locale) {
        // 登録されたすべてのTraitをループで回し、名前と説明を自動的に言語ファイルに書き込む
        for (TraitDef trait : TRAITS) {
            provider.addTranslation("trait.lunex." + trait.key() + ".name", trait.englishName(), trait.japaneseName());
            provider.addTranslation("trait.lunex." + trait.key() + ".desc", trait.englishDescription(), trait.japaneseDescription());
        }
    }
}