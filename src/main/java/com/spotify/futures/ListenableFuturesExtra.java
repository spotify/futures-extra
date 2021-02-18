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

import com.google.common.util.concurrent.ListenableFuture;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ListenableFuturesExtra {
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
    return toCompletableFuture(future, MoreExecutors.directExecutor());
  }

  /**
   * Wrap a {@link ListenableFuture} in a {@link CompletableFuture}. The returned future will
   * complete with the same result or failure as the original future. Completing the returned
   * future does not complete the original future.
   *
   * @param future The {@link ListenableFuture} to wrap in a {@link CompletableFuture}.
   * @param executor the executor where the listener is running.
   * @return A {@link CompletableFuture} that completes when the original future completes.
   */
  public static <V> CompletableFuture<V> toCompletableFuture(
          ListenableFuture<V> future, Executor executor) {
    if (future instanceof CompletableToListenableFutureWrapper) {
      return ((CompletableToListenableFutureWrapper<V>) future).unwrap();
    }
    return new ListenableToCompletableFutureWrapper<>(future, executor);
  }

}
