package com.nishiyu.lunex.program.server.entity;

import com.nishiyu.lunex.program.core.APIRegistry;
import com.nishiyu.lunex.program.server.entity.api.CustomGoalAPI;
import com.nishiyu.lunex.program.server.entity.api.EntitySystemAPI;
import com.nishiyu.lunex.program.server.entity.api.MobEntityAPI;

public class EntityAPIRegistry extends APIRegistry {

    @Override
    protected void registerAPIs() {
        registerAPIClass("mob", MobEntityAPI.class);
        registerAPIClass("goal", CustomGoalAPI.class);
        registerAPIClass("system", EntitySystemAPI.class);
    }
}