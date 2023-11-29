package br.com.wlabs.hayiz.doc.listener.config;

import java.util.concurrent.*;

public class BlockingTaskSubmissionPolicy implements RejectedExecutionHandler {

    private final long timeout;

    public BlockingTaskSubmissionPolicy(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        try {
            BlockingQueue queue = executor.getQueue();
            //As we are expecting the value of thread pool queue is 0
            if (!queue.offer(r, this.timeout, TimeUnit.MILLISECONDS)) {
                throw new RejectedExecutionException("The Thread Pool is full");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}