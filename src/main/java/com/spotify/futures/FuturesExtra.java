package com.spotify.futures;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FuturesExtra {
  public static <Z, A, B> ListenableFuture<Z> transform(ListenableFuture<A> a,
                                                        ListenableFuture<B> b,
                                                        final Function2<Z, A, B> function) {
    return transform(Arrays.asList(a, b), new Function<Z>() {
      @Override
      public Z apply(Object[] results) {
        return function.apply((A) results[0], (B) results[1]);
      }
    });
  }

  public static interface Function2<Z, A, B> {
    Z apply(A a, B b);
  }

  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final Function3<Z, A, B, C> function) {
    return transform(Arrays.asList(a, b, c), new Function<Z>() {
      @Override
      public Z apply(Object[] results) {
        return function.apply((A) results[0], (B) results[1], (C) results[2]);
      }
    });
  }

  public static interface Function3<Z, A, B, C> {
    Z apply(A a, B b, C c);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final Function4<Z, A, B, C, D> function) {
    return transform(Arrays.asList(a, b, c, d), new Function<Z>() {
      @Override
      public Z apply(Object[] results) {
        return function.apply((A) results[0], (B) results[1], (C) results[2], (D) results[3]);
      }
    });
  }

  public static interface Function4<Z, A, B, C, D> {
    Z apply(A a, B b, C c, D d);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final Function5<Z, A, B, C, D, E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new Function<Z>() {
      @Override
      public Z apply(Object[] results) {
        return function.apply((A) results[0], (B) results[1], (C) results[2], (D) results[3], (E) results[4]);
      }
    });
  }

  public static interface Function5<Z, A, B, C, D, E> {
    Z apply(A a, B b, C c, D d, E e);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs, final Function<Z> function) {
    final SettableFuture<Z> result = SettableFuture.create();
    final Object[] values = new Object[inputs.size()];
    final AtomicInteger countdown = new AtomicInteger(inputs.size());
    for (int i = 0; i < inputs.size(); i++) {
      final int finalI = i;
      ListenableFuture<?> input = inputs.get(finalI);
      Futures.addCallback(input, new FutureCallback<Object>() {
        @Override
        public void onSuccess(Object o) {
          values[finalI] = o;
          if (countdown.decrementAndGet() == 0) {
            result.set(function.apply(values));
          }
        }

        @Override
        public void onFailure(Throwable throwable) {
          result.setException(throwable);
        }
      });
    }
    return result;
  }

  private static interface Function<Z> {
    Z apply(Object[] results);
  }
}
