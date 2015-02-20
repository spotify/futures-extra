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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TimeoutFutureTest {

  private ScheduledExecutorService threadpool;

  @Before
  public void setUp() throws Exception {
    threadpool = Executors.newScheduledThreadPool(1);
  }

  @After
  public void tearDown() throws Exception {
    threadpool.shutdown();
  }

  @Test
  public void testSuccess() throws Exception {
    SettableFuture<String> future = SettableFuture.create();
    ListenableFuture<String> timeoutFuture = FuturesExtra.makeTimeoutFuture(threadpool, future, 10, TimeUnit.MILLISECONDS);
    future.set("value");
    assertEquals("value", timeoutFuture.get());
  }

  @Test
  public void testException() throws Exception {
    SettableFuture<String> future = SettableFuture.create();
    ListenableFuture<String> timeoutFuture = FuturesExtra.makeTimeoutFuture(threadpool, future, 1000, TimeUnit.MILLISECONDS);
    future.setException(new IllegalArgumentException());
    try {
      timeoutFuture.get();
      fail();
    } catch (InterruptedException e) {
      fail();
    } catch (ExecutionException e) {
      assertEquals(IllegalArgumentException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testTimeout() throws Exception {
    SettableFuture<String> future = SettableFuture.create();
    ListenableFuture<String> timeoutFuture = FuturesExtra.makeTimeoutFuture(threadpool, future, 10, TimeUnit.MILLISECONDS);
    try {
      timeoutFuture.get();
      fail();
    } catch (InterruptedException e) {
      fail();
    } catch (ExecutionException e) {
      assertEquals(TimeoutException.class, e.getCause().getClass());
    }
  }
}
