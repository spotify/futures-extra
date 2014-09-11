/*
 * Copyright (c) 2014 Spotify AB
 */

package com.spotify.futures;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A helper for retrying asynchronous calls.
 *
 * It implements retries up to a specified limit with a constant delay between each retry.
 *
 * TODO: Add support for specifying backoff behavior
 */
public final class AsyncRetrier {

  public static final long DEFAULT_DELAY_MILLIS = 0;

  private final ScheduledExecutorService executorService;

  private AsyncRetrier(ScheduledExecutorService executorService) {
    this.executorService = executorService;
  }

  public static AsyncRetrier create(ScheduledExecutorService sleeper) {
    return new AsyncRetrier(sleeper);
  }

  public <T> ListenableFuture<T> retry(final Supplier<ListenableFuture<T>> code,
                                       final int retries) {

    return retry(code, retries, DEFAULT_DELAY_MILLIS, MILLISECONDS, Predicates.<T>alwaysTrue());
  }

  public <T> ListenableFuture<T> retry(final Supplier<ListenableFuture<T>> code,
                                       final int retries,
                                       final long delayMillis) {

    return retry(code, retries, delayMillis, MILLISECONDS, Predicates.<T>alwaysTrue());
  }

  public <T> ListenableFuture<T> retry(final Supplier<ListenableFuture<T>> code,
                                       final int retries,
                                       final long delay,
                                       final TimeUnit timeUnit) {
    return retry(code, retries, delay, timeUnit, Predicates.<T>alwaysTrue());
  }

  public <T> ListenableFuture<T> retry(final Supplier<ListenableFuture<T>> code,
                                       final int retries,
                                       final long delay,
                                       final TimeUnit timeUnit,
                                       final Predicate<T> retryCondition) {

    SettableFuture<T> future = SettableFuture.create();
    startRetry(future, code, retries, delay, timeUnit, retryCondition);
    return future;
  }

  private <T> void startRetry(final SettableFuture<T> future,
                              final Supplier<ListenableFuture<T>> code,
                              final int retries,
                              final long delay,
                              final TimeUnit timeUnit,
                              final Predicate<T> retryCondition) {

    ListenableFuture<T> codeFuture;
    try {
      codeFuture = code.get();
    } catch (Exception e) {
      handleFailure(future, code, retries, delay, timeUnit, retryCondition, e);
      return;
    }

    Futures.addCallback(codeFuture, new FutureCallback<T>() {
      @Override
      public void onSuccess(T result) {
        if (retryCondition.apply(result)) {
          future.set(result);
        } else {
          handleFailure(future, code, retries, delay, timeUnit, retryCondition, new RuntimeException("Failed retry condition"));
        }
      }

      @Override
      public void onFailure(Throwable t) {
        handleFailure(future, code, retries, delay, timeUnit, retryCondition, t);
      }
    });
  }

  private <T> void handleFailure(final SettableFuture<T> future,
                                 final Supplier<ListenableFuture<T>> code,
                                 final int retries,
                                 final long delay, final TimeUnit timeUnit,
                                 final Predicate<T> retryCondition,
                                 Throwable t) {
    if (retries > 0) {
      if (delay > 0) {
        executorService.schedule(new Runnable() {
          @Override
          public void run() {
            startRetry(future, code, retries - 1, delay, timeUnit, retryCondition);
          }
        }, delay, timeUnit);
      } else {
        startRetry(future, code, retries - 1, delay, timeUnit, retryCondition);
      }
    } else {
      future.setException(t);
    }
  }
}
