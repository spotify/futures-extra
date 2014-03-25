package com.spotify.futures;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
  public void testSuccessfulSelect() throws Exception {
    final SettableFuture<String> f1 = SettableFuture.create();
    final SettableFuture<String> f2 = SettableFuture.create();
    final SettableFuture<String> f3 = SettableFuture.create();
    f1.set("value");

    final ListenableFuture<String> zealous = FuturesExtra.select(Lists.<ListenableFuture<String>>newArrayList(f1, f2, f3));
    assertTrue(zealous.get().equals("value"));
  }

  @Test(expected = ExecutionException.class)
  public void testAllFailedSelect() throws Exception {
    final SettableFuture<String> f1 = SettableFuture.create();
    final SettableFuture<String> f2 = SettableFuture.create();
    final SettableFuture<String> f3 = SettableFuture.create();
    f1.setException(new Exception());
    f2.setException(new Exception());
    f3.setException(new Exception());

    final ListenableFuture<String> zealous = FuturesExtra.select(Arrays.<ListenableFuture<String>>asList(f1, f2, f3));
    zealous.get(); // will throw Exception
  }

  @Test()
  public void testOneSuccessfulSelect() throws Exception {
    final SettableFuture<String> f1 = SettableFuture.create();
    final SettableFuture<String> f2 = SettableFuture.create();
    final SettableFuture<String> f3 = SettableFuture.create();
    f1.setException(new Exception());
    f2.set("value");
    f3.setException(new Exception());
    final ListenableFuture<String> zealous = FuturesExtra.select(Arrays.<ListenableFuture<String>>asList(f1, f2, f3));
    assertTrue(zealous.get().equals("value"));
  }

  @Test(expected = ExecutionException.class)
  public void testSelectWithEmptyList() throws ExecutionException, InterruptedException {
    final ListenableFuture<String> f = FuturesExtra.select(Collections.<ListenableFuture<String>>emptyList());
    f.get(); // will throw Exception
  }

  @Test(expected = NullPointerException.class)
  public void testSelectWithNullList() throws ExecutionException, InterruptedException {
    final ListenableFuture<String> f = FuturesExtra.select(null);
    f.get(); // will throw Exception
  }

}
