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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class ApiFutureToCompletableFutureWrapper<V>
    extends CompletableFuture<V>
    implements Runnable {

  private final ApiFuture<V> future;

  ApiFutureToCompletableFutureWrapper(final ApiFuture<V> future, final Executor executor) {
    this.future = checkNotNull(future, "future");
    this.future.addListener(this, executor);
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    future.cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }

  public ApiFuture<V> unwrap() {
    return future;
  }

  @Override
  public void run() {
    try {
      complete(Uninterruptibles.getUninterruptibly(future));
    } catch (final ExecutionException e) {
      completeExceptionally(e.getCause());
    } catch (final CancellationException e) {
      cancel(false);
    } catch (final RuntimeException e) {
      completeExceptionally(e);
    }
  }
}
