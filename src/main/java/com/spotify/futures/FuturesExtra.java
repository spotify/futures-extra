/*
 * Copyright (c) 2013-2014 Spotify AB
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
        String message = "Future timed out after " + timeout + " " + unit.name();
        promise.setException(new TimeoutException(message));
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
   * This takes two futures of type {@link A} and {@link B} and works like
   * a valve on {@link A}, with validation executed on {@link B}.
   *
   * Returns a future with the result of {@link A} that will wait for a
   * condition on {@link B} to be validated first. Both futures can run in
   * parallel. If the condition fails validation, the {@link A} future will
   * be cacelled by a call to {@link ListenableFuture#cancel(boolean)} with
   * {@code false}.
   *
   * This is useful for when you want to optimistically run a time consuming
   * path while validating if it should be computed or not by a parallel
   * async computation.
   *
   * @param conditionValue  The future computing the value for validation.
   * @param future          The actual value future.
   * @param validator       A validator for the condition.
   *
   * @return a new {@link ListenableFuture} eventually either containing
   * {@param future} or any exception trown by {@param validator}.
   */
  public static <A, B> ListenableFuture<A> fastFail(
          final ListenableFuture<B> conditionValue,
          final ListenableFuture<A> future,
          final Validator<B> validator) {
    return Futures.transform(conditionValue, new AsyncFunction<B, A>() {
      @Override
      public ListenableFuture<A> apply(B value) throws Exception {
        try {
          validator.validate(value);
          return future;

        } catch (Exception e) {
          future.cancel(false);
          throw e;
        }
      }
    });
  }

  /**
   * @return a new {@link ListenableFuture} whose result is
   * that of the first successful of {@param futures} to return or the exception of the
   * last failing future, or a failed future {@code #IllegalArgumentException}
   * if {@param futures} is empty
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

  /**
   * @deprecated
   * use {@link #syncTransform2(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.Function2)} instead
   */
  @Deprecated
  public static <Z, A, B> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          final Function2<Z, ? super A, ? super B> function) {
    return syncTransform2(a, b, function);
  }

  public static <Z, A, B> ListenableFuture<Z> syncTransform2(
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

  public interface Function2<Z, A, B> {
    Z apply(A a, B b);
  }

  /**
   * @deprecated
   * use {@link #asyncTransform2(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.AsyncFunction2)} instead
   */
  @Deprecated
  public static <Z, A, B> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          final AsyncFunction2<Z, ? super A, ? super B> function) {
    return asyncTransform2(a, b, function);
  }

  public static <Z, A, B> ListenableFuture<Z> asyncTransform2(
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

  public interface AsyncFunction2<Z, A, B> {
    ListenableFuture<Z> apply(A a, B b);
  }

  /**
   * @deprecated
   * use {@link #syncTransform3(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.Function3)} instead
   */
  @Deprecated
  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final Function3<Z, ? super A, ? super B, ? super C> function) {
    return syncTransform3(a, b, c, function);
  }

  public static <Z, A, B, C> ListenableFuture<Z> syncTransform3(
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

  public interface Function3<Z, A, B, C> {
    Z apply(A a, B b, C c);
  }

  /**
   * @deprecated
   * use {@link #asyncTransform3(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.AsyncFunction3)} instead
   */
  @Deprecated
  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final AsyncFunction3<Z, ? super A, ? super B, ? super C> function) {
    return asyncTransform3(a, b, c, function);
  }

  public static <Z, A, B, C> ListenableFuture<Z> asyncTransform3(
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

  public interface AsyncFunction3<Z, A, B, C> {
    ListenableFuture<Z> apply(A a, B b, C c);
  }

  /**
   * @deprecated
   * use {@link #syncTransform4(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.Function4)} instead
   */
  @Deprecated
  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final Function4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return syncTransform4(a, b, c, d, function);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> syncTransform4(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final Function4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return transform(Arrays.asList(a, b, c, d), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public interface Function4<Z, A, B, C, D> {
    Z apply(A a, B b, C c, D d);
  }

  /**
   * @deprecated
   * use {@link #asyncTransform4(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.AsyncFunction4)} instead
   */
  @Deprecated
  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final AsyncFunction4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return asyncTransform4(a, b, c, d, function);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> asyncTransform4(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final AsyncFunction4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return transform(Arrays.asList(a, b, c, d), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public interface AsyncFunction4<Z, A, B, C, D> {
    ListenableFuture<Z> apply(A a, B b, C c, D d);
  }

  /**
   * @deprecated
   * use {@link #syncTransform5(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.Function5)} instead
   */
  @Deprecated
  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final Function5<Z, ? super A, ? super B, ? super C, ? super D, ? super E> function) {
    return syncTransform5(a, b, c, d, e, function);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> syncTransform5(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final Function5<Z, ? super A, ? super B, ? super C, ? super D, ? super E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2),
                (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public interface Function5<Z, A, B, C, D, E> {
    Z apply(A a, B b, C c, D d, E e);
  }

  /**
   * @deprecated
   * use {@link #asyncTransform5(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.AsyncFunction5)} instead
   */
  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final AsyncFunction5<Z, ? super A, ? super B, ? super C,
                  ? super D, ? super E> function) {
    return asyncTransform5(a, b, c, d, e, function);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> asyncTransform5(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final AsyncFunction5<Z, ? super A, ? super B, ? super C,
                  ? super D, ? super E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2),
                (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public interface AsyncFunction5<Z, A, B, C, D, E> {
    ListenableFuture<Z> apply(A a, B b, C c, D d, E e);
  }

  /**
   * @deprecated
   * use {@link #syncTransform6(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.Function6)} instead
   */
  public static <Z, A, B, C, D, E, F> ListenableFuture<Z> transform(
      ListenableFuture<A> a,
      ListenableFuture<B> b,
      ListenableFuture<C> c,
      ListenableFuture<D> d,
      ListenableFuture<E> e,
      ListenableFuture<F> f,
      final Function6<Z, ? super A, ? super B, ? super C,
              ? super D, ? super E, ? super F> function) {
    return syncTransform6(a, b, c, d, e, f, function);
  }

  public static <Z, A, B, C, D, E, F> ListenableFuture<Z> syncTransform6(
      ListenableFuture<A> a,
      ListenableFuture<B> b,
      ListenableFuture<C> c,
      ListenableFuture<D> d,
      ListenableFuture<E> e,
      ListenableFuture<F> f,
      final Function6<Z, ? super A, ? super B, ? super C,
              ? super D, ? super E, ? super F> function) {
    return transform(Arrays.asList(a, b, c, d, e, f), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2),
                (D) results.get(3), (E) results.get(4), (F) results.get(5));
      }
    });
  }

  public interface Function6<Z, A, B, C, D, E, F> {
    Z apply(A a, B b, C c, D d, E e, F f);
  }

  /**
   * @deprecated
   * use {@link #asyncTransform6(com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.google.common.util.concurrent.ListenableFuture,
   * com.spotify.futures.FuturesExtra.AsyncFunction6)} instead
   */
  public static <Z, A, B, C, D, E, F> ListenableFuture<Z> transform(
      ListenableFuture<A> a,
      ListenableFuture<B> b,
      ListenableFuture<C> c,
      ListenableFuture<D> d,
      ListenableFuture<E> e,
      ListenableFuture<F> f,
      final AsyncFunction6<Z, ? super A, ? super B, ? super C,
              ? super D, ? super E, ? super F> function) {
    return asyncTransform6(a, b, c, d, e, f, function);
  }

  public static <Z, A, B, C, D, E, F> ListenableFuture<Z> asyncTransform6(
      ListenableFuture<A> a,
      ListenableFuture<B> b,
      ListenableFuture<C> c,
      ListenableFuture<D> d,
      ListenableFuture<E> e,
      ListenableFuture<F> f,
      final AsyncFunction6<Z, ? super A, ? super B, ? super C, ? super D,
              ? super E, ? super F> function) {
    return transform(Arrays.asList(a, b, c, d, e, f), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2),
                (D) results.get(3), (E) results.get(4), (F) results.get(5));
      }
    });
  }

  public interface AsyncFunction6<Z, A, B, C, D, E, F> {
    ListenableFuture<Z> apply(A a, B b, C c, D d, E e, F f);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs,
                                                   final Function<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs,
                                                   final AsyncFunction<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }

  /**
   * <p>Transform a list of futures to a future that returns a joined result of them all.
   * The result can be used to get the joined results and ensures no future that were not part of
   * the join is accessed.</p>
   * @see #join(ListenableFuture...)
   */
  public static ListenableFuture<JoinedResults> join(List<ListenableFuture<?>> inputs) {
    return Futures.transform(Futures.allAsList(inputs), new JoinedResults.Transform(inputs));
  }

  /**
   * <p>Transform a list of futures to a future that returns a joined result of them all.
   * The result can be used to get the joined results and ensures no future that were not part of
   * the join is accessed.</p>
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

  public static <I, O> ListenableFuture<O> syncTransform(
          ListenableFuture<I> input, Function<? extends I, ? super O> function) {
    return Futures.transform(input, (Function<? super I, ? extends O>) function);
  }

  public static <I, O> ListenableFuture<O> asyncTransform(
          ListenableFuture<I> input, AsyncFunction<? extends I, ? super O> function) {
    return Futures.transform(input, (AsyncFunction<? super I, ? extends O>) function);
  }
}
