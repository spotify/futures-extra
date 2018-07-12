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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;

class ListenableToCompletableFutureWrapper<V>
    extends CompletableFuture<V>
    implements FutureCallback<V> {

  private final ListenableFuture<V> future;

  ListenableToCompletableFutureWrapper(final ListenableFuture<V> future) {
    this.future = checkNotNull(future, "future");
    Futures.addCallback(future, this);
  }

  ListenableToCompletableFutureWrapper(final ListenableFuture<V> future, Executor executor) {
    this.future = checkNotNull(future, "future");
    Futures.addCallback(future, this, executor);
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    future.cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }

  public ListenableFuture<V> unwrap() {
    return future;
  }

  @Override
  public void onSuccess(final V result) {
    complete(result);
  }

  @Override
  public void onFailure(final Throwable t) {
    completeExceptionally(t);
  }
}
