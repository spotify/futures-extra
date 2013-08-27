package com.spotify.futures;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.List;

public class FuturesExtra {
  public static <Z, A, B> ListenableFuture<Z> transform(ListenableFuture<A> a,
                                                        ListenableFuture<B> b,
                                                        final Function2<Z, A, B> function) {
    return transform(Arrays.asList(a, b), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1));
      }
    });
  }

  public static interface Function2<Z, A, B> {
    Z apply(A a, B b);
  }

  public static <Z, A, B> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          final AsyncFunction2<Z, A, B> function) {
    return transform(Arrays.asList(a, b), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1));
      }
    });
  }

  public static interface AsyncFunction2<Z, A, B> {
    ListenableFuture<Z> apply(A a, B b);
  }

  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final Function3<Z, A, B, C> function) {
    return transform(Arrays.asList(a, b, c), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2));
      }
    });
  }

  public static interface Function3<Z, A, B, C> {
    Z apply(A a, B b, C c);
  }

  public static <Z, A, B, C> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          final AsyncFunction3<Z, A, B, C> function) {
    return transform(Arrays.asList(a, b, c), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2));
      }
    });
  }

  public static interface AsyncFunction3<Z, A, B, C> {
    ListenableFuture<Z> apply(A a, B b, C c);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final Function4<Z, A, B, C, D> function) {
    return transform(Arrays.asList(a, b, c, d), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public static interface Function4<Z, A, B, C, D> {
    Z apply(A a, B b, C c, D d);
  }

  public static <Z, A, B, C, D> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          final AsyncFunction4<Z, A, B, C, D> function) {
    return transform(Arrays.asList(a, b, c, d), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3));
      }
    });
  }

  public static interface AsyncFunction4<Z, A, B, C, D> {
    ListenableFuture<Z> apply(A a, B b, C c, D d);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final Function5<Z, A, B, C, D, E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new Function<List<Object>, Z>() {
      @Override
      public Z apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public static interface Function5<Z, A, B, C, D, E> {
    Z apply(A a, B b, C c, D d, E e);
  }

  public static <Z, A, B, C, D, E> ListenableFuture<Z> transform(
          ListenableFuture<A> a,
          ListenableFuture<B> b,
          ListenableFuture<C> c,
          ListenableFuture<D> d,
          ListenableFuture<E> e,
          final AsyncFunction5<Z, A, B, C, D, E> function) {
    return transform(Arrays.asList(a, b, c, d, e), new AsyncFunction<List<Object>, Z>() {
      @Override
      public ListenableFuture<Z> apply(List<Object> results) {
        return function.apply((A) results.get(0), (B) results.get(1), (C) results.get(2), (D) results.get(3), (E) results.get(4));
      }
    });
  }

  public static interface AsyncFunction5<Z, A, B, C, D, E> {
    ListenableFuture<Z> apply(A a, B b, C c, D d, E e);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs, final Function<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }

  private static <Z> ListenableFuture<Z> transform(final List<ListenableFuture<?>> inputs, final AsyncFunction<List<Object>, Z> function) {
    return Futures.transform(Futures.allAsList(inputs), function);
  }
}
