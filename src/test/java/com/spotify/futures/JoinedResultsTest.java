package com.spotify.futures;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class JoinedResultsTest {
  @Test
  public void testMixedTypes() throws Exception {
    ListenableFuture<String> firstString = Futures.immediateFuture("ok");
    ListenableFuture<String> secondString = Futures.immediateFuture("alsoString");
    ListenableFuture<Integer> integer = Futures.immediateFuture(1);
    ListenableFuture<List<String>> list = Futures.immediateFuture(Collections.singletonList("value"));
    JoinedResults joined = FuturesExtra.join(firstString, secondString, integer, list).get();
    assertEquals("ok", joined.get(firstString));
    assertEquals("alsoString", joined.get(secondString));
    assertEquals(Integer.valueOf(1), joined.get(integer));
    assertEquals("value", joined.get(list).get(0));
  }

  @Test
  public void testNullTypes() throws Exception {
    ListenableFuture<String> nullable = Futures.immediateFuture(null);
    JoinedResults joined = FuturesExtra.join(nullable).get();
    assertEquals(null, joined.get(nullable));
  }

  @Test(expected=IllegalArgumentException.class)
  public void testGetMissingFromJoin() throws Exception {
    JoinedResults joined = FuturesExtra.join().get();
    joined.get(Futures.immediateFuture(null));
  }
}
