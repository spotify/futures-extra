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

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * A ConcurrencyLimiter can be used for efficiently queueing up
 * asynchronous work to only run up to a specific limit of work
 * concurrently.
 *
 * This is a threadsafe class.
 */
public final class ConcurrencyLimiter<T> {

  private final Queue<Job<T>> queue;
  private final Semaphore limit;
  private final int maxConcurrency;

  private ConcurrencyLimiter(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
    Preconditions.checkArgument(maxConcurrency > 0);
    this.queue = new ConcurrentLinkedQueue<Job<T>>();
    this.limit = new Semaphore(maxConcurrency);
  }

  public static <T> ConcurrencyLimiter<T> create(int maxConcurrency) {
    return new ConcurrencyLimiter<T>(maxConcurrency);
  }

  /**
   * the callable function will run as soon as the currently active set of
   * futures is less than the maxConcurrency limit.
   *
   * @param callable - a function that creates a future.
   * @returns a proxy future that completes with the future created by the
   *          input function.
   */
  public ListenableFuture<T> add(Callable<? extends ListenableFuture<T>> callable) {
    Preconditions.checkNotNull(callable);
    final SettableFuture<T> response = SettableFuture.create();
    final Job<T> job = new Job<T>(callable, response);
    queue.add(job);
    pump();
    return response;
  }

  /**
   * @returns the number of callables that are queued up and haven't started
   *          yet.
   */
  public int numQueued() {
    return queue.size();
  }

  /**
   * @returns the number of currently active futures that have not yet completed.
   */
  public int numActive() {
    return maxConcurrency - limit.availablePermits();
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
        response.setException(new NullPointerException());
        limit.release();
        return;
      }
    } catch (Throwable e) {
      response.setException(e);
      limit.release();
      return;
    }

    Futures.addCallback(future, new FutureCallback<T>() {
      @Override
      public void onSuccess(T result) {
        response.set(result);
        limit.release();
        pump();
      }

      @Override
      public void onFailure(Throwable t) {
        response.setException(t);
        limit.release();
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
}
