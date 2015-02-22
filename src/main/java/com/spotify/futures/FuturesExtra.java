/*
 * Copyright (c) 2013-2015 Spotify AB
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

/**
 * Static utility methods pertaining to the {@link ListenableFuture} interface.
 */
@SuppressWarnings("unchecked")
public class FuturesExtra {

  /**
   * Returns a future that fails with a {@link TimeoutException} if the parent future has not
   * finished before the timeout. The new returned future will always be executed on the provided
   * scheduledExecutorService, even when the parent future does not timeout.
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
   * be cancelled by a call to {@link ListenableFuture#cancel(boolean)} with
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
   * {@param future} or any exception thrown by {@param validator}.
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
   * Returns a new {@link ListenableFuture} with the result of the first of futures that
   * successfully completes. If all ListenableFutures in futures fails the returned feature
   * fails as well with the Exception of the last failed future. If futures is an empty
   * list the returned future fails immediately with {@link java.util.NoSuchElementException}
   *
   * @param futures a {@link List} of futures
   * @return a new future with the result of the first completing future.
   * @throws NullPointerException if the {@param futures} is null
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
   * Transform the results of two {@link ListenableFuture}s a and b once both has completed using
   * {@code function} and complete the returned ListenableFuture at that point. This method is
   * synchronous in the sense that function.apply() executes synchronously and returns a value
   * and not a {@link java.util.concurrent.Future}.
   *
   * @param a the first ListenableFuture
   * @param b the second ListenableFuture
   * @param function a Function2 implementation, possibly expressed as a java 8 lambda
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the supplied Function2.apply() invocation has completed.
   */
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

  /**
   * Implementations of this interface is used in {@link #syncTransform2} to synchronously
   * transform two values into a third value.
   */
  public interface Function2<Z, A, B> {
    /**
     * Combine a and b and return the result.
     * @param a the first value.
     * @param b the second value.
     * @return a result of the combination of a and b.
     */
    Z apply(A a, B b);
  }

  /**
   * Asynchronously transform the result of two {@link ListenableFuture}s a and b once both
   * has completed using {@code function} and complete the returned ListenableFuture at the point.
   * Execution is asynchronous in the sense that function returns a Future that may be executed
   * asynchronously and once it completes it will complete the future that this method returns.
   *
   * @param a the first ListenableFuture
   * @param b the second ListenableFuture
   * @param function an AsyncFunction2 implementation, possibly expressed as a java 8 lambda.
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the future that gets returned by the supplied Function2.apply() has completed.
   */
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

  /**
   * Implementations of this interface is used in {@link #syncTransform2} to asynchronously
   * transform two values into a return value.
   */
  public interface AsyncFunction2<Z, A, B> {
    /**
     * Create and return a {@link ListenableFuture} that will execute combining a and b into
     * some sort of result.
     *
     * @param a the first input value.
     * @param b the second input value.
     * @return a ListenableFuture that will complete once the result of a and b is computed.
     */
    ListenableFuture<Z> apply(A a, B b);
  }

  /**
   * Transform the results of 3 {@link ListenableFuture}s a, b and c once all has completed using
   * {@code function} and complete the returned ListenableFuture at that point. This method is
   * synchronous in the sense that function.apply() executes synchronously and returns a value
   * and not a {@link java.util.concurrent.Future}.
   *
   * @param a the first ListenableFuture.
   * @param b the second ListenableFuture.
   * @param c the third ListenableFuture.
   * @param function a Function3 implementation, possibly expressed as a java 8 lambda
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the supplied Function3.apply() invocation has completed.
   */
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
   * Asynchronously transform the result of 3 {@link ListenableFuture}s a, b and c once both
   * has completed using {@code function} and complete the returned ListenableFuture at the point.
   * Execution is asynchronous in the sense that function returns a Future that may be executed
   * asynchronously and once it completes it will complete the future that this method returns.
   *
   * @param a the first ListenableFuture
   * @param b the second ListenableFuture
   * @param c the third ListenableFuture
   * @param function an AsyncFunction3 implementation, possibly expressed as a java 8 lambda.
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the future that gets returned by the supplied Function3.apply() has completed.
   */
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
   * Transform the results of 4 {@link ListenableFuture}s a, b, c and d once all has completed using
   * {@code function} and complete the returned ListenableFuture at that point. This method is
   * synchronous in the sense that function.apply() executes synchronously and returns a value
   * and not a {@link java.util.concurrent.Future}.
   *
   * @param a the first ListenableFuture.
   * @param b the second ListenableFuture.
   * @param c the third ListenableFuture.
   * @param d the fourth ListenableFuture.
   * @param function a Function4 implementation, possibly expressed as a java 8 lambda
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the supplied Function4.apply() invocation has completed.
   */
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
   * Asynchronously transform the result of 3 {@link ListenableFuture}s a, b, c and d once both
   * has completed using {@code function} and complete the returned ListenableFuture at the point.
   * Execution is asynchronous in the sense that function returns a Future that may be executed
   * asynchronously and once it completes it will complete the future that this method returns.
   *
   * @param a the first ListenableFuture
   * @param b the second ListenableFuture
   * @param c the third ListenableFuture
   * @param d the fourth ListenableFuture
   * @param function an AsyncFunction4 implementation, possibly expressed as a java 8 lambda.
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the future that gets returned by the supplied Function4.apply() has completed.
   */
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
   * Transform the results of 5 {@link ListenableFuture}s a, b, c, d and e once all has completed
   * using {@code function} and complete the returned ListenableFuture at that point. This method is
   * synchronous in the sense that function.apply() executes synchronously and returns a value
   * and not a {@link java.util.concurrent.Future}.
   *
   * @param a the first ListenableFuture.
   * @param b the second ListenableFuture.
   * @param c the third ListenableFuture.
   * @param d the fourth ListenableFuture.
   * @param e the fifth ListenableFuture.
   * @param function a Function5 implementation, possibly expressed as a java 8 lambda
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the supplied Function5.apply() invocation has completed.
   */
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
   * Asynchronously transform the result of 3 {@link ListenableFuture}s a, b, c, d and e once both
   * has completed using {@code function} and complete the returned ListenableFuture at the point.
   * Execution is asynchronous in the sense that function returns a Future that may be executed
   * asynchronously and once it completes it will complete the future that this method returns.
   *
   * @param a the first ListenableFuture
   * @param b the second ListenableFuture
   * @param c the third ListenableFuture
   * @param d the fourth ListenableFuture
   * @param e the fifth ListenableFuture
   * @param function an AsyncFunction5 implementation, possibly expressed as a java 8 lambda.
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the future that gets returned by the supplied Function5.apply() has completed.
   */
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
   * Transform the results of 6 {@link ListenableFuture}s a, b, c, d, e and f once all has completed
   * using {@code function} and complete the returned ListenableFuture at that point. This method is
   * synchronous in the sense that function.apply() executes synchronously and returns a value
   * and not a {@link java.util.concurrent.Future}.
   *
   * @param a the first ListenableFuture.
   * @param b the second ListenableFuture.
   * @param c the third ListenableFuture.
   * @param d the fourth ListenableFuture.
   * @param e the fifth ListenableFuture.
   * @param f the sixth ListenableFuture.
   * @param function a Function5 implementation, possibly expressed as a java 8 lambda
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the supplied Function5.apply() invocation has completed.
   */
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
   * Asynchronously transform the result of 3 {@link ListenableFuture}s a, b, c, d, e and f once
   * both has completed using {@code function} and complete the returned ListenableFuture at the
   * point.
   * Execution is asynchronous in the sense that function returns a Future that may be executed
   * asynchronously and once it completes it will complete the future that this method returns.
   *
   * @param a the first ListenableFuture
   * @param b the second ListenableFuture
   * @param c the third ListenableFuture
   * @param d the fourth ListenableFuture
   * @param e the fifth ListenableFuture
   * @param f the sixth ListenableFuture
   * @param function an AsyncFunction6 implementation, possibly expressed as a java 8 lambda.
   * @return a ListenableFuture of the result of {@code function}, that completes as soon as
   * the future that gets returned by the supplied Function6.apply() has completed.
   */
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
