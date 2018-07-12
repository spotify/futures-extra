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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CompletableFuturesExtra {

  private CompletableFuturesExtra() {
    throw new AssertionError();
  }

  /**
   * Wrap a {@link ListenableFuture} in a {@link CompletableFuture}. The returned future will
   * complete with the same result or failure as the original future. Completing the returned
   * future does not complete the original future.
   *
   * @param future The {@link ListenableFuture} to wrap in a {@link CompletableFuture}.
   * @return A {@link CompletableFuture} that completes when the original future completes.
   */
  public static <V> CompletableFuture<V> toCompletableFuture(
      ListenableFuture<V> future) {
    if (future instanceof CompletableToListenableFutureWrapper) {
      return ((CompletableToListenableFutureWrapper<V>) future).unwrap();
    }
    return new ListenableToCompletableFutureWrapper<V>(future);
  }

  /**
   * Wrap a {@link ListenableFuture} in a {@link CompletableFuture}. The returned future will
   * complete with the same result or failure as the original future. Completing the returned
   * future does not complete the original future.
   *
   * @param future The {@link ListenableFuture} to wrap in a {@link CompletableFuture}.
   * @param executor The executor to run the wrapped {@code future} in.
   * @return A {@link CompletableFuture} that completes when the original future completes.
   */
  public static <V> CompletableFuture<V> toCompletableFuture(
      ListenableFuture<V> future, Executor executor) {
    if (future instanceof CompletableToListenableFutureWrapper) {
      return ((CompletableToListenableFutureWrapper<V>) future).unwrap();
    }
    return new ListenableToCompletableFutureWrapper<V>(future, executor);
  }

  /**
   * Wrap a {@link CompletableFuture} in a {@link ListenableFuture}. The returned future will
   * complete with the same result or failure as the original future.
   *
   * @param future The {@link CompletableFuture} to wrap in a {@link ListenableFuture}.
   * @return A {@link ListenableFuture} that completes when the original future completes.
   */
  public static <V> ListenableFuture<V> toListenableFuture(
      CompletableFuture<V> future) {
    return toListenableFuture((CompletionStage<V>) future);
  }

  /**
   * Wrap a {@link CompletionStage} in a {@link ListenableFuture}. The returned future will
   * complete with the same result or failure as the original future.
   *
   * @param future The {@link CompletionStage} to wrap in a {@link ListenableFuture}.
   * @return A {@link ListenableFuture} that completes when the original future completes.
   */
  public static <V> ListenableFuture<V> toListenableFuture(
      CompletionStage<V> future) {
    if (future instanceof ListenableToCompletableFutureWrapper) {
      return ((ListenableToCompletableFutureWrapper<V>) future).unwrap();
    }
    return new CompletableToListenableFutureWrapper<V>(future);
  }

  /**
   * Returns a new CompletableFuture that is already exceptionally completed with
   * the given exception.
   *
   * @param throwable the exception
   * @return the exceptionally completed CompletableFuture
   */
  public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable throwable) {
    final CompletableFuture<T> future = new CompletableFuture<T>();
    future.completeExceptionally(throwable);
    return future;
  }

  /**
   * Returns a new stage that, when this stage completes
   * either normally or exceptionally, is executed with this stage's
   * result and exception as arguments to the supplied function.
   *
   * <p>When this stage is complete, the given function is invoked
   * with the result (or {@code null} if none) and the exception (or
   * {@code null} if none) of this stage as arguments, and the
   * function's result is used to complete the returned stage.
   *
   * This differs from
   * {@link java.util.concurrent.CompletionStage#handle(java.util.function.BiFunction)}
   * in that the function should return a {@link java.util.concurrent.CompletionStage} rather than
   * the value directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn the function to use to compute the value of the
   * returned {@link CompletionStage}
   * @param <U> the function's return type
   * @return the new {@link CompletionStage}
   */
  public static <T, U> CompletionStage<U> handleCompose(
          CompletionStage<T> stage,
          BiFunction<? super T, Throwable, ? extends CompletionStage<U>> fn) {
    return dereference(stage.handle(fn));
  }

  /**
   * Returns a new stage that, when this stage completes
   * exceptionally, is executed with this stage's exception as the
   * argument to the supplied function.  Otherwise, if this stage
   * completes normally, then the returned stage also completes
   * normally with the same value.
   *
   * This differs from
   * {@link java.util.concurrent.CompletionStage#exceptionally(java.util.function.Function)}
   * in that the function should return a {@link java.util.concurrent.CompletionStage} rather than
   * the value directly.
   *
   * @param stage the {@link CompletionStage} to compose
   * @param fn the function to use to compute the value of the
   * returned {@link CompletionStage} if this stage completed
   * exceptionally
   * @return the new {@link CompletionStage}
   */
  public static <T> CompletionStage<T> exceptionallyCompose(
          CompletionStage<T> stage,
          Function<Throwable, ? extends CompletionStage<T>> fn) {
    return dereference(wrap(stage).exceptionally(fn));
  }

  /**
   * check that a stage is completed.
   * @param stage a {@link CompletionStage}.
   * @throws IllegalStateException if the stage is not completed.
   */
  public static <T> void checkCompleted(CompletionStage<T> stage) {
    if (!stage.toCompletableFuture().isDone()) {
      throw new IllegalStateException("future was not completed");
    }
  }

  /**
   * Get the value of a completed stage.
   *
   * @param stage a completed {@link CompletionStage}.
   * @return the value of the stage if it has one.
   * @throws IllegalStateException if the stage is not completed.
   * @throws com.google.common.util.concurrent.UncheckedExecutionException
   * if the future has failed with a non-runtime exception, otherwise
   * the actual exception
   */
  public static <T> T getCompleted(CompletionStage<T> stage) {
    CompletableFuture<T> future = stage.toCompletableFuture();
    checkCompleted(future);
    try {
      return future.join();
    } catch (CompletionException e) {
      throw Throwables.propagate(e.getCause());
    }
  }

  /**
   * This takes a stage of a stage of a value and
   * returns a plain stage of a value.
   *
   * @param stage a {@link CompletionStage} of a {@link CompletionStage} of a value
   * @return the {@link CompletionStage} of the value
   */
  public static <T> CompletionStage<T> dereference(
      CompletionStage<? extends CompletionStage<T>> stage) {
    //noinspection unchecked
    return stage.thenCompose(Identity.INSTANCE);
  }

  private static <T> CompletionStage<CompletionStage<T>> wrap(CompletionStage<T> future) {
    //noinspection unchecked
    return future.thenApply((Function<T, CompletionStage<T>>) WrapFunction.INSTANCE);
  }

  private enum Identity implements Function {
    INSTANCE;

    @Override
    public Object apply(Object o) {
      return o;
    }
  }


  private enum WrapFunction implements Function {
    INSTANCE;

    @Override
    public Object apply(Object o) {
      return CompletableFuture.completedFuture(o);
    }
  }

}
