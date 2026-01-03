package dev.svmarvinthepa.statsviewer.core.stats;

public enum MCStatCategory {

    CUSTOM("custom"),
    PICKED_UP("picked_up"),
    MINED("mined"),
    USED("used"),
    CRAFTED("crafted"),
    DROPPED("dropped"),
    KILLED("killed"),
    KILLED_BY("killed_by");

    private final String tableSuffix;

    MCStatCategory(String tableSuffix) {
        this.tableSuffix = tableSuffix;
    }

    public String getTableName() {
        return "stats_" + tableSuffix;
    }
}
