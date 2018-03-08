package com.spotify.futures.jdk8;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import com.spotify.futures.CompletableFuturesExtra;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.spotify.futures.CompletableFuturesExtra.toCompletableFuture;
import static com.spotify.futures.CompletableFuturesExtra.toListenableFuture;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CompletableFuturesExtraTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Mock FutureCallback<String> callback;

  private final SettableFuture<String> settable = SettableFuture.create();
  private final ListenableFuture<String> listenable = settable;

  @Before
  public void setup() {
    assumeThat(hasCompletableFuture(), is(true));
  }

  @Test
  public void testToCompletableFutureUnwrap() {
    final CompletableFuture<String> wrapped = toCompletableFuture(listenable);
    final ListenableFuture<String> unwrapped = toListenableFuture(wrapped);
    assertThat(unwrapped, is(sameInstance(listenable)));
  }

  @Test
  public void testToCompletableFutureUnwrapWithStage() {
    final CompletionStage<String> wrapped = toCompletableFuture(listenable);
    final ListenableFuture<String> unwrapped = toListenableFuture(wrapped);
    assertThat(unwrapped, is(sameInstance(listenable)));
  }

  @Test
  public void testToCompletableFutureSuccess() throws ExecutionException, InterruptedException {
    @SuppressWarnings("unchecked") final BiConsumer<String, Throwable> consumer = mock(BiConsumer.class);
    final CompletableFuture<String> wrapped = toCompletableFuture(listenable);
    wrapped.whenComplete(consumer);
    assertThat(wrapped.isDone(), is(false));
    settable.set("done");
    assertThat(wrapped.isDone(), is(true));
    verify(consumer).accept("done", null);
    assertThat(wrapped.get(), is("done"));
  }

  @Test
  public void testToCompletableFutureFailure() {
    @SuppressWarnings("unchecked") final BiConsumer<String, Throwable> consumer = mock(BiConsumer.class);
    final CompletableFuture<String> wrapped = toCompletableFuture(listenable);
    wrapped.whenComplete(consumer);
    assertThat(wrapped.isDone(), is(false));
    final Exception failure = new Exception("failure");
    settable.setException(failure);
    assertThat(wrapped.isDone(), is(true));
    verify(consumer).accept(null, failure);
    exception.expect(Exception.class);
    wrapped.getNow("absent");
  }

  @Test
  public void testToListenableFutureUnwrap() {
    final CompletableFuture<String> completable = new CompletableFuture<String>();
    final ListenableFuture<String> wrapped = toListenableFuture(completable);
    final CompletableFuture<String> unwrapped = toCompletableFuture(wrapped);
    assertThat(unwrapped, is(sameInstance(completable)));
  }

  @Test
  public void testException() throws Exception {
    final CompletableFuture<Object> future = new CompletableFuture<Object>();
    future.completeExceptionally(new IllegalStateException());
    final ListenableFuture<Object> converted = toListenableFuture(future);
    try {
      converted.get();
      fail("Should have failed");
    } catch (final ExecutionException e) {
      assertEquals(IllegalStateException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testToListenableFutureSuccess() throws ExecutionException, InterruptedException {
    final CompletableFuture<String> completable = new CompletableFuture<String>();
    final ListenableFuture<String> listenable = toListenableFuture(completable);
    Futures.addCallback(listenable, callback);
    assertThat(listenable.isDone(), is(false));
    completable.complete("done");
    assertThat(listenable.isDone(), is(true));
    verify(callback).onSuccess("done");
    assertThat(listenable.get(), is("done"));
  }

  @Test
  public void testToListenableFutureFailure() throws ExecutionException, InterruptedException {
    final CompletableFuture<String> completable = new CompletableFuture<String>();
    final ListenableFuture<String> wrapped = toListenableFuture(completable);
    Futures.addCallback(wrapped, callback);
    assertThat(wrapped.isDone(), is(false));
    final Exception failure = new Exception("failure");
    completable.completeExceptionally(failure);
    assertThat(wrapped.isDone(), is(true));
    verify(callback).onFailure(failure);
    exception.expect(ExecutionException.class);
    wrapped.get();
  }

  private static boolean hasCompletableFuture() {
    try {
      Class.forName("java.util.concurrent.CompletableFuture");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testImmediateFailed() throws Exception {
    final CompletionStage<Object> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());
    CompletableFuturesExtra.getCompleted(future);
    fail();
  }

  @Test
  public void testGetCompleted() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");
    assertEquals("hello", CompletableFuturesExtra.getCompleted(future));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetCompletedFails() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<String>();
    CompletableFuturesExtra.getCompleted(future);
    fail();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDereferenceFailure() throws Exception {
    final CompletionStage<Object> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());
    final CompletionStage<CompletionStage<Object>> future2 = CompletableFuture.completedFuture(future);
    final CompletionStage<Object> dereferenced = CompletableFuturesExtra.dereference(future2);
    CompletableFuturesExtra.getCompleted(dereferenced.toCompletableFuture());
    fail();
  }

  @Test(expected = NullPointerException.class)
  public void testDereferenceNull() throws Exception {
    final CompletionStage<CompletableFuture<Object>> future2 = CompletableFuture.completedFuture(null);
    final CompletionStage<Object> dereferenced = CompletableFuturesExtra.dereference(future2);
    CompletableFuturesExtra.getCompleted(dereferenced);
    fail();
  }

  @Test
  public void testDereferenceSuccess() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");
    final CompletionStage<CompletionStage<String>> future2 = CompletableFuture.completedFuture(future);
    final CompletionStage<String> dereferenced = CompletableFuturesExtra.dereference(future2);
    assertEquals("hello", CompletableFuturesExtra.getCompleted(dereferenced));
  }

  @Test
  public void testExceptionallyCompose() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return CompletableFuture.completedFuture("hello");
      }
    });

    assertEquals("hello", CompletableFuturesExtra.getCompleted(composed));

  }

  @Test(expected = IllegalStateException.class)
  public void testExceptionallyComposeFailure() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalStateException());
      }
    });
    CompletableFuturesExtra.getCompleted(composed);
    fail();
  }

  @Test
  public void testExceptionallyComposeUnused() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalStateException());
      }
    });
    assertEquals("hello", CompletableFuturesExtra.getCompleted(composed));
  }

  @Test(expected = IllegalStateException.class)
  public void testExceptionallyComposeThrows() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        throw new IllegalStateException();
      }
    });
    CompletableFuturesExtra.getCompleted(composed);
    fail();
  }

  @Test(expected = NullPointerException.class)
  public void testExceptionallyComposeReturnsNull() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, new Function<Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(Throwable throwable) {
        return null;
      }
    });
    CompletableFuturesExtra.getCompleted(composed);
    fail();
  }

  @Test
  public void testHandleCompose() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future, new BiFunction<String, Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(String s, Throwable throwable) {
        return CompletableFuture.completedFuture("hello");
      }
    });

    assertEquals("hello", CompletableFuturesExtra.getCompleted(composed));

  }

  @Test(expected = IllegalStateException.class)
  public void testHandleComposeFailure() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future, new BiFunction<String, Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(String s, Throwable throwable) {
        return CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalStateException());
      }
    });
    CompletableFuturesExtra.getCompleted(composed);
    fail();
  }

  @Test(expected = IllegalStateException.class)
  public void testHandleComposeThrows() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future, new BiFunction<String, Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(String s, Throwable throwable) {
        throw new IllegalStateException();
      }
    });
    CompletableFuturesExtra.getCompleted(composed);
    fail();
  }

  @Test(expected = NullPointerException.class)
  public void testHandleComposeReturnsNull() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future, new BiFunction<String, Throwable, CompletionStage<String>>() {
      @Override
      public CompletionStage<String> apply(String s, Throwable throwable) {
        return null;
      }
    });
    CompletableFuturesExtra.getCompleted(composed);
    fail();
  }

  @Test
  public void testCancelCompletableFuture() throws Exception {
    final CompletableFuture<Object> future = new CompletableFuture<Object>();
    final ListenableFuture<Object> converted = toListenableFuture(future);
    future.cancel(false);
    assertTrue(converted.isDone());
    assertTrue(converted.isCancelled());
  }

  @Test
  public void testCancelListenableFuture() throws Exception {
    final SettableFuture<Object> future = SettableFuture.create();
    final CompletableFuture<Object> converted = toCompletableFuture(future);
    future.cancel(false);
    assertTrue(converted.isDone());
    assertTrue(converted.isCancelled());
  }
}
