package com.spotify.futures.jdk8;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

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
import static org.junit.Assert.assertThat;
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
}
