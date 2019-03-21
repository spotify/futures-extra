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
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ApiFuturesExtra {
  /**
   * Converts an {@link ApiFuture} to a {@link CompletableFuture}.
   *
   * @param future the {@link ApiFuture} to wrap.
   * @return a {@link CompletableFuture} that completes when the original future completes.
   */
  public static <V> CompletableFuture<V> toCompletableFuture(ApiFuture<V> future) {
    return toCompletableFuture(future, MoreExecutors.directExecutor());
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

}
