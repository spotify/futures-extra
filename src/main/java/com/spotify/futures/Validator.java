/*
 * Copyright (c) 2014 Spotify AB
 */

package com.spotify.futures;

/**
 * Validate a value, throw an exception on invalid values.
 * @param <T>
 */
public interface Validator<T> {
  void validate(T value) throws Exception;
}
