package com.spotify.futures;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

public class FuturesExtra {

  /**
   * Returns a future that fails with a timeout exception if the parent future has not
   * finished before the timeout. If a timeout occurs, the future will throw a TimeoutException.
   * The new returned future will always be executed on the provided scheduledExecutorService,
   * even when the parent future does not timeout.
   *
   * @param scheduledExecutorService executor that runs the timeout code. If the future times out,
   *                                 this is also the thread any callbacks will run on.
   * @param future                   the future to wrap as a timeout future.
   * @param timeout                  how long to wait before timing out a future
   * @param unit                     unit of the timeout
   * @return a future that may timeout before the parent future is done.
   */
  public static <T> ListenableFuture<T> makeTimeoutFuture(
          ScheduledExecutorService scheduledExecutorService,
          ListenableFuture<T> future,
          final long timeout, final TimeUnit unit) {
    final SettableFuture<T> promise = SettableFuture.create();

    scheduledExecutorService.schedule(new Runnable() {
      @Override
      public void run() {
        promise.setException(new TimeoutException("Future timed out after " + timeout + " " + unit.name()));
      }
    }, timeout, unit);

    Futures.addCallback(future, new FutureCallback<T>() {
      @Override
      public void onSuccess(T result) {
        promise.set(result);
      }

      @Override
      public void onFailure(Throwable t) {
        promise.setException(t);
      }
    }, scheduledExecutorService);

    return promise;
  }

  /**
   * @return a new {@link com.google.common.util.concurrent.ListenableFuture} whose result is
   * that of the first successful of {@param futures} to return or the exception of the last failing future,
   * or a failed future {@code #IllegalArgumentException} if {@param futures} is empty
   *
   * @throws {@code #java.lang.NullPointerException} if the {@param futures} is null
   */
  public static <T> ListenableFuture<T> select(final List<ListenableFuture<T>> futures) {
    requireNonNull(futures);
    if (futures.isEmpty()) {
      return Futures.immediateFailedFuture(new NoSuchElementException("List is empty"));
    }
    final int count = futures.size();
    final AtomicInteger failures = new AtomicInteger();

    final SettableFuture<T> promise = SettableFuture.create();
    final FutureCallback<T> cb = new FutureCallback<T>() {
      @Override
      public void onSuccess(final T result) {
        promise.set(result);
      }

      @Override
      public void onFailure(Throwable t) {
        if (failures.incrementAndGet() == count) {
          promise.setException(t);
        }
      }
    };

    for (final ListenableFuture<T> future: futures) {
      Futures.addCallback(future, cb);
    }
    return promise;
  }

  public static <Z, A, B> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          final Function2<Z, ? super A, ? super B> function) {
    return transform(Arrays.asList(a, b), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1));
      }
    });
  }

  public static interface Function2<Z, A, B> {
    Z apply(A a, B b);
  }

  public static <Z, A, B> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          final AsyncFunction2<Z, ? super A, ? super B> function) {
    return transform(Arrays.asList(a, b), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1));
      }
    });
  }

  public static interface AsyncFunction2<Z, A, B> {
    ListenableFuture<Z> apply(A a, B b);
  }

  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final Function3<Z, ? super A, ? super B, ? super C> function) {
    return transform(Arrays.asList(a, b, c), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2));
      }
    });
  }

  public static interface Function3<Z, A, B, C> {
    Z apply(A a, B b, C c);
  }

  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final AsyncFunction3<Z, ? super A, ? super B, ? super C> function) {
    return transform(Arrays.asList(a, b, c), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2));
      }
    });
  }

  public static interface AsyncFunction3<Z, A, B, C> {
    ListenableFuture<Z> apply(A a, B b, C c);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final Function4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return transform(Arrays.asList(a, b, c, d), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public static interface Function4<Z, A, B, C, D> {
    Z apply(A a, B b, C c, D d);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final AsyncFunction4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return transform(Arrays.asList(a, b, c, d), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public static interface AsyncFunction4<Z, A, B, C, D> {
    ListenableFuture<Z> apply(A a, B b, C c, D d);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final Function5<Z, ? super A, ? super B, ? super C, ? super D, ? super E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public static interface Function5<Z, A, B, C, D, E> {
    Z apply(A a, B b, C c, D d, E e);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final AsyncFunction5<Z, ? super A, ? super B, ? super C, ? super D, ? super E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public static interface AsyncFunction5<Z, A, B, C, D, E> {
    ListenableFuture<Z> apply(A a, B b, C c, D d, E e);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs, final Function<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs, final AsyncFunction<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }

  /**
   * <p>Transform a list of futures to a future that returns a joined result of them all.
   * The result can be used to get the joined results and ensures no future that were not part of the join is accessed.</p>
   * @see #join(ListenableFuture...)
   */
  public static ListenableFuture<JoinedResults> join(List<ListenableFuture<?>> inputs) {
    return Futures.transform(Futures.allAsList(inputs), new JoinedResults.Transform(inputs));
  }

  /**
   * <p>Transform a list of futures to a future that returns a joined result of them all.
   * The result can be used to get the joined results and ensures no future that were not part of the join is accessed.</p>
   * <p>Example</p>
   * <pre>
   * {@code
   * final Future<String> first = Futures.immediateFuture("ok");
   * final Future<Integer> second = Futures.immediateFuture(1);
   * JoinedResults futures = FuturesExtra.join(first, second).get();
   * assertEquals("ok", futures.get(first));
   * assertEquals(1, futures.get(second));
   * }
   * </pre>
   */
  public static ListenableFuture<JoinedResults> join(ListenableFuture<?>... inputs) {
    List<ListenableFuture<?>> list = Arrays.asList(inputs);
    return Futures.transform(Futures.allAsList(list), new JoinedResults.Transform(list));
  }
}
