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

package com.spotify.futures;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AsyncRetrierTest {

  @Mock
  Supplier<ListenableFuture<String>> fun;

  @Mock
  ScheduledExecutorService executorService;

  AsyncRetrier retrier;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    ListenableFuture<String> f1 = immediateFailedFuture(new RuntimeException("e1"));
    ListenableFuture<String> f2 = immediateFailedFuture(new RuntimeException("e2"));
    ListenableFuture<String> f3 = immediateFuture("success");
    ListenableFuture<String> f4 = immediateFuture("success!!!");

    when(fun.get()).thenReturn(f1, f2, f3, f4);

    when(executorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .then(new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            Runnable command = (Runnable) invocation.getArguments()[0];
            command.run();
            return null;
          }
        });

    retrier = AsyncRetrier.create(executorService);
  }

  @Test
  public void testRetry() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5, 0);
    String s = getUninterruptibly(retry);

    assertEquals("success", s);
    verifyZeroInteractions(executorService);
  }

  @Test
  public void testRetryDelayMillis() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5, 100);
    String s = getUninterruptibly(retry);

    assertEquals("success", s);
    verify(executorService, times(2))
        .schedule(any(Runnable.class), eq(100L), eq(MILLISECONDS));
  }

  @Test
  public void testRetryDelayTimeUnit() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 5, 1, SECONDS);
    String s = getUninterruptibly(retry);

    assertEquals("success", s);
    verify(executorService, times(2))
        .schedule(any(Runnable.class), eq(1L), eq(SECONDS));
  }

  @Test
  public void testImmediateSuccess() throws Exception {
    reset(fun);
    when(fun.get()).thenReturn(immediateFuture("direct success"));

    ListenableFuture<String> retry = retrier.retry(fun, 5, 100);
    String s = getUninterruptibly(retry);

    assertEquals("direct success", s);
    verifyZeroInteractions(executorService);
  }

  @Test
  public void testRetryFailing() throws Exception {
    ListenableFuture<String> retry = retrier.retry(fun, 1, 0);

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
