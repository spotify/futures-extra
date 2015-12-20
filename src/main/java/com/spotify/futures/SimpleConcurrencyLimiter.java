/*
 * Copyright (c) 2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.futures;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * A SimpleConcurrencyLimiter can be used for efficiently queueing up
 * asynchronous work to only run up to a specific limit of work
 * concurrently.
 *
 * This is a threadsafe class.
 */
public final class SimpleConcurrencyLimiter<T> implements ConcurrencyLimiter<T> {

  private final BlockingQueue<Job<T>> queue;
  private final Semaphore limit;
  private final int maxQueueSize;

  private SimpleConcurrencyLimiter(int maxConcurrency, int maxQueueSize) {
    Preconditions.checkArgument(maxConcurrency > 0);
    Preconditions.checkArgument(maxQueueSize > 0);
    this.maxQueueSize = maxQueueSize;
    this.queue = new ArrayBlockingQueue<Job<T>>(maxQueueSize, false);
    this.limit = new Semaphore(maxConcurrency);
  }

  /**
   *
   * @param maxConcurrency maximum number of futures in progress,
   * @param maxQueueSize maximum number of jobs in queue. This is a soft bound and may be
   *                     temporarily exceeded if add() is called concurrently.
   * @return a new concurrency limiter
   */
  public static <T> SimpleConcurrencyLimiter<T> create(int maxConcurrency, int maxQueueSize) {
    return new SimpleConcurrencyLimiter<T>(maxConcurrency, maxQueueSize);
  }

  /**
   * the callable function will run as soon as the currently active set of
   * futures is less than the maxConcurrency limit.
   *
   * @param callable - a function that creates a future.
   * @returns a proxy future that completes with the future created by the
   *          input function.
   *          This future will be immediately failed with
   *          {@link CapacityReachedException} if the soft queue size limit is exceeded.
   * @throws {@link NullPointerException} if callable is null
   */
  @Override
  public ListenableFuture<T> add(Callable<? extends ListenableFuture<T>> callable) {
    Preconditions.checkNotNull(callable);
    final SettableFuture<T> response = SettableFuture.create();
    final Job<T> job = new Job<T>(callable, response);

    if (!queue.offer(job)) {
      final String message = "Queue size has reached capacity: " + maxQueueSize;
      return Futures.immediateFailedFuture(new CapacityReachedException(message));
    }

    pump();
    return response;
  }

  /**
   * @returns the number of additional callables that can be queued before failing.
   */
  @Override
  public int remainingQueueCapacity() {
    return queue.remainingCapacity();
  }

  /**
   * @returns the number of additional callables that can be run without queueing.
   */
  @Override
  public int remainingActiveCapacity() {
    return limit.availablePermits();
  }

  private void pump() {
    while (true) {
      if (!limit.tryAcquire()) {
        return;
      }

      final Job<T> job = queue.poll();
      if (job == null) {
        limit.release();
        return;
      }

      final SettableFuture<T> response = job.response;

      if (response.isCancelled()) {
        limit.release();
        continue;
      }

      invoke(response, job.callable);
    }
  }

  private void invoke(
      final SettableFuture<T> response, Callable<? extends ListenableFuture<T>> callable) {
    final ListenableFuture<T> future;
    try {
      future = callable.call();
      if (future == null) {
        limit.release();
        response.setException(new NullPointerException());
        return;
      }
    } catch (Throwable e) {
      limit.release();
      response.setException(e);
      return;
    }

    Futures.addCallback(future, new FutureCallback<T>() {
      @Override
      public void onSuccess(T result) {
        limit.release();
        response.set(result);
        pump();
      }

      @Override
      public void onFailure(Throwable t) {
        limit.release();
        response.setException(t);
        pump();
      }
    });
  }

  private static class Job<T> {
    private final Callable<? extends ListenableFuture<T>> callable;
    private final SettableFuture<T> response;

    public Job(Callable<? extends ListenableFuture<T>> callable, SettableFuture<T> response) {
      this.callable = callable;
      this.response = response;
    }
  }

  public static class CapacityReachedException extends RuntimeException {

    public CapacityReachedException(String errorMessage) {
      super(errorMessage);
    }
  }
}
