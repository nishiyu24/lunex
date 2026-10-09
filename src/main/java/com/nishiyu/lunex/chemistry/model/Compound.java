package com.nishiyu.lunex.chemistry.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 現実の化学・鉱物学・生化学に準拠した化合物・鉱物・純物質の定義
 */
public enum Compound {
    // ==========================================
    // 1. 気体・揮発性物質・流体
    // ==========================================
    WATER("水", "H2O", create(Element.H, 2, Element.O, 1)),
    HYDROGEN_GAS("水素ガス", "H2", create(Element.H, 2)),
    OXYGEN_GAS("酸素ガス", "O2", create(Element.O, 2)),
    NITROGEN_GAS("窒素ガス", "N2", create(Element.N, 2)),
    CARBON_DIOXIDE("二酸化炭素", "CO2", create(Element.C, 1, Element.O, 2)),
    CARBON_MONOXIDE("一酸化炭素", "CO", create(Element.C, 1, Element.O, 1)),
    METHANE("メタン", "CH4", create(Element.C, 1, Element.H, 4)),
    AMMONIA("アンモニア", "NH3", create(Element.N, 1, Element.H, 3)),
    HYDROGEN_SULFIDE("硫化水素", "H2S", create(Element.H, 2, Element.S, 1)),
    SULFUR_DIOXIDE("二酸化硫黄", "SO2", create(Element.S, 1, Element.O, 2)),

    // ==========================================
    // 2. 単体元素 (自然界に単体で産出するもの)
    // ==========================================
    CARBON_PURE("炭素単体 / 黒鉛", "C", create(Element.C, 1)),
    SULFUR_PURE("天然硫黄", "S", create(Element.S, 1)),
    GOLD_PURE("自然金", "Au", create(Element.Au, 1)),
    SILVER_PURE("自然銀", "Ag", create(Element.Ag, 1)),
    COPPER_PURE("銅", "Cu", create(Element.Cu, 1)),
    IRON_PURE("鉄", "Fe", create(Element.Fe, 1)),
    PHOSPHORUS_PURE("リン単体", "P", create(Element.P, 1)),

    // ==========================================
    // 3. 銅の酸化物・緑青
    // ==========================================
    CUPRITE("赤色酸化銅", "Cu2O", create(Element.Cu, 2, Element.O, 1)),
    TENORITE("黒色酸化銅", "CuO", create(Element.Cu, 1, Element.O, 1)),
    MALACHITE("緑青 / 塩基性炭酸銅", "Cu2CO3(OH)2", create(Element.Cu, 2, Element.C, 1, Element.H, 2, Element.O, 5)),

    // ==========================================
    // 4. 無機塩・アルカリ・工業塩
    // ==========================================
    SALT("岩塩 / 塩化ナトリウム", "NaCl", create(Element.Na, 1, Element.Cl, 1)),
    POTASSIUM_NITRATE("硝石 / 硝酸カリウム", "KNO3", create(Element.K, 1, Element.N, 1, Element.O, 3)),
    CALCIUM_SULFATE("石膏 / 硫酸カルシウム", "CaSO4", create(Element.Ca, 1, Element.S, 1, Element.O, 4)),
    SODIUM_CARBONATE("ソーダ灰 / 炭酸ナトリウム", "Na2CO3", create(Element.Na, 2, Element.C, 1, Element.O, 3)),
    HYDROCHLORIC_ACID("塩化水素", "HCl", create(Element.H, 1, Element.Cl, 1)),
    SULFURIC_ACID("硫酸", "H2SO4", create(Element.H, 2, Element.S, 1, Element.O, 4)),

    // ==========================================
    // 5. 造岩ケイ酸塩鉱物・石灰質・深層高圧鉱物
    // ==========================================
    SILICA("石英 / 二酸化ケイ素", "SiO2", create(Element.Si, 1, Element.O, 2)),
    ALUMINA("アルミナ", "Al2O3", create(Element.Al, 2, Element.O, 3)),
    ORTHOCLASE("カリ長石", "KAlSi3O8", create(Element.K, 1, Element.Al, 1, Element.Si, 3, Element.O, 8)),
    ANORTHITE("灰長石", "CaAl2Si2O8", create(Element.Ca, 1, Element.Al, 2, Element.Si, 2, Element.O, 8)),
    ALBITE("曹長石", "NaAlSi3O8", create(Element.Na, 1, Element.Al, 1, Element.Si, 3, Element.O, 8)),
    FORSTERITE("苦土かんらん石", "Mg2SiO4", create(Element.Mg, 2, Element.Si, 1, Element.O, 4)),
    FAYALITE("鉄かんらん石", "Fe2SiO4", create(Element.Fe, 2, Element.Si, 1, Element.O, 4)),
    KAOLINITE("カオリナイト / 粘土鉱物", "Al2Si2O5(OH)4", create(Element.Al, 2, Element.Si, 2, Element.O, 9, Element.H, 4)),
    CALCITE("方解石 / 炭酸カルシウム", "CaCO3", create(Element.Ca, 1, Element.C, 1, Element.O, 3)),
    QUICKLIME("生石灰 / 酸化カルシウム", "CaO", create(Element.Ca, 1, Element.O, 1)),
    SLAKED_LIME("消石灰 / 水酸化カルシウム", "Ca(OH)2", create(Element.Ca, 1, Element.O, 2, Element.H, 2)),

    // --- 深層岩・高圧変成鉱物 ---
    PYROXENE("普通輝石", "CaMgSi2O6", create(Element.Ca, 1, Element.Mg, 1, Element.Si, 2, Element.O, 6)),
    SERPENTINE("蛇紋石", "Mg3Si2O5(OH)4", create(Element.Mg, 3, Element.Si, 2, Element.O, 9, Element.H, 4)),
    RINGWOODITE("リングウッダイト / 超高圧スピネル鉱物", "Mg2SiO4", create(Element.Mg, 2, Element.Si, 1, Element.O, 4)),

    // ==========================================
    // 6. 鉱石鉱物・半導体・起電力励起物質
    // ==========================================
    HEMATITE("赤鉄鉱", "Fe2O3", create(Element.Fe, 2, Element.O, 3)),
    MAGNETITE("磁鉄鉱", "Fe3O4", create(Element.Fe, 3, Element.O, 4)),
    CHALCOPYRITE("黄銅鉱", "CuFeS2", create(Element.Cu, 1, Element.Fe, 1, Element.S, 2)),
    PYRITE("黄鉄鉱", "FeS2", create(Element.Fe, 1, Element.S, 2)),
    BERYL("緑柱石 / エメラルド母岩", "Be3Al2Si6O18", create(Element.Be, 3, Element.Al, 2, Element.Si, 6, Element.O, 18)),
    LAZURITE("ラズライト / 青金石", "Na3CaAl3Si3O12S", create(Element.Na, 3, Element.Ca, 1, Element.Al, 3, Element.Si, 3, Element.O, 12, Element.S, 1)),
    APATITE("リン灰石", "Ca5(PO4)3", create(Element.Ca, 5, Element.P, 3, Element.O, 12)),
    HYDROXYAPATITE("水酸アパタイト / 生体骨基質", "Ca5(PO4)3(OH)", create(Element.Ca, 5, Element.P, 3, Element.O, 13, Element.H, 1)),

    // --- レッドストーン用 ---
    FERROELECTRIC_CRYSTAL("強誘電マルチフェロイック結晶", "BiFeO3", create(Element.Bi, 1, Element.Fe, 1, Element.O, 3)),
    SELENIDE_SEMICONDUCTOR("電荷移動型セレン化物半導体", "CdSe", create(Element.Cd, 1, Element.Se, 1)),

    // ==========================================
    // 7. 有機化合物・土壌有機物・植物二次代謝産物
    // ==========================================
    CELLULOSE("セルロース / 植物繊維", "C6H10O5", create(Element.C, 6, Element.H, 10, Element.O, 5)),
    LIGNIN("リグニン / 木質素", "C9H10O2", create(Element.C, 9, Element.H, 10, Element.O, 2)),
    GLUCOSE("ブドウ糖 / グルコース", "C6H12O6", create(Element.C, 6, Element.H, 12, Element.O, 6)),
    SUCROSE("スクロース / 砂糖", "C12H22O11", create(Element.C, 12, Element.H, 22, Element.O, 11)),
    AMINO_ACID("アミノ酸骨格", "C3H7NO2", create(Element.C, 3, Element.H, 7, Element.N, 1, Element.O, 2)),
    KERATIN("ケラチン / 毛髪・羽毛タンパク質", "C5H9NO2S", create(Element.C, 5, Element.H, 9, Element.N, 1, Element.O, 2, Element.S, 1)),
    COLLAGEN("コラーゲン / 皮革タンパク質", "C5H8N2O2", create(Element.C, 5, Element.H, 8, Element.N, 2, Element.O, 2)),
    CHITIN("キチン / 甲殻・菌類外壁", "C8H13NO5", create(Element.C, 8, Element.H, 13, Element.N, 1, Element.O, 5)),
    LIPID("中性脂肪 / 脂質", "C18H36O2", create(Element.C, 18, Element.H, 36, Element.O, 2)),
    CHLOROPHYLL("クロロフィル / 葉緑素", "C55H72MgN4O5", create(Element.C, 55, Element.H, 72, Element.Mg, 1, Element.N, 4, Element.O, 5)),
    THEOBROMINE("テオブロミン / カカオ成分", "C7H8N4O2", create(Element.C, 7, Element.H, 8, Element.N, 4, Element.O, 2)),
    MELANIN("メラニン色素", "C18H10N2O4", create(Element.C, 18, Element.H, 10, Element.N, 2, Element.O, 4)),
    TNT_COMPOUND("トリニトロトルエン", "C7H5N3O6", create(Element.C, 7, Element.H, 5, Element.N, 3, Element.O, 6)),

    // --- 土壌有機物 ---
    HUMUS("腐植質 / フミン酸", "C9H9NO6", create(Element.C, 9, Element.H, 9, Element.N, 1, Element.O, 6)),
    FULVIC_ACID("フルボ酸 / 森林浸透酸", "C14H12O8", create(Element.C, 14, Element.H, 12, Element.O, 8)),

    // --- 樹種固有成分 ---
    TANNIN("タンニン / 没食子酸", "C7H6O5", create(Element.C, 7, Element.H, 6, Element.O, 5)),
    PINENE("α-ピネン / 松脂", "C10H16", create(Element.C, 10, Element.H, 16)),
    BETULIN("ベツリン / 白樺外皮成分", "C30H50O2", create(Element.C, 30, Element.H, 50, Element.O, 2)),
    LATEX("天然ゴム / ポリイソプレン", "C5H8", create(Element.C, 5, Element.H, 8)),
    ACACIA_GUM("アラビアガム / 多糖類", "C6H10O5", create(Element.C, 6, Element.H, 10, Element.O, 5)),
    COUMARIN("クマリン / 桜芳香成分", "C9H6O2", create(Element.C, 9, Element.H, 6, Element.O, 2)),

    // ==========================================
    // 8. 染料・顔料化合物
    // ==========================================
    TITANIUM_DIOXIDE("チタン白 / 酸化チタン", "TiO2", create(Element.Ti, 1, Element.O, 2)),
    CHROMIUM_OXIDE("ビリジアン / 酸化クロム", "Cr2O3", create(Element.Cr, 2, Element.O, 3)),
    COBALT_ALUMINATE("コバルト青", "CoAl2O4", create(Element.Co, 1, Element.Al, 2, Element.O, 4)),
    CINNABAR("辰砂 / 赤色顔料", "HgS", create(Element.Hg, 1, Element.S, 1)),
    CADMIUM_SULFIDE("カドミウム黄", "CdS", create(Element.Cd, 1, Element.S, 1)),
    RHODONITE("バラ輝石 / ピンク鉱物", "MnSiO3", create(Element.Mn, 1, Element.Si, 1, Element.O, 3)),
    TYRIAN_PURPLE("貝紫 / 臭素化インジゴ", "C16H8Br2N2O2", create(Element.C, 16, Element.H, 8, Element.Br, 2, Element.N, 2, Element.O, 2)),
    QUINACRIDONE("キナクリドン / マゼンタ顔料", "C20H12N2O2", create(Element.C, 20, Element.H, 12, Element.N, 2, Element.O, 2)),
    COPPER_PHTHALOCYANINE("フタロシアニン青 / シアン顔料", "C32H16CuN8", create(Element.C, 32, Element.H, 16, Element.Cu, 1, Element.N, 8)),

    // ==========================================
    // 9. 宇宙・隕石・異界希少化合物
    // ==========================================
    TROILITE("トロイライト / 隕石硫化鉄", "FeS", create(Element.Fe, 1, Element.S, 1)),
    MOISSANITE("モアッサナイト / 天然炭化ケイ素", "SiC", create(Element.Si, 1, Element.C, 1)),
    FULLERENE("フラーレン / 宇宙炭素ナノケージ", "C60", create(Element.C, 60)),
    XENON_TRIOXIDE("三酸化キセノン / 異界希ガス酸化物", "XeO3", create(Element.Xe, 1, Element.O, 3));

    private final String localizedName;
    private final String formulaString;
    private final Map<Element, Integer> formula;

    Compound(String localizedName, String rawFormula, Map<Element, Integer> formula) {
        this.localizedName = localizedName;
        this.formulaString = formatSubscript(rawFormula);
        this.formula = Collections.unmodifiableMap(formula);
    }

    public String getLocalizedName() { return localizedName; }
    public String getFormulaString() { return formulaString; }
    public Map<Element, Integer> getFormula() { return formula; }

    public static String formatSubscript(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append((char) ('\u2080' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Map<Element, Integer> create(Object... args) {
        Map<Element, Integer> map = new EnumMap<>(Element.class);
        for (int i = 0; i < args.length; i += 2) {
            Element elem = (Element) args[i];
            int count = (Integer) args[i + 1];
            map.put(elem, count);
        }
        return map;
    }
}