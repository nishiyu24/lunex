package com.nishiyu.lunex.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SkinLoader {
    // スティーブ用スキンのフォルダ: .minecraft/lunex_skins
    public static final File SKINS_DIR = new File(FMLPaths.GAMEDIR.get().toFile(), "lunex_skins");
    private static final List<String> availableSkins = new ArrayList<>();

    // 起動時に呼ばれる初期化メソッド
    public static void init() {
        // 1. 動的スキン用フォルダの作成
        if (!SKINS_DIR.exists()) {
            SKINS_DIR.mkdirs();
        }

        // 2. GeckoLibモデル用リソースパックの自動生成
        generateResourcePack();
    }

    private static void generateResourcePack() {
        File resourcePackDir = new File(FMLPaths.GAMEDIR.get().toFile(), "resourcepacks");
        if (!resourcePackDir.exists()) resourcePackDir.mkdirs();

        File customPackDir = new File(resourcePackDir, "Lunex_CustomResources");
        if (!customPackDir.exists()) customPackDir.mkdirs();

        // pack.mcmeta の生成 (マイクラがリソースパックとして認識するために必須)
        File packMeta = new File(customPackDir, "pack.mcmeta");
        if (!packMeta.exists()) {
            try (FileWriter writer = new FileWriter(packMeta)) {
                int packFormat = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
                String metaContent = "{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": " + packFormat + ",\n" +
                        "    \"description\": \"Lunex Custom Models & Skins\"\n" +
                        "  }\n" +
                        "}";
                writer.write(metaContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // GeckoLib用のフォルダツリー生成
        String[] paths = {
                "assets/lunex/geo",
                "assets/lunex/animations",
                "assets/lunex/textures/entity"
        };

        for (String path : paths) {
            File dir = new File(customPackDir, path);
            if (!dir.exists()) dir.mkdirs();
        }

        // Readmeの生成
        File readme = new File(customPackDir, "readme.txt");
        if (!readme.exists()) {
            try (FileWriter writer = new FileWriter(readme)) {
                writer.write("=== Lunex Custom Resources ===\n\n");
                writer.write("Place your GeckoLib files here:\n");
                writer.write("- Models (.geo.json) -> assets/lunex/geo/\n");
                writer.write("- Textures (.png) -> assets/lunex/textures/entity/\n");
                writer.write("- Animations (.json) -> assets/lunex/animations/\n\n");
                writer.write("IMPORTANT: Enable this resource pack in Minecraft's options menu!\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // フォルダ内の .png をスキャンしてリストを返す
    public static List<String> getAvailableSkins() {
        availableSkins.clear();
        availableSkins.add("default");

        if (SKINS_DIR.exists() && SKINS_DIR.isDirectory()) {
            File[] files = SKINS_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().substring(0, file.getName().length() - 4);
                    availableSkins.add(name);
                }
            }
        }
        return availableSkins;
    }

    // レンダリング用の ResourceLocation を取得・生成する
    public static ResourceLocation getSkinLocation(String skinName) {
        if (skinName == null || skinName.isEmpty() || skinName.equals("default")) {
            return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
        }

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("lunex", "dynamic_skin_" + skinName.toLowerCase());

        if (Minecraft.getInstance().getTextureManager().getTexture(loc, null) == null) {
            File skinFile = new File(SKINS_DIR, skinName + ".png");
            if (skinFile.exists()) {
                try (InputStream in = new FileInputStream(skinFile)) {
                    NativeImage nativeImage = NativeImage.read(in);
                    DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                    Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
                } catch (Exception e) {
                    e.printStackTrace();
                    return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
                }
            } else {
                return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
            }
        }
        return loc;
    }
}