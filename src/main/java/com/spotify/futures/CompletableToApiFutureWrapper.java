/*
 * Copyright (c) 2013-2018 Spotify AB
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
import com.google.common.util.concurrent.AbstractFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

class CompletableToApiFutureWrapper<V>
    extends AbstractFuture<V>
    implements ApiFuture<V>, BiConsumer<V, Throwable> {

  private final CompletionStage<V> future;

  CompletableToApiFutureWrapper(final CompletionStage<V> future) {
    this.future = future;
    this.future.whenComplete(this);
  }

  public CompletableFuture<V> unwrap() {
    return future.toCompletableFuture();
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    future.toCompletableFuture().cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }

  @Override
  public void accept(V v, Throwable throwable) {
    if (throwable != null) {
      if (throwable instanceof CancellationException) {
        cancel(false);
      } else {
        setException(CompletableToListenableFutureWrapper.unwrapThrowable(throwable));
      }
    } else {
      set(v);
    }
  }
}
