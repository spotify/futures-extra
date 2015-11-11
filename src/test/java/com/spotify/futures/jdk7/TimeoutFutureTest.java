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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import com.spotify.futures.FuturesExtra;

import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TimeoutFutureTest {

  private DeterministicScheduler scheduler;

  @Before
  public void setUp() throws Exception {
    scheduler = new DeterministicScheduler();
  }

  @Test
  public void testSuccess() throws Exception {
    SettableFuture<String> future = SettableFuture.create();
    ListenableFuture<String> timeoutFuture = FuturesExtra.makeTimeoutFuture(scheduler, future, 10, TimeUnit.MILLISECONDS);
    scheduler.tick(3, TimeUnit.MILLISECONDS);
    future.set("value");
    scheduler.tick(3, TimeUnit.MILLISECONDS);
    assertEquals("value", timeoutFuture.get());
  }

  @Test
  public void testException() throws Exception {
    SettableFuture<String> future = SettableFuture.create();
    ListenableFuture<String> timeoutFuture = FuturesExtra.makeTimeoutFuture(scheduler, future, 1000, TimeUnit.MILLISECONDS);
    scheduler.tick(3, TimeUnit.MILLISECONDS);
    future.setException(new IllegalArgumentException());
    scheduler.tick(3, TimeUnit.MILLISECONDS);
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
    ListenableFuture<String> timeoutFuture = FuturesExtra.makeTimeoutFuture(scheduler, future, 10, TimeUnit.MILLISECONDS);
    scheduler.tick(100, TimeUnit.MILLISECONDS);
    future.set("Success too late");
    scheduler.tick(100, TimeUnit.MILLISECONDS);
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
