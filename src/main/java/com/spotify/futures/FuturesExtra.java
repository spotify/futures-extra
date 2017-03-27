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
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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
    return Futures.transformAsync(conditionValue, new AsyncFunction<B, A>() {
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
  public static <T> ListenableFuture<T> select(final List<? extends ListenableFuture<T>> futures) {
    Preconditions.checkNotNull(futures);
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
   * Represents an operation that accepts a single input argument and returns no result.
   */
  public interface Consumer<T> {

    /**
     * Performs this operation on the given argument.
     * @param t the input argument
     */
    void accept(T t);
  }

  /**
   * A lambda-friendly way to attach callbacks to a {@link ListenableFuture}. The success
   * callback will only be run if the future completes successfully, and failure will
   * only be run if the future fails.
   * @param future a ListenableFuture to attach the callbacks to.
   * @param success a consumer, to be called with the result of the successful future.
   * @param failure a consumer, to be called with the result of the failed future.
   * @throws NullPointerException if the {@param success} and {@param failure} are null
   */
  public static <T> void addCallback(
          final ListenableFuture<T> future,
          final Consumer<? super T> success,
          final Consumer<Throwable> failure) {
    if (success == null && failure == null) {
      throw new NullPointerException();
    }
    Futures.addCallback(future, new FutureCallback<T>() {
      @Override
      public void onSuccess(final T result) {
        if (success != null) {
          success.accept(result);
        }
      }

      @Override
      public void onFailure(final Throwable throwable) {
        if (failure != null) {
          failure.accept(throwable);
        }
      }
    });
  }

  /**
   * A lambda-friendly way to attach a callback to a {@link ListenableFuture}. The callback will
   * only be run if the future completes successfully.
   * @param future a ListenableFuture to attach the callback to.
   * @param consumer a consumer, to be called with the result of the successful future.
   */
  public static <T> void addSuccessCallback(
          ListenableFuture<T> future,
          final Consumer<? super T> consumer) {
    addCallback(future, consumer, null);
  }

  /**
   * A lambda-friendly way to attach a callback to a {@link ListenableFuture}. The callback will
   * only be run if the future fails.
   * @param future a ListenableFuture to attach the callback to.
   * @param consumer a consumer, to be called with the result of the failed future.
   */
  public static <T> void addFailureCallback(
          ListenableFuture<T> future,
          final Consumer<Throwable> consumer) {
    addCallback(future, null, consumer);
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transform(ListenableFuture, Function)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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
   * Implementations of this interface is used to synchronously transform the
   * input values into an output value.
   */
  public interface Function2<Z, A, B> {
    /**
     * Combine the inputs into the returned output
     *
     * @param a an input value
     * @param b an input value
     * @return a result of the combination of the input values.
     */
    Z apply(A a, B b);
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transformAsync(ListenableFuture, AsyncFunction)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
   */
  public static <Z, A, B> ListenableFuture<Z> asyncTransform2(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          final AsyncFunction2<Z, ? super A, ? super B> function) {
    return transform(Arrays.asList(a, b), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) throws Exception {
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
    ListenableFuture<Z> apply(A a, B b) throws Exception;
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transform(ListenableFuture, Function)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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

  /**
   * Implementations of this interface is used to synchronously transform the
   * input values into an output value.
   */
  public interface Function3<Z, A, B, C> {
    Z apply(A a, B b, C c);
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transformAsync(ListenableFuture, AsyncFunction)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
   */
  public static <Z, A, B, C> ListenableFuture<Z> asyncTransform3(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final AsyncFunction3<Z, ? super A, ? super B, ? super C> function) {
    return transform(Arrays.asList(a, b, c), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) throws Exception {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2));
      }
    });
  }

  public interface AsyncFunction3<Z, A, B, C> {
    ListenableFuture<Z> apply(A a, B b, C c) throws Exception;
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transform(ListenableFuture, Function)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param d a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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

  /**
   * Implementations of this interface is used to synchronously transform the
   * input values into an output value.
   */
  public interface Function4<Z, A, B, C, D> {
    Z apply(A a, B b, C c, D d);
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transformAsync(ListenableFuture, AsyncFunction)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param d a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
   */
  public static <Z, A, B, C, D> ListenableFuture<Z> asyncTransform4(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final AsyncFunction4<Z, ? super A, ? super B, ? super C, ? super D> function) {
    return transform(Arrays.asList(a, b, c, d), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) throws Exception {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public interface AsyncFunction4<Z, A, B, C, D> {
    ListenableFuture<Z> apply(A a, B b, C c, D d) throws Exception;
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transform(ListenableFuture, Function)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param d a ListenableFuture to combine
   * @param e a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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

  /**
   * Implementations of this interface is used to synchronously transform the
   * input values into an output value.
   */
  public interface Function5<Z, A, B, C, D, E> {
    Z apply(A a, B b, C c, D d, E e);
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transformAsync(ListenableFuture, AsyncFunction)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param d a ListenableFuture to combine
   * @param e a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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
      public ListenableFuture<Z> apply(List<Object> results) throws Exception {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2),
                (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public interface AsyncFunction5<Z, A, B, C, D, E> {
    ListenableFuture<Z> apply(A a, B b, C c, D d, E e) throws Exception;
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transform(ListenableFuture, Function)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param d a ListenableFuture to combine
   * @param e a ListenableFuture to combine
   * @param f a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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

  /**
   * Implementations of this interface is used to synchronously transform the
   * input values into an output value.
   */
  public interface Function6<Z, A, B, C, D, E, F> {
    Z apply(A a, B b, C c, D d, E e, F f);
  }

  /**
   * Transform the input futures into a single future, using the provided
   * transform function. The transformation follows the same semantics as as
   * {@link Futures#transformAsync(ListenableFuture, AsyncFunction)} and the input
   * futures are combined using {@link Futures#allAsList}.
   *
   * @param a a ListenableFuture to combine
   * @param b a ListenableFuture to combine
   * @param c a ListenableFuture to combine
   * @param d a ListenableFuture to combine
   * @param e a ListenableFuture to combine
   * @param f a ListenableFuture to combine
   * @param function the implementation of the transform
   * @return a ListenableFuture holding the result of function.apply()
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
      public ListenableFuture<Z> apply(List<Object> results) throws Exception {
        return function.apply(
                (A) results.get(0), (B) results.get(1), (C) results.get(2),
                (D) results.get(3), (E) results.get(4), (F) results.get(5));
      }
    });
  }

  public interface AsyncFunction6<Z, A, B, C, D, E, F> {
    ListenableFuture<Z> apply(A a, B b, C c, D d, E e, F f) throws Exception;
  }

  private static <Z> ListenableFuture<Z> transform(final List<? extends ListenableFuture<?>> inputs,
                                                   final Function<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }

  private static <Z> ListenableFuture<Z> transform(final List<? extends ListenableFuture<?>> inputs,
                                                   final AsyncFunction<List<Object>, Z> function) {
    return Futures.transformAsync(Futures.allAsList(inputs), function);
  }

  /**
   * <p>Transform a list of futures to a future that returns a joined result of them all.
   * The result can be used to get the joined results and ensures no future that were not part of
   * the join is accessed.</p>
   * @see #join(ListenableFuture...)
   */
  public static ListenableFuture<JoinedResults> join(List<? extends ListenableFuture<?>> inputs) {
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
    List<? extends ListenableFuture<?>> list = Arrays.asList(inputs);
    return Futures.transform(Futures.allAsList(list), new JoinedResults.Transform(list));
  }

  /**
   * Use {@link Futures#transform(ListenableFuture, Function)} instead if using Guava 20.
   */
  public static <I, O> ListenableFuture<O> syncTransform(
          ListenableFuture<I> input, Function<? super I, ? extends O> function) {
    return Futures.transform(input, function);
  }

  /**
   * @deprecated Use {@link Futures#transformAsync(ListenableFuture, AsyncFunction)}
   */
  @Deprecated
  public static <I, O> ListenableFuture<O> asyncTransform(
          ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function) {
    return Futures.transformAsync(input, function);
  }

  /**
   * check that a future is completed.
   * @param future the future.
   * @throws IllegalStateException if the future is not completed.
   */
  public static <T> void checkCompleted(ListenableFuture<T> future) {
    if (!future.isDone()) {
      throw new IllegalStateException("future was not completed");
    }
  }

  /**
   * Get the value of a completed future.
   *
   * @param future a completed future.
   * @return the value of the future if it has one.
   * @throws IllegalStateException if the future is not completed.
   * @throws com.google.common.util.concurrent.UncheckedExecutionException if the future has failed
   */
  public static <T> T getCompleted(ListenableFuture<T> future) {
    checkCompleted(future);
    return Futures.getUnchecked(future);
  }

  /**
   * Get the exception of a completed future.
   *
   * @param future a completed future.
   * @return the exception of a future or null if no exception was thrown
   * @throws IllegalStateException if the future is not completed.
   */
  public static <T> Throwable getException(ListenableFuture<T> future) {
    checkCompleted(future);
    try {
      Uninterruptibles.getUninterruptibly(future);
      return null;
    } catch (ExecutionException e) {
      return e.getCause();
    }
  }
}
