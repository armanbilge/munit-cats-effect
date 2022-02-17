/*
 * Copyright 2021 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package munit

import cats.effect.{IO, Resource}
import CatsEffectSuite.Deferred

/** Similar to `ResourceSuiteLocalFixture`, but supported on both JVM and JS via several caveats.
  * Instead of directly providing `T` provides a (memoized) `IO[T]` that is backed by a
  * `Deferred[T]`. It is unsafe because on JS the resource is closed concurrently without
  * backpressure,
  * i.e. the suite will complete even while the resource has not closed yet. On JVM it is
  * semantically equivalent to `ResourceSuiteLocalFixture`. Note also that constructing this fixture
  * is impure because it unsafely allocates a `Deferred`.
  */
private[munit] class UnsafeResourceSuiteLocalDeferredFixture(val suite: CatsEffectSuite) {

  def apply[T](name: String, resource: Resource[IO, T]): suite.Fixture[IO[T]] =
    new suite.Fixture[IO[T]](name) {
      val value: Deferred[IO, (T, IO[Unit])] = Deferred.unsafe

      def apply(): IO[T] = value.get.map(_._1)

      override def beforeAll(): Unit = {
        val resourceEffect = resource.allocated.flatMap(value.complete)
        suite.unsafeRunSyncOrForget(resourceEffect)
      }

      override def afterAll(): Unit = {
        suite.unsafeRunSyncOrForget(value.get.flatMap(_._2))
      }
    }
}
