package com.nishiyu.lunex.program.server.tool;

import com.nishiyu.lunex.program.core.APIRegistry;
import com.nishiyu.lunex.program.core.SuggestionDef;
import com.nishiyu.lunex.program.server.tool.api.*;

import java.util.ArrayList;
import java.util.List;

public class ToolAPIRegistry extends APIRegistry {

    @Override
    protected void registerEvents() {
        super.registerEvents(); // 基本イベントの追加

        // ツール専用のイベントを追加
        suggestions.add(new SuggestionDef("event", "tool", "on_right_click", List.of("num:charge_ticks"), new ArrayList<>(),
                "ツールを右クリックした時に呼ばれます。", "Called when the tool is right-clicked.", true, false, List.of("Out")));
        suggestions.add(new SuggestionDef("event", "tool", "on_left_click_block", List.of("num:x", "num:y", "num:z", "num:mine_state", "num:cooldown"), new ArrayList<>(),
                "ツールでブロックを左クリック（破壊）した時に呼ばれます。", "Called when a block is left-clicked (broken) with the tool.", true, false, List.of("Out")));
        suggestions.add(new SuggestionDef("event", "tool", "on_attack_entity", List.of("str:target_name", "num:cooldown"), new ArrayList<>(),
                "ツールでエンティティを攻撃した時に呼ばれます。", "Called when an entity is attacked with the tool.", true, false, List.of("Out")));
    }

    @Override
    protected void registerAPIs() {
        registerAPIClass("tool", ToolAPI.class);
        registerAPIClass("tool.target", ToolTargetAPI.class);
        registerAPIClass("tool.player", ToolPlayerAPI.class);
        registerAPIClass("tool.world", ToolWorldAPI.class);
        registerAPIClass("tool.entity", ToolEntityAPI.class);
        registerAPIClass("tool.task", ToolTaskAPI.class);
    }
}