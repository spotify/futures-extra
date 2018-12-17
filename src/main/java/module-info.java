/*
 * Copyright (c) 2018 Spotify AB
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
module com.spotify.futures {
  exports com.spotify.futures;
  /*
   * com.google.guava:guava,
   * dependency is optional in case only CompletionStage
   * and/or ApiFuture are required:
   */
  requires static com.google.common;
  /*
   * com.google.api:api-common,
   * dependency is optional in case only CompletionStage
   * and/or ListenableFuture are required:
   */
  requires static api.common;
}
