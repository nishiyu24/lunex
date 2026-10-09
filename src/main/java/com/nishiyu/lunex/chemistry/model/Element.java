package com.nishiyu.lunex.chemistry.model;

public enum Element {
    // --- 第1周期 ---
    H("水素", 0xFFFFFF),
    He("ヘリウム", 0xFFC0CB),

    // --- 第2周期 ---
    Li("リチウム", 0xCC40FF),
    Be("ベリリウム", 0xC2FF00),
    B("ホウ素", 0xFFB5B5),
    C("炭素", 0xAAAAAA),
    N("窒素", 0x5070FF),
    O("酸素", 0xFF4040),
    F("フッ素", 0xA0FFA0),
    Ne("ネオン", 0xFF4040),

    // --- 第3周期 ---
    Na("ナトリウム", 0xAB5CF2),
    Mg("マグネシウム", 0x8AFF00),
    Al("アルミニウム", 0xBFA6A6),
    Si("ケイ素", 0xF0C8A0),
    P("リン", 0xFF9040),
    S("硫黄", 0xFFFF40),
    Cl("塩素", 0x40FF40),
    Ar("アルゴン", 0xC080FF),

    // --- 第4周期 ---
    K("カリウム", 0x8F40D4),
    Ca("カルシウム", 0x3DFF00),
    Sc("スカンジウム", 0xE6E6E6),
    Ti("チタン", 0xBFC2C7),
    V("バナジウム", 0xA6A6AB),
    Cr("クロム", 0x8A99C7),
    Mn("マンガン", 0x9C7AC7),
    Fe("鉄", 0xC0C0C0),
    Co("コバルト", 0x4040FF),
    Ni("ニッケル", 0x50C080),
    Cu("銅", 0xFF9040),
    Zn("亜鉛", 0x7D80B0),
    Ga("ガリウム", 0xC28F8F),
    Ge("ゲルマニウム", 0x668F8F),
    As("ヒ素", 0xBD80E3),
    Se("セレン", 0xFFA100),
    Br("臭素", 0xFF6040),
    Kr("クリプトン", 0x80FF80),

    // --- 第5周期 ---
    Rb("ルビジウム", 0x702EB0),
    Sr("ストロンチウム", 0x00FF00),
    Y("イットリウム", 0x94FFFF),
    Zr("ジルコニウム", 0x94E0E0),
    Nb("ニオブ", 0x73C2C9),
    Mo("モリブデン", 0x54B5B5),
    Tc("テクネチウム", 0x3B9E9E),
    Ru("ルテニウム", 0x248F8F),
    Rh("ロジウム", 0x0A7D8C),
    Pd("パラジウム", 0x006985),
    Ag("銀", 0xE0E0E0),
    Cd("カドミウム", 0xFFD98F),
    In("インジウム", 0xA67573),
    Sn("スズ", 0x668080),
    Sb("アンチモン", 0x9E63B5),
    Te("テルル", 0xD47A00),
    I("ヨウ素", 0xC040C0),
    Xe("キセノン", 0x429EB0),

    // --- 第6周期 ---
    Cs("セシウム", 0x57178F),
    Ba("バリウム", 0x00D900),
    La("ランタン", 0x70D4FF),
    Ce("セリウム", 0xFFFFC7),
    Pr("プラセオジム", 0xD9FFC7),
    Nd("ネオジム", 0xC7FFC7),
    Pm("プロメチウム", 0xA3FFC7),
    Sm("サマリウム", 0x8FFFC7),
    Eu("ユウロピウム", 0x61FFC7),
    Gd("ガドリニウム", 0x45FFC7),
    Tb("テルビウム", 0x30FFC7),
    Dy("ジスプロシウム", 0x1FFFC7),
    Ho("ホルミウム", 0x00FF9C),
    Er("エルビウム", 0x00E675),
    Tm("ツリウム", 0x00D452),
    Yb("イッテルビウム", 0x00BF38),
    Lu("ルテチウム", 0x00AB24),
    Hf("ハフニウム", 0x4DC2FF),
    Ta("タンタル", 0x4DA6FF),
    W("タングステン", 0x2194D6),
    Re("レニウム", 0x267DAB),
    Os("オスミウム", 0x266696),
    Ir("イリジウム", 0x175487),
    Pt("白金", 0xD0E0E0),
    Au("金", 0xFFD700),
    Hg("水銀", 0xB8B8D0),
    Tl("タリウム", 0xA6544D),
    Pb("鉛", 0x575961),
    Bi("ビスマス", 0x9E4FB5),
    Po("ポロニウム", 0xAB5C00),
    At("アスタチン", 0x754F45),
    Rn("ラドン", 0x428296),

    // --- 第7周期 ---
    Fr("フランシウム", 0x420066),
    Ra("ラジウム", 0x007D00),
    Ac("アクチニウム", 0x70ABFA),
    Th("トリウム", 0x00BAFF),
    Pa("プロトアクチニウム", 0x00A1FF),
    U("ウラン", 0x008FFF),
    Np("ネプツニウム", 0x0080FF),
    Pu("プルトニウム", 0x006BFF),
    Am("アメリシウム", 0x545CF2),
    Cm("キュリウム", 0x785CE6),
    Bk("バークリウム", 0x8A4FE6),
    Cf("カリホルニウム", 0xA136D4),
    Es("アインスタイニウム", 0xB31FD4),
    Fm("フェルミウム", 0xB31FBA),
    Md("メンデレビウム", 0xB30DA6),
    No("ノーベリウム", 0xBD0D87),
    Lr("ローレンシウム", 0xC70066),
    Rf("ラザホージウム", 0xCC0059),
    Db("ドブニウム", 0xD1004F),
    Sg("シーボーギウム", 0xD90045),
    Bh("ボーリウム", 0xE00038),
    Hs("ハッシウム", 0xE6002E),
    Mt("マイトネリウム", 0xEB0026),
    Ds("ダームスタチウム", 0x999999),
    Rg("レントゲニウム", 0x999999),
    Cn("コペルニシウム", 0x999999),
    Nh("ニホニウム", 0x999999),
    Fl("フレロビウム", 0x999999),
    Mc("モスコビウム", 0x999999),
    Lv("リバモリウム", 0x999999),
    Ts("テネシン", 0x999999),
    Og("オガネソン", 0x999999);

    private final String localizedName;
    private final int color;

    Element(String localizedName) {
        this(localizedName, 0x999999);
    }

    Element(String localizedName, int color) {
        this.localizedName = localizedName;
        this.color = color;
    }

    public String getLocalizedName() { return localizedName; }
    public int getColor() { return color; }
}