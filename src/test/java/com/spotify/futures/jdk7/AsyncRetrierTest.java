/*
 * Copyright (c) 2014 Spotify AB
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

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.ListenableFuture;

import com.spotify.futures.AsyncRetrier;

import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class AsyncRetrierTest {

  @Mock
  Supplier<ListenableFuture<String>> fun;

  DeterministicScheduler executorService = new DeterministicScheduler();

  AsyncRetrier retrier;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    ListenableFuture<String> f1 = immediateFailedFuture(new RuntimeException("e1"));
    ListenableFuture<String> f2 = immediateFailedFuture(new RuntimeException("e2"));
    ListenableFuture<String> f3 = immediateFuture("success");
    ListenableFuture<String> f4 = immediateFuture("success!!!");

    when(fun.get()).thenReturn(f1, f2, f3, f4);


    retrier = AsyncRetrier.create(executorService);
  }

  @Test
  public void testRetry() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5, 0);

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    String s = getUninterruptibly(retry);

    assertEquals("success", s);
  }

  @Test
  public void testRetryDelayMillis() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5, 100);

    executorService.tick(199, MILLISECONDS);
    assertFalse(retry.isDone());

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    String s = getUninterruptibly(retry);

    assertEquals("success", s);
  }

  @Test
  public void testRetryWithDefaultDelay() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5);

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    String s = getUninterruptibly(retry);

    assertEquals("success", s);
  }

  @Test
  public void testRetryDelayTimeUnit() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5, 1, SECONDS);

    executorService.tick(1999, MILLISECONDS);
    assertFalse(retry.isDone());

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    String s = getUninterruptibly(retry);

    assertEquals("success", s);
  }

  @Test
  public void testImmediateSuccess() throws Exception {
    reset(fun);
    when(fun.get()).thenReturn(immediateFuture("direct success"));

    ListenableFuture<String> retry = retrier.retry(fun, 5, 100);

    assertTrue(retry.isDone());

    String s = getUninterruptibly(retry);

    assertEquals("direct success", s);
  }

  @Test
  public void testSupplierThrows() throws Exception {
    reset(fun);
    when(fun.get())
            .thenThrow(RuntimeException.class)
            .thenReturn(immediateFuture("success"));

    ListenableFuture<String> retry = retrier.retry(fun, 5, 100);

    executorService.tick(99, MILLISECONDS);
    assertFalse(retry.isDone());

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    String s = getUninterruptibly(retry);

    assertEquals("success", s);
  }

  @Test
  public void testRetryFailing() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 1, 0);

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    try {
      getUninterruptibly(retry);
      fail();
    } catch (Exception e) {
      assertEquals("e2", e.getCause().getMessage());
    }
  }

  @Test
  public void testRetryFailureOnCustomPredicate() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 2, 0, SECONDS, successPredicate());

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    try {
      getUninterruptibly(retry);
      fail();
    } catch (Exception e) {
      assertEquals("Failed retry condition", e.getCause().getMessage());
    }
  }

  @Test
  public void testRetrySuccessOnCustomPredicate() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 3, 0, SECONDS, successPredicate());

    executorService.tick(1, MILLISECONDS);
    assertTrue(retry.isDone());

    assertEquals("success!!!", getUninterruptibly(retry));
  }

  private Predicate<String> successPredicate() {
    return new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        return input.equals("success!!!");
      }
    };
  }
}
