package dev.svmarvinthepa.statsviewer.core.module;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;

public interface StatsModule {

    String getName();

    void onEnable();
    void onDisable();

    void onSessionStart(StatsContext ctx);

    void onSessionEnd(SessionSnapshot snapshot);
}

