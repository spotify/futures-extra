/*
 * Copyright (c) 2013-2014 Spotify AB
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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static com.spotify.futures.FuturesExtra.fastFail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FuturesExtraTest {
  @Test
  public void testSimple() throws Exception {
    ListenableFuture<String> a = Futures.immediateFuture("a");
    ListenableFuture<String> b = Futures.immediateFuture("b");
    ListenableFuture<String> c = Futures.immediateFuture("c");
    ListenableFuture<String> result = FuturesExtra.syncTransform3(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
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
    ListenableFuture<String> result = FuturesExtra.syncTransform3(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });

    ListenableFuture<String> d = Futures.immediateFuture("d");
    ListenableFuture<String> result2 = FuturesExtra.syncTransform3(a, result, d, new FuturesExtra.Function3<String, String, String, String>() {
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
    ListenableFuture<String> result = FuturesExtra.syncTransform3(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
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
    ListenableFuture<String> result = FuturesExtra.syncTransform3(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
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
    ListenableFuture<String> result = FuturesExtra.syncTransform3(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
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
    ListenableFuture<String> result = FuturesExtra.syncTransform3(a, b, c, new FuturesExtra.Function3<String, String, String, String>() {
      @Override
      public String apply(String s, String s2, String s3) {
        return s + s2 + s3;
      }
    });

    ListenableFuture<String> d = Futures.immediateFuture("d");
    ListenableFuture<String> result2 = FuturesExtra.syncTransform3(a, result, d, new FuturesExtra.Function3<String, String, String, String>() {
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
    ListenableFuture<String> result = FuturesExtra.asyncTransform3(a, b, c, new FuturesExtra.AsyncFunction3<String, String, String, String>() {
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
    ListenableFuture<Integer> result = FuturesExtra.syncTransform2(a, b,  new FuturesExtra.Function2<Integer, Number, Number>() {
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

  @Test
  public void testFastFail() throws Exception {
    SettableFuture<Integer> condition = SettableFuture.create();
    SettableFuture<String> value = SettableFuture.create(); // will not be set

    ListenableFuture<String> result = fastFail(condition, value, new Min7());

    condition.set(3);
    try {
      getUninterruptibly(result);
      fail();
    } catch (Exception e) {
      assertEquals("value too low", e.getCause().getMessage());
    }
  }

  @Test
  public void testFastFailSuccess() throws Exception {
    SettableFuture<Integer> condition = SettableFuture.create();
    SettableFuture<String> value = SettableFuture.create();

    ListenableFuture<String> result = fastFail(condition, value, new Min7());

    condition.set(7);
    assertFalse(result.isDone());

    value.set("done now");
    String s = getUninterruptibly(result);
    assertEquals("done now", s);
  }

  static class Min7 implements Validator<Integer> {
    @Override
    public void validate(Integer value) throws Exception {
      if (value < 7) {
        throw new RuntimeException("value too low");
      }
    }
  }

  @Test
  public void testSyncTransform2() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    assertEquals("ab", FuturesExtra.syncTransform2(futureA, futureB,
            new FuturesExtra.Function2<String, String, String>() {
              @Override
              public String apply(String a, String b) {
                return a + b;
              }
            }
    ).get());
  }

  @Test
  public void testAsyncTransform2() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    assertEquals("ab", FuturesExtra.asyncTransform2(futureA, futureB,
            new FuturesExtra.AsyncFunction2<String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b) {
                return Futures.immediateFuture(a + b);
              }
            }
    ).get());
  }

  @Test
  public void testSyncTransform3() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    assertEquals("abc", FuturesExtra.syncTransform3(futureA, futureB, futureC,
            new FuturesExtra.Function3<String, String, String, String>() {
              @Override
              public String apply(String a, String b, String c) {
                return a + b + c;
              }
            }
    ).get());
  }

  @Test
  public void testAsyncTransform3() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    assertEquals("abc", FuturesExtra.asyncTransform3(futureA, futureB, futureC,
            new FuturesExtra.AsyncFunction3<String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b, String c) {
                return Futures.immediateFuture(a + b + c);
              }
            }
    ).get());
  }

  @Test
  public void testSyncTransform4() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    ListenableFuture<String> futureD = Futures.immediateFuture("d");
    assertEquals("abcd", FuturesExtra.syncTransform4(futureA, futureB, futureC, futureD,
            new FuturesExtra.Function4<String, String, String, String, String>() {
              @Override
              public String apply(String a, String b, String c, String d) {
                return a + b + c + d;
              }
            }
    ).get());
  }

  @Test
  public void testAsyncTransform4() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    ListenableFuture<String> futureD = Futures.immediateFuture("d");
    assertEquals("abcd", FuturesExtra.asyncTransform4(futureA, futureB, futureC, futureD,
            new FuturesExtra.AsyncFunction4<String, String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b, String c, String d) {
                return Futures.immediateFuture(a + b + c + d);
              }
            }
    ).get());
  }

  @Test
  public void testSyncTransform5() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    ListenableFuture<String> futureD = Futures.immediateFuture("d");
    ListenableFuture<String> futureE = Futures.immediateFuture("e");
    assertEquals("abcde", FuturesExtra.syncTransform5(futureA, futureB, futureC, futureD, futureE,
            new FuturesExtra.Function5<String, String, String, String, String, String>() {
              @Override
              public String apply(String a, String b, String c, String d, String e) {
                return a + b + c + d + e;
              }
            }
    ).get());
  }

  @Test
  public void testAsyncTransform5() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    ListenableFuture<String> futureD = Futures.immediateFuture("d");
    ListenableFuture<String> futureE = Futures.immediateFuture("e");
    assertEquals("abcde", FuturesExtra.asyncTransform5(futureA, futureB, futureC, futureD, futureE,
            new FuturesExtra.AsyncFunction5<String, String, String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(
                      String a, String b, String c, String d, String e) {
                return Futures.immediateFuture(a + b + c + d + e);
              }
            }
    ).get());
  }

  @Test
  public void testSyncTransform6() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    ListenableFuture<String> futureD = Futures.immediateFuture("d");
    ListenableFuture<String> futureE = Futures.immediateFuture("e");
    ListenableFuture<String> futureF = Futures.immediateFuture("f");
    assertEquals("abcdef", FuturesExtra.syncTransform6(
            futureA, futureB, futureC, futureD, futureE, futureF,
            new FuturesExtra.Function6<String, String, String, String, String, String, String>() {
              @Override
              public String apply(String a, String b, String c, String d, String e, String f) {
                return a + b + c + d + e + f;
              }
            }
    ).get());
  }

  @Test
  public void testAsyncTransform6() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    ListenableFuture<String> futureC = Futures.immediateFuture("c");
    ListenableFuture<String> futureD = Futures.immediateFuture("d");
    ListenableFuture<String> futureE = Futures.immediateFuture("e");
    ListenableFuture<String> futureF = Futures.immediateFuture("f");
    assertEquals("abcdef", FuturesExtra.asyncTransform6(
            futureA, futureB, futureC, futureD, futureE, futureF,
            new FuturesExtra.AsyncFunction6<
                    String, String, String, String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(
                      String a, String b, String c, String d, String e, String f) {
                return Futures.immediateFuture(a + b + c + d + e + f);
              }
            }
    ).get());
  }
}
