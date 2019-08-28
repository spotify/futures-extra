/*
 * Copyright (c) 2013-2018 Spotify AB
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

package com.spotify.futures;

import com.google.api.core.ApiFuture;

public class TestMainUtil {
  public static void usesMissing(ApiFuture<String> param) {
  }

  public static ApiFuture<String> returnsMissing() {
    return null;
  }

  public static String bar() {
    return null;
  }

  public static void main(String[] args) {
    System.out.println("Hello world!");
    System.out.println(bar());
  }

}
