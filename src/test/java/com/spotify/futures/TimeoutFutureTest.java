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
