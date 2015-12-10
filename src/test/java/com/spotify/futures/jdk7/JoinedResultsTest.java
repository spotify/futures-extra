/*
 * Copyright (c) 2014 Spotify AB
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import com.spotify.futures.FuturesExtra;
import com.spotify.futures.JoinedResults;

public class JoinedResultsTest {
  @Test
  public void testMixedTypes() throws Exception {
    ListenableFuture<String> firstString = Futures.immediateFuture("ok");
    ListenableFuture<String> secondString = Futures.immediateFuture("alsoString");
    ListenableFuture<Integer> integer = Futures.immediateFuture(1);
    List<String> valueList = Collections.singletonList("value");
    ListenableFuture<List<String>> list = Futures.immediateFuture(valueList);
    JoinedResults joined = FuturesExtra.join(firstString, secondString, integer, list).get();
    assertEquals("ok", joined.get(firstString));
    assertEquals("alsoString", joined.get(secondString));
    assertEquals(Integer.valueOf(1), joined.get(integer));
    assertEquals("value", joined.get(list).get(0));
  }

  @Test
  public void testWithList() throws Exception {
    ListenableFuture<String> futureA = Futures.immediateFuture("a");
    ListenableFuture<String> futureB = Futures.immediateFuture("b");
    List<ListenableFuture<String>> list = Arrays.asList(futureA, futureB);
    JoinedResults joined = FuturesExtra.join(list).get();
    assertEquals("a", joined.get(futureA));
    assertEquals("b", joined.get(futureB));
  }

  @Test
  public void testWithListOfSpecific() throws Exception {
    SettableFuture<String> futureA = SettableFuture.create();
    SettableFuture<String> futureB = SettableFuture.create();
    List<SettableFuture<String>> list = Arrays.asList(futureA, futureB);

    final ListenableFuture<JoinedResults> joinFuture = FuturesExtra.join(list);
    assertFalse(joinFuture.isDone());

    futureA.set("a");
    futureB.set("b");

    JoinedResults joined = joinFuture.get();

    assertEquals("a", joined.get(futureA));
    assertEquals("b", joined.get(futureB));
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
