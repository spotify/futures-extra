### Futures-extra

Futures-extra is a set of small utility functions to simplify working with Guava's ListenableFuture class

### Build status

[![Travis](https://api.travis-ci.org/spotify/futures-extra.svg?branch=master)](https://travis-ci.org/spotify/futures-extra)
[![Coverage Status](http://img.shields.io/coveralls/spotify/futures-extra/master.svg)](https://coveralls.io/r/spotify/futures-extra?branch=master)

### Maven central

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.spotify/futures-extra/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.spotify/futures-extra)

### Build dependencies
* Java 6 or higher
* Maven

### Usage

Futures-extra is meant to be used as a library embedded in other software.

To import it with maven, use this:

    <dependency>
      <groupId>com.spotify</groupId>
      <artifactId>futures-extra</artifactId>
      <version>1.4.1</version>
    </dependency>

### Examples

#### Joining multiple futures

A common use case is waiting for two or more futures and then transforming the
result to something else. You can do this in a couple of different ways, here are two of them:

The examples are for Java 8, but they also work for Java 6 and 7 (though it becomes more verbose).

```java
final ListenableFuture<A> futureA = getFutureA();
final ListenableFuture<B> futureB = getFutureB();

return Futures.transform(Futures.allAsList(futureA, futureB), 
    list -> combine((A) list.get(0), (B) list.get(1));
```
This one has the problem that you have to manually make sure that the casts and ordering are correct, otherwise you will get ClassCastException.

You could also do this one instead to avoid casts:
```java
final ListenableFuture<A> futureA = getFutureA();
final ListenableFuture<B> futureB = getFutureB();

return Futures.transform(Futures.allAsList(futureA, futureB), 
    list -> combine(getUnchecked(futureA), getUnchecked(futureB));
```
Now you instead need to make sure that the futures in the transform input are the same as the ones you getUnchecked.
If you fail to do this, things may work anyway (which is a good way of hiding bugs), but block the thread, actually removing the asynchronous advantage. Even worse - the future may never finish, blocking the thread forever.

To simplify these use cases we have a couple of helper functions:
```java
final ListenableFuture<A> futureA = getFutureA();
final ListenableFuture<B> futureB = getFutureB();

return FuturesExtra.syncTransform2(futureA, futureB,
    (a, b) -> combine(a, b));
```

This is much clearer! We don't need any type information because the lambda can infer it, and we avoid the potential bugs that can occur as a result of the first to examples.

The tuple transform can be used up to 6 arguments. If you need more than that, some refactoring is likely in place, but you can also use the JoinedResult:

```java
final ListenableFuture<A> futureA = getFutureA();
final ListenableFuture<B> futureB = getFutureB();

final ListenableFuture<JoinedResult> futureJoined = FuturesExtra.join(futureA, futureB);
return Futures.transform(futureJoined,
    joined -> combine(joined.get(futureA), joined.get(futureB)));
```

This supports an arbitrary number of futures, but is slightly more complex. However, it is much safer than the first two examples,
because joined.get(...) will fail if you try to get the value of a future that was not part of the input.

#### Timeouts

Sometimes you want to stop waiting for a future after a specific timeout and to do this you generally need to have some sort of scheduling involved.
To simplify that, you can use this:
```java
final ListenableFuture<A> future = getFuture();
final ListenableFuture<A> futureWithTimeout = FuturesExtra.makeTimeoutFuture(scheduledExecutor, future, 100, TimeUnit.MILLISECONDS);
```

#### Select

If you have some futures and want to succeed as soon as the first one succeeds, you can use select:
```java
final List<ListenableFuture<A>> futures = getFutures();
final ListenableFuture<A> firstSuccessful = FuturesExtra.select(futures);
```

