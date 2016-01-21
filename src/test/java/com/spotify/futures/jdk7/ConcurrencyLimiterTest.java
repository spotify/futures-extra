/*
 * Copyright (c) 2015 Spotify AB
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
package com.spotify.futures.jdk7;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import com.spotify.futures.ConcurrencyLimiter;
import com.spotify.futures.FuturesExtra;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ConcurrencyLimiterTest {

  @Test(expected = IllegalArgumentException.class)
  public void testTooLowConcurrency() throws Exception {
    ConcurrencyLimiter.create(0, 10);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTooLowQueueSize() throws Exception {
    ConcurrencyLimiter.create(10, 0);
  }

  @Test(expected = NullPointerException.class)
  public void testNullJob() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(1, 10);
    limiter.add(null);
  }

  @Test
  public void testJobReturnsNull() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(1, 10);
    final ListenableFuture<String> response = limiter.add(job(null));
    assertTrue(response.isDone());
    final Throwable exception = FuturesExtra.getException(response);
    assertEquals(NullPointerException.class, exception.getClass());
  }

  @Test
  public void testJobThrows() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(1, 10);
    final ListenableFuture<String> response = limiter.add(new Callable<ListenableFuture<String>>() {
      @Override
      public ListenableFuture<String> call() throws Exception {
        throw new IllegalStateException();
      }
    });

    assertTrue(response.isDone());
    final Throwable exception = FuturesExtra.getException(response);
    assertEquals(IllegalStateException.class, exception.getClass());
  }

  @Test
  public void testJobReturnsFailure() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(1, 10);
    final ListenableFuture<String> response = limiter.add(job(Futures.<String>immediateFailedFuture(new IllegalStateException())));

    assertTrue(response.isDone());
    final Throwable exception = FuturesExtra.getException(response);
    assertEquals(IllegalStateException.class, exception.getClass());
  }

  @Test
  public void testCancellation() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(2, 10);
    final SettableFuture<String> request1 = SettableFuture.create();
    final SettableFuture<String> request2 = SettableFuture.create();

    final ListenableFuture<String> response1 = limiter.add(job(request1));
    final ListenableFuture<String> response2 = limiter.add(job(request2));

    final AtomicBoolean wasInvoked = new AtomicBoolean();
    final ListenableFuture<String> response3 = limiter.add(new Callable<ListenableFuture<String>>() {
      @Override
      public ListenableFuture<String> call() throws Exception {
        wasInvoked.set(true);
        return null;
      }
    });

    response3.cancel(false);

    // 1 and 2 are in progress, 3 is cancelled

    assertFalse(response1.isDone());
    assertFalse(response2.isDone());
    assertTrue(response3.isDone());
    assertEquals(2, limiter.numActive());
    assertEquals(1, limiter.numQueued());

    request2.set("2");

    assertFalse(response1.isDone());
    assertTrue(response2.isDone());
    assertTrue(response3.isDone());
    assertEquals(1, limiter.numActive());
    assertEquals(0, limiter.numQueued());

    request1.set("1");

    assertTrue(response1.isDone());
    assertTrue(response2.isDone());
    assertTrue(response3.isDone());
    assertEquals(0, limiter.numActive());
    assertEquals(0, limiter.numQueued());

    assertFalse(wasInvoked.get());
  }

  @Test
  public void testSimple() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(2, 10);
    final SettableFuture<String> request1 = SettableFuture.create();
    final SettableFuture<String> request2 = SettableFuture.create();
    final SettableFuture<String> request3 = SettableFuture.create();
    final ListenableFuture<String> response1 = limiter.add(job(request1));
    final ListenableFuture<String> response2 = limiter.add(job(request2));
    final ListenableFuture<String> response3 = limiter.add(job(request3));

    request3.set("3");

    // 1 and 2 are in progress, 3 is still blocked

    assertFalse(response1.isDone());
    assertFalse(response2.isDone());
    assertFalse(response3.isDone());
    assertEquals(2, limiter.numActive());
    assertEquals(1, limiter.numQueued());

    request2.set("2");

    assertFalse(response1.isDone());
    assertTrue(response2.isDone());
    assertTrue(response3.isDone());
    assertEquals(1, limiter.numActive());
    assertEquals(0, limiter.numQueued());

    request1.set("1");

    assertTrue(response1.isDone());
    assertTrue(response2.isDone());
    assertTrue(response3.isDone());
    assertEquals(0, limiter.numActive());
    assertEquals(0, limiter.numQueued());

  }

  @Test
  public void testLongRunning() throws Exception {
    final AtomicInteger activeCount = new AtomicInteger();
    final AtomicInteger maxCount = new AtomicInteger();
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(10, 100000);
    List<CountingJob> jobs = Lists.newArrayList();
    List<ListenableFuture<String>> responses = Lists.newArrayList();
    for (int i = 0; i < 100000; i++) {
      final CountingJob job = new CountingJob(activeCount, maxCount);
      jobs.add(job);
      responses.add(limiter.add(job));
    }

    for (int i = 0; i < jobs.size(); i++) {
      int jobIndex = 10 * (i / 10) + (9 - i % 10);
      final CountingJob job = jobs.get(jobIndex);
      if (jobIndex % 2 == 0) {
        job.future.set("success");
      } else {
        job.future.setException(new IllegalStateException());
      }
    }
    for (ListenableFuture<String> response : responses) {
      assertTrue(response.isDone());
    }
    assertEquals(10, maxCount.get());
    assertEquals(0, activeCount.get());
    assertEquals(0, limiter.numActive());
    assertEquals(0, limiter.numQueued());
    assertEquals(10, limiter.remainingActiveCapacity());
    assertEquals(100000, limiter.remainingQueueCapacity());
  }

  @Test
  public void testQueueSize() throws Exception {
    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(10, 10);
    for (int i = 0; i < 20; i++) {
      limiter.add(job(SettableFuture.<String>create()));
    }

    final ListenableFuture<String> future = limiter.add(job(SettableFuture.<String>create()));
    assertTrue(future.isDone());
    final Throwable e = FuturesExtra.getException(future);
    assertEquals(ConcurrencyLimiter.CapacityReachedException.class, e.getClass());
  }

  @Test
  public void testQueueSizeCounter() throws Exception {
    final SettableFuture<String> future = SettableFuture.create();

    final ConcurrencyLimiter<String> limiter = ConcurrencyLimiter.create(10, 10);
    for (int i = 0; i < 20; i++) {
      limiter.add(job(future));
    }

    assertEquals(10, limiter.numActive());
    assertEquals(10, limiter.numQueued());

    future.set("");

    assertEquals(0, limiter.numActive());
    assertEquals(0, limiter.numQueued());
    assertEquals(10, limiter.remainingActiveCapacity());
    assertEquals(10, limiter.remainingQueueCapacity());
  }

  private Callable<ListenableFuture<String>> job(final ListenableFuture<String> future) {
    return new Callable<ListenableFuture<String>>() {
      @Override
      public ListenableFuture<String> call() throws Exception {
        return future;
      }
    };
  }

  private static class CountingJob implements Callable<ListenableFuture<String>> {

    private final AtomicInteger activeCount;
    private final AtomicInteger maxCount;

    final SettableFuture<String> future = SettableFuture.create();

    public CountingJob(final AtomicInteger activeCount, AtomicInteger maxCount) {
      this.activeCount = activeCount;
      this.maxCount = maxCount;

      Futures.addCallback(future, new FutureCallback<String>() {
        @Override
        public void onSuccess(String result) {
          activeCount.decrementAndGet();
        }

        @Override
        public void onFailure(Throwable t) {
          activeCount.decrementAndGet();
        }
      });
    }

    @Override
    public ListenableFuture<String> call() throws Exception {
      activeCount.incrementAndGet();
      if (activeCount.get() > maxCount.get()) {
        maxCount.set(activeCount.get());
      }
      return future;
    }
  }
}

