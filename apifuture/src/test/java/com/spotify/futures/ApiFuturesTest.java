package com.spotify.futures;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.spotify.futures.ApiFuturesExtra.toApiFuture;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ApiFuturesTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Mock ApiFutureCallback<String> apiFutureCallback;

  private final SettableFuture<String> settable = SettableFuture.create();
  private final ListenableFuture<String> listenable = settable;

  @Before
  public void setup() {
    assumeThat(hasCompletableFuture(), is(true));
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
  public void testToApiFutureUnwrap() {
    final CompletableFuture<String> completable = new CompletableFuture<>();
    final ApiFuture<String> wrapped = toApiFuture(completable);
    final CompletableFuture<String> unwrapped = ApiFuturesExtra.toCompletableFuture(wrapped);
    assertThat(unwrapped, is(sameInstance(completable)));
  }

  @Test
  public void testToCompletableFutureFromApiFutureUnwrap() {
    final ApiFuture<String> apiFuture = SettableApiFuture.create();
    final CompletableFuture<String> wrapped = ApiFuturesExtra.toCompletableFuture(apiFuture);
    final ApiFuture<String> unwrapped = toApiFuture(wrapped);
    assertThat(unwrapped, is(sameInstance(apiFuture)));
  }

  @Test
  public void testToApiFutureSuccess() throws ExecutionException, InterruptedException {
    final CompletableFuture<String> completable = new CompletableFuture<>();
    final ApiFuture<String> wrapped = toApiFuture(completable);
    ApiFutures.addCallback(wrapped, apiFutureCallback, MoreExecutors.directExecutor());
    assertThat(wrapped.isDone(), is(false));
    final String value = "value";
    completable.complete(value);
    assertThat(wrapped.isDone(), is(true));
    wrapped.get();
    assertThat(wrapped.get(), is(value));
  }

  @Test
  public void testToApiFutureFailure() throws ExecutionException, InterruptedException {
    final CompletableFuture<String> completable = new CompletableFuture<>();
    final ApiFuture<String> wrapped = toApiFuture(completable);
    ApiFutures.addCallback(wrapped, apiFutureCallback, MoreExecutors.directExecutor());
    assertThat(wrapped.isDone(), is(false));
    final Exception failure = new Exception("failure");
    completable.completeExceptionally(failure);
    assertThat(wrapped.isDone(), is(true));
    verify(apiFutureCallback).onFailure(failure);
    exception.expect(ExecutionException.class);
    wrapped.get();
  }

  @Test
  public void testApiFutureSuccess() throws ExecutionException, InterruptedException {
    final SettableApiFuture<String> apiFuture = SettableApiFuture.create();
    final CompletableFuture<String> completable = ApiFuturesExtra.toCompletableFuture(apiFuture);
    assertThat(completable.isDone(), is(false));
    apiFuture.set("done");
    assertThat(completable.isDone(), is(true));
    assertThat(completable.get(), is("done"));
  }

  @Test
  public void testApiFutureFailure() throws ExecutionException, InterruptedException {
    final SettableApiFuture<String> apiFuture = SettableApiFuture.create();
    final CompletableFuture<String> completable = ApiFuturesExtra.toCompletableFuture(apiFuture);
    assertThat(completable.isDone(), is(false));
    final Exception failure = new Exception("failure");
    apiFuture.setException(failure);
    assertThat(completable.isDone(), is(true));
    exception.expect(ExecutionException.class);
    completable.get();
  }

}
