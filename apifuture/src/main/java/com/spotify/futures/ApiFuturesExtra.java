/*
 * Copyright (c) 2019 Spotify AB
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

import com.google.api.core.ApiFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class ApiFuturesExtra {
  /**
   * Converts an {@link ApiFuture} to a {@link CompletableFuture}.
   *
   * @param future the {@link ApiFuture} to wrap.
   * @return a {@link CompletableFuture} that completes when the original future completes.
   */
  public static <V> CompletableFuture<V> toCompletableFuture(ApiFuture<V> future) {
    return toCompletableFuture(future, Runnable::run);
  }

  /**
   * Converts an {@link ApiFuture} to a {@link CompletableFuture}.
   *
   * @param future the {@link ApiFuture} to wrap.
   * @param executor the executor where the listener is running.
   * @return a {@link CompletableFuture} that completes when the original future completes.
   */
  public static <V> CompletableFuture<V> toCompletableFuture(ApiFuture<V> future,
                                                             Executor executor) {
    if (future instanceof CompletableToApiFutureWrapper) {
      return ((CompletableToApiFutureWrapper<V>) future).unwrap();
    }
    return new ApiFutureToCompletableFutureWrapper<>(future, executor);
  }

  /**
   * Wrap a {@link CompletionStage} in a {@link ApiFuture}. The returned future will
   * complete with the same result or failure as the original future.
   *
   * @param future The {@link CompletionStage} to wrap in a {@link ApiFuture}.
   * @return A {@link ApiFuture} that completes when the original future completes.
   */
  public static <V> ApiFuture<V> toApiFuture(CompletionStage<V> future) {
    if (future instanceof ApiFutureToCompletableFutureWrapper) {
      return ((ApiFutureToCompletableFutureWrapper<V>) future).unwrap();
    }
    return new CompletableToApiFutureWrapper<>(future);
  }

}
