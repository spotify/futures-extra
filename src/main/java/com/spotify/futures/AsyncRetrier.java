/*
 * Copyright (c) 2014 Spotify AB
 */

package com.spotify.futures;

import com.google.common.base.Supplier;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.FutureFallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.withFallback;
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

    return retry(code, retries, DEFAULT_DELAY_MILLIS, MILLISECONDS);
  }

  public <T> ListenableFuture<T> retry(final Supplier<ListenableFuture<T>> code,
                                       final int retries,
                                       final long delayMillis) {

    return retry(code, retries, delayMillis, MILLISECONDS);
  }

  public <T> ListenableFuture<T> retry(final Supplier<ListenableFuture<T>> code,
                                       final int retries,
                                       final long delay,
                                       final TimeUnit timeUnit) {

    return withFallback(code.get(), new FutureFallback<T>() {
      @Override
      public ListenableFuture<T> create(Throwable t) throws Exception {
        if (retries > 0) {
          if (delay > 0) {
            return delay(new Supplier<ListenableFuture<T>>() {
              @Override
              public ListenableFuture<T> get() {
                return retry(code, retries - 1, delay, timeUnit);
              }
            }, delay, timeUnit);
          } else {
            return retry(code, retries - 1, delay, timeUnit);
          }
        } else {
          return immediateFailedFuture(t);
        }
      }
    });
  }

  private <T> ListenableFuture<T> delay(final Supplier<ListenableFuture<T>> code,
                                        final long delay,
                                        final TimeUnit timeUnit) {

    final CallbackFuture<T> future = new CallbackFuture<T>();

    executorService.schedule(new Runnable() {
      @Override
      public void run() {
        try {
          addCallback(code.get(), future);
        } catch (Exception e) {
          future.onFailure(e);
        }
      }
    }, delay, timeUnit);

    return future;
  }

  private static class CallbackFuture<T> extends AbstractFuture<T> implements FutureCallback<T> {
    @Override
    public void onSuccess(T result) {
      set(result);
    }

    @Override
    public void onFailure(Throwable t) {
      setException(t);
    }
  }

}
