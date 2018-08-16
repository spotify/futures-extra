package com.spotify.futures;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.CompletionException;
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
    final CompletableFuture<String> completable = new CompletableFuture<>();
    final ListenableFuture<String> wrapped = toListenableFuture(completable);
    final CompletableFuture<String> unwrapped = toCompletableFuture(wrapped);
    assertThat(unwrapped, is(sameInstance(completable)));
  }

  @Test
  public void testException() throws Exception {
    final CompletableFuture<Object> future = new CompletableFuture<>();
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
    final CompletableFuture<String> completable = new CompletableFuture<>();
    final ListenableFuture<String> listenable = toListenableFuture(completable);
    Futures.addCallback(listenable, callback, MoreExecutors.directExecutor());
    assertThat(listenable.isDone(), is(false));
    completable.complete("done");
    assertThat(listenable.isDone(), is(true));
    verify(callback).onSuccess("done");
    assertThat(listenable.get(), is("done"));
  }

  @Test
  public void testToListenableFutureFailure() throws ExecutionException, InterruptedException {
    final CompletableFuture<String> completable = new CompletableFuture<>();
    final ListenableFuture<String> wrapped = toListenableFuture(completable);
    Futures.addCallback(wrapped, callback, MoreExecutors.directExecutor());
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

  @Test
  public void testImmediateFailed() throws Exception {
    final CompletionStage<Object> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());
    try {
      CompletableFuturesExtra.getCompleted(future.toCompletableFuture());
      fail();
    } catch (final CompletionException e) {
      assertEquals(IllegalArgumentException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testGetCompleted() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");
    assertEquals("hello", CompletableFuturesExtra.getCompleted(future));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetCompletedFails() throws Exception {
    final CompletionStage<String> future = new CompletableFuture<>();
    CompletableFuturesExtra.getCompleted(future.toCompletableFuture());
    fail();
  }

  @Test
  public void testDereferenceFailure() throws Exception {
    final CompletionStage<Object> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());
    final CompletionStage<CompletionStage<Object>> future2 = CompletableFuture.completedFuture(future);
    final CompletionStage<Object> dereferenced = CompletableFuturesExtra.dereference(future2);
    try {
      CompletableFuturesExtra.getCompleted(dereferenced.toCompletableFuture());
      fail();
    } catch (final CompletionException e) {
      assertEquals(IllegalArgumentException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testDereferenceNull() throws Exception {
    final CompletionStage<CompletableFuture<Object>> future2 = CompletableFuture.completedFuture(null);
    final CompletionStage<Object> dereferenced = CompletableFuturesExtra.dereference(future2);
    try {
      CompletableFuturesExtra.getCompleted(dereferenced.toCompletableFuture());
      fail();
    } catch (final CompletionException e) {
      assertEquals(NullPointerException.class, e.getCause().getClass());
    }
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

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future,
        throwable -> CompletableFuture.completedFuture("hello"));

    assertEquals("hello", CompletableFuturesExtra.getCompleted(composed));

  }

  @Test
  public void testExceptionallyComposeFailure() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future,
        throwable -> CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalStateException()));

    try {
      CompletableFuturesExtra.getCompleted(composed);
      fail();
    } catch (final CompletionException e) {
      assertEquals(IllegalStateException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testExceptionallyComposeUnused() throws Exception {
    final CompletionStage<String> future = CompletableFuture.completedFuture("hello");

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future,
        throwable -> CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalStateException()));
    assertEquals("hello", CompletableFuturesExtra.getCompleted(composed));
  }

  @Test
  public void testExceptionallyComposeThrows() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, throwable -> {
      throw new IllegalStateException();
    });
    try {
      CompletableFuturesExtra.getCompleted(composed);
      fail();
    } catch (final CompletionException e) {
      assertEquals(IllegalStateException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testExceptionallyComposeReturnsNull() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.exceptionallyCompose(future, throwable -> null);
    try {
      CompletableFuturesExtra.getCompleted(composed);
      fail();
    } catch (final CompletionException e) {
      assertEquals(NullPointerException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testHandleCompose() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future,
        (s, throwable) -> CompletableFuture.completedFuture("hello"));

    assertEquals("hello", CompletableFuturesExtra.getCompleted(composed));

  }

  @Test
  public void testHandleComposeFailure() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future,
        (s, throwable) -> CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalStateException()));
    try {
      CompletableFuturesExtra.getCompleted(composed);
      fail();
    } catch (final CompletionException e) {
      assertEquals(IllegalStateException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testHandleComposeThrows() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future, (s, throwable) -> {
      throw new IllegalStateException();
    });
    try {
      CompletableFuturesExtra.getCompleted(composed);
      fail();
    } catch (final CompletionException e) {
      assertEquals(IllegalStateException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testHandleComposeReturnsNull() throws Exception {
    final CompletionStage<String> future = CompletableFuturesExtra.exceptionallyCompletedFuture(new IllegalArgumentException());

    final CompletionStage<String> composed = CompletableFuturesExtra.handleCompose(future, (s, throwable) -> null);
    try {
      CompletableFuturesExtra.getCompleted(composed);
      fail();
    } catch (final CompletionException e) {
      assertEquals(NullPointerException.class, e.getCause().getClass());
    }
  }

  @Test
  public void testCancelCompletableFuture() throws Exception {
    final CompletableFuture<Object> future = new CompletableFuture<>();
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
