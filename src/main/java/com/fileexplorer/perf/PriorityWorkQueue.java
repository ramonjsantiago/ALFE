package com.fileexplorer.perf;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Priority work queue with cancellation tokens. Lower priority executes first.
 * Intended for icon/thumb/fs hydration jobs.
 */
public final class PriorityWorkQueue implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PriorityWorkQueue.class.getName());

    public static final class CancelToken {
        private final AtomicLong generationRef;
        private final long myGeneration;

        private CancelToken(AtomicLong generationRef, long myGeneration) {
            this.generationRef = generationRef;
            this.myGeneration = myGeneration;
        }

        public boolean isCancelled() {
            return generationRef.get() != myGeneration;
        }

        public void throwIfCancelled() {
            if (isCancelled()) throw new CancellationException("Cancelled (generation changed)");
        }
    }

    private static final class PTask implements Comparable<PTask>, Runnable {
        final int priority;
        final long seq;
        final String name;
        final CancelToken token;
        final Runnable delegate;

        PTask(int priority, long seq, String name, CancelToken token, Runnable delegate) {
            this.priority = priority;
            this.seq = seq;
            this.name = name;
            this.token = token;
            this.delegate = delegate;
        }

        @Override
        public int compareTo(PTask o) {
            int c = Integer.compare(this.priority, o.priority);
            if (c != 0) return c;
            return Long.compare(this.seq, o.seq);
        }

        @Override
        public void run() {
            if (token != null && token.isCancelled()) return;
            delegate.run();
        }
    }

    private final ThreadPoolExecutor exec;
    private final AtomicLong seq = new AtomicLong(0);
    private final AtomicLong generation = new AtomicLong(1);
    private final int maxQueue;

    public PriorityWorkQueue(String threadNamePrefix, int threads, int maxQueue) {
        BlockingQueue<Runnable> q = new PriorityBlockingQueue<>();
        this.exec = new ThreadPoolExecutor(
                Math.max(1, threads),
                Math.max(1, threads),
                30, TimeUnit.SECONDS,
                q,
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName(threadNamePrefix + "-" + t.getId());
                    return t;
                }
        );
        this.exec.setRejectedExecutionHandler((r, e) -> LOG.warning("Work rejected (executor shutting down?)"));
        this.maxQueue = Math.max(100, maxQueue);
    }

    public CancelToken newToken() {
        return new CancelToken(generation, generation.get());
    }

    public void cancelAll() {
        generation.incrementAndGet();
    }

    public void submit(int priority, String name, CancelToken token, Runnable task) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(task, "task");

        int qs = exec.getQueue().size();
        if (qs > maxQueue && priority > 50) {
            return;
        }

        long s = seq.incrementAndGet();
        exec.execute(new PTask(priority, s, name, token, wrap(name, token, task)));
    }

    private Runnable wrap(String name, CancelToken token, Runnable task) {
        return () -> {
            if (token != null && token.isCancelled()) return;
            long t0 = System.nanoTime();
            try {
                task.run();
            } catch (CancellationException ce) {
                // expected
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Task failed: " + name, t);
            } finally {
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                if (ms > 50) {
                    LOG.info(() -> "BG task '" + name + "' took " + ms + "ms");
                }
            }
        };
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}
