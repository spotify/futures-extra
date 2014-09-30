package com.spotify.futures;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Contains references to a set of joined future values
 */
public final class JoinedResults {
  private final Map<ListenableFuture<?>, Object> futures;

  private JoinedResults(Map<ListenableFuture<?>, Object> futures) {
    this.futures = futures;
  }

  /**
   * Gets a future value if it was part of the joined future values, else throws an illegal argument exception
   */
  public <T> T get(ListenableFuture<T> future) {
    Object value = futures.get(future);
    if (value == Null.INSTANCE) {
      return null;
    } else if (value == null) {
      throw new IllegalArgumentException("Attempt to access future value not part of joined operation");
    }
    // Must be of type T since futures is an identity map and future has resolved into this value earlier
    @SuppressWarnings("unchecked")
    T t = (T) value;
    return t;
  }

  static class Transform implements Function<List<Object>, JoinedResults> {
    private final List<ListenableFuture<?>> futures;
    public Transform(List<ListenableFuture<?>> list) {
      futures = ImmutableList.copyOf(list);
    }

    @Override
    public JoinedResults apply(List<Object> input) {
      Preconditions.checkArgument(input.size() == futures.size(), "Wrong number of futures resolved");
      Map<ListenableFuture<?>, Object> result = Maps.newIdentityHashMap();
      for (int i = 0 ; i < futures.size() ; i++) {
        Object value = input.get(i);
        if (value == null) {
          value = Null.INSTANCE;
        }
        result.put(futures.get(i), value);
      }
      return new JoinedResults(result);
    }
  }

  private static enum Null {
    INSTANCE
  }
}
