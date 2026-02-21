package dev.svmarvinthepa.statsviewer.core.database.write;

import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.plugin.Plugin;

import java.sql.SQLTransientConnectionException;
import java.util.concurrent.TimeUnit;

public class DbWriteWorker implements Runnable {

    private final Plugin plugin;
    private volatile boolean running = true;

    public DbWriteWorker(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        DebugLogger.info("DB write worker started");

        while (running || !DbWriteQueue.isEmpty()) {
            try {
                DbWriteTask task = DbWriteQueue.poll(1, TimeUnit.SECONDS);
                if (task == null) continue;

                try {
                    task.execute();
                } catch (SQLTransientConnectionException e) {
                    DebugLogger.warn("DB busy, retrying write later");
                    Thread.sleep(1000);
                    DbWriteQueue.submit(task);
                } catch (Exception e) {
                    DebugLogger.error("DB write failed permanently", e);
                }

            } catch (InterruptedException e) {
                break;
            }
        }

        DebugLogger.info("DB write worker stopped (queue flushed)");
    }

    public void shutdown() {
        running = false;
    }
}
