package com.spotify.futures;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Callable;

/**
 * Interface for a Semaphore-like concurrency limiter that operates on asynchronous tasks.
 */
public interface ConcurrencyLimiter<T> {
  ListenableFuture<T> add(Callable<? extends ListenableFuture<T>> callable);
  int remainingQueueCapacity();
  int remainingActiveCapacity();
}
