package dev.svmarvinthepa.statsviewer.core.database.write;

@FunctionalInterface
public interface DbWriteTask {
    void execute() throws Exception;
}
