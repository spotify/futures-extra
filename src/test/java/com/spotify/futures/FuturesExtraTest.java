package com.spotify.futures;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FuturesExtraTest {
  @Test
  public void testSimple() throws Exception {
    ListenableFuture<String> a = Futures.immediateFuture("a");
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFuture("c");
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });
    assertEquals("abc", result.get());
  }

  @Test
  public void testDAG() throws Exception {
    ListenableFuture<String> a = Futures.immediateFuture("a");
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFuture("c");
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });

    ListenableFuture<String> d = Futures.immediateFuture("d");
    ListenableFuture<String> result2 = FuturesExtra.transform(a, result, d, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + "|" + s2 + "|" + s3;
      }
    });
    assertEquals("a|abc|d", result2.get());
  }

  @Test
  public void testException() throws Exception {
    ListenableFuture<String> a = Futures.immediateFuture("a");
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFailedFuture(new IllegalArgumentException("my error message"));
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });

    try {
      result.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals("my error message", e.getCause().getMessage());
    }
  }

  @Test
  public void testMultipleExceptions() throws Exception {
    ListenableFuture<String> a = Futures.immediateFuture("a");
    ListenableFuture<String> b = Futures.immediateFailedFuture(new IllegalArgumentException("first error"));
    ListenableFuture<String> c = Futures.immediateFailedFuture(new IllegalArgumentException("second error"));
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });

    try {
      result.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals("first error", e.getCause().getMessage());
    }
  }

  @Test
  public void testDelayedExecution() throws Exception {
    SettableFuture<String> a = SettableFuture.create();
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFuture("c");
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });
    assertEquals(false, result.isDone());
    a.set("a");
    assertEquals("abc", result.get());
  }

  @Test
  public void testDelayedExecutionDAG() throws Exception {
    SettableFuture<String> a = SettableFuture.create();
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFuture("c");
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });

    ListenableFuture<String> d = Futures.immediateFuture("d");
    ListenableFuture<String> result2 = FuturesExtra.transform(a, result, d, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + "|" + s2 + "|" + s3;
      }
    });
    assertEquals(false, result.isDone());
    assertEquals(false, result2.isDone());
    a.set("a");
    assertEquals("a|abc|d", result2.get());
  }

  @Test
  public void testSimpleAsync() throws Exception {
    ListenableFuture<String> a = Futures.immediateFuture("a");
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFuture("c");
    ListenableFuture<String> result = FuturesExtra.transform(a, b, c, new FuturesExtra.AsyncFunction3<String, String, String, String>() {
      @Override
      public ListenableFuture<String> apply(String s, String s2, String s3) {
        return Futures.immediateFuture(s + s2 + s3);
      }
    });
    assertEquals("abc", result.get());
  }

  @Test
  public void testGenericBounds() throws Exception {
    ListenableFuture<Integer> a = Futures.immediateFuture(17);
    ListenableFuture<Integer> b = Futures.immediateFuture(42);
    ListenableFuture<Integer> result = FuturesExtra.transform(a, b,  new FuturesExtra.Function2<Integer, Number, Number>() {
      @Override
      public Integer apply(Number s, Number s2) {
        return s.intValue() + s2.intValue();
      }
    });
    assertEquals(42+17, result.get().intValue());
  }

}
