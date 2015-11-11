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
package com.spotify.futures.jdk7;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;

import com.spotify.futures.FuturesExtra;
import com.spotify.futures.FuturesExtra.Consumer;
import com.spotify.futures.Validator;

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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    ListenableFuture<Integer> result = FuturesExtra.syncTransform2(a, b, new FuturesExtra.Function2<Integer, Number, Number>() {
      @Override
      public Integer apply(Number s, Number s2) {
        return s.intValue() + s2.intValue();
      }
    });
    assertEquals(42 + 17, result.get().intValue());
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
  public void testCallbackForSuccess() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Integer> success = mock(Consumer.class);
    final Consumer<Throwable> failure = mock(Consumer.class);

    FuturesExtra.addCallback(future, success, failure);

    future.set(10);
    verify(success).accept(10);
    verify(failure, never()).accept(any(Throwable.class));
  }

  @Test
  public void testCallbackForSuccessNullFailure() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Integer> success = mock(Consumer.class);

    FuturesExtra.addCallback(future, success, null);

    future.set(10);
    verify(success).accept(10);
  }

  @Test
  public void testCallbackForFailure() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Integer> success = mock(Consumer.class);
    final Consumer<Throwable> failure = mock(Consumer.class);

    FuturesExtra.addCallback(future, success, failure);

    final Throwable expected = new RuntimeException("boom");
    future.setException(expected);
    verify(failure).accept(expected);
    verify(success, never()).accept(anyInt());
  }

  @Test
  public void testCallbackForFailureNullSuccess() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Throwable> failure = mock(Consumer.class);

    FuturesExtra.addCallback(future, null, failure);

    final Throwable expected = new RuntimeException("boom");
    future.setException(expected);
    verify(failure).accept(expected);
  }

  @Test(expected = NullPointerException.class)
  public void testCallbackWithNulls() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    FuturesExtra.addCallback(future, null, null);
  }

  @Test
  public void testSuccessCallback() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Integer> consumer = mock(Consumer.class);

    FuturesExtra.addSuccessCallback(future, consumer);

    future.set(10);
    verify(consumer).accept(10);
  }

  @Test
  public void testSuccessCallbackFailure() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Integer> consumer = mock(Consumer.class);

    FuturesExtra.addSuccessCallback(future, consumer);

    future.setException(new RuntimeException("boom"));
    verify(consumer, never()).accept(anyInt());
  }

  @Test
  public void testFailureCallback() throws Exception {
    final SettableFuture<Integer> future = SettableFuture.create();
    final Consumer<Throwable> consumer = mock(Consumer.class);

    FuturesExtra.addFailureCallback(future, consumer);

    final Throwable expected = new RuntimeException("boom");
    future.setException(expected);
    verify(consumer).accept(expected);
  }

  @Test
  public void testFailureCallbackSuccess() throws Exception {
    final SettableFuture<Long> future = SettableFuture.create();
    final Consumer<Throwable> consumer = mock(Consumer.class);

    FuturesExtra.addFailureCallback(future, consumer);

    future.set(42l);
    verify(consumer, never()).accept(any(Throwable.class));
  }

  @Test
  public void testSyncTransform() throws Exception {
    ListenableFuture<String> future = Futures.immediateFuture("a");
    assertEquals("aa", FuturesExtra.syncTransform(future,
            new Function<String, String>() {
              @Override
              public String apply(String s) {
                return s + s;
              }
            }).get());
  }

  @Test
  public void testAsyncTransform() throws Exception {
    ListenableFuture<String> future = Futures.immediateFuture("a");
    assertEquals("aa", FuturesExtra.asyncTransform(future,
            new AsyncFunction<String, String>() {
              @Override
              public ListenableFuture<String> apply(String s) {
                return Futures.immediateFuture(s + s);
              }
            }).get());
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

  @Test(expected = ExecutionException.class)
  public void testAsyncException2() throws Exception {
    ListenableFuture<String> future = FuturesExtra.asyncTransform2(Futures.immediateFuture("A"), Futures.immediateFuture("B"),
            new FuturesExtra.AsyncFunction2<String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b) throws Exception {
                throw new Exception("foo");
              }
            });
    future.get();
  }

  @Test(expected = ExecutionException.class)
  public void testAsyncException3() throws Exception {
    ListenableFuture<String> future = FuturesExtra.asyncTransform3(
            Futures.immediateFuture("A"), Futures.immediateFuture("B"), Futures.immediateFuture("C"),
            new FuturesExtra.AsyncFunction3<String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b, String c) throws Exception {
                throw new Exception("foo");
              }
            });
    future.get();
  }
  @Test(expected = ExecutionException.class)
  public void testAsyncException4() throws Exception {
    ListenableFuture<String> future = FuturesExtra.asyncTransform4(
            Futures.immediateFuture("A"), Futures.immediateFuture("B"),
            Futures.immediateFuture("C"), Futures.immediateFuture("D"),
            new FuturesExtra.AsyncFunction4<String, String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b, String c, String d) throws Exception {
                throw new Exception("foo");
              }
            });
    future.get();
  }
  @Test(expected = ExecutionException.class)
  public void testAsyncException5() throws Exception {
    ListenableFuture<String> future = FuturesExtra.asyncTransform5(
            Futures.immediateFuture("A"), Futures.immediateFuture("B"),
            Futures.immediateFuture("C"), Futures.immediateFuture("D"),
            Futures.immediateFuture("E"),
            new FuturesExtra.AsyncFunction5<String, String, String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b, String c, String d, String e) throws Exception {
                throw new Exception("foo");
              }
            });
    future.get();
  }

  @Test(expected = ExecutionException.class)
  public void testAsyncException6() throws Exception {
    ListenableFuture<String> future = FuturesExtra.asyncTransform6(
            Futures.immediateFuture("A"), Futures.immediateFuture("B"),
            Futures.immediateFuture("C"), Futures.immediateFuture("D"),
            Futures.immediateFuture("E"), Futures.immediateFuture("F"),
            new FuturesExtra.AsyncFunction6<String, String, String, String, String, String, String>() {
              @Override
              public ListenableFuture<String> apply(String a, String b, String c, String d, String e, String f) throws Exception {
                throw new Exception("foo");
              }
            });
    future.get();
  }

  @Test
  public void testCheckCompleted() throws Exception {
    FuturesExtra.checkCompleted(Futures.immediateFuture("hello"));
    FuturesExtra.checkCompleted(Futures.immediateFailedFuture(new RuntimeException()));
  }

  @Test(expected = IllegalStateException.class)
  public void testCheckCompletedFails() throws Exception {
    FuturesExtra.checkCompleted(SettableFuture.create());
  }

  @Test
  public void testGetCompleted() throws Exception {
    assertEquals("hello", FuturesExtra.getCompleted(Futures.immediateFuture("hello")));
  }

  @Test(expected = UncheckedExecutionException.class)
  public void testGetCompletedThrows() throws Exception {
    FuturesExtra.getCompleted(Futures.immediateFailedFuture(new ArrayIndexOutOfBoundsException()));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetCompletedNotComplete() throws Exception {
    FuturesExtra.getCompleted(SettableFuture.create());
  }

  @Test
  public void testGetException() throws Exception {
    assertEquals(null, FuturesExtra.getException(Futures.immediateFuture("hello")));
  }

  @Test
  public void testGetExceptionThrows() throws Exception {
    ArrayIndexOutOfBoundsException t = new ArrayIndexOutOfBoundsException();
    assertEquals(t, FuturesExtra.getException(Futures.immediateFailedFuture(t)));
  }

  @Test(expected = IllegalStateException.class)
  public void testExceptiondNotComplete() throws Exception {
    FuturesExtra.getException(SettableFuture.create());
  }
}
