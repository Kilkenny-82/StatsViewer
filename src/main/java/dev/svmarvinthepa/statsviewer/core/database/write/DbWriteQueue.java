package dev.svmarvinthepa.statsviewer.core.database.write;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class DbWriteQueue {

    private static final BlockingQueue<DbWriteTask> QUEUE =
            new LinkedBlockingQueue<>();

    private DbWriteQueue() {}

    public static void submit(DbWriteTask task) {
        QUEUE.offer(task);
    }

    public static DbWriteTask take() throws InterruptedException {
        return QUEUE.take();
    }

    public static DbWriteTask poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        return QUEUE.poll(timeout, unit);
    }

    public static boolean isEmpty() {
        return QUEUE.isEmpty();
    }

    public static int size() {
        return QUEUE.size();
    }
}
