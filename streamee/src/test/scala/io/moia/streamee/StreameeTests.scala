/*
 * Copyright 2018 MOIA GmbH
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

package io.moia.streamee

import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.stream.scaladsl.{ Sink, Source }
import org.scalatest.{ AsyncWordSpec, Matchers }
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.concurrent.duration.DurationInt
import scala.concurrent.Promise

final class StreameeTests
    extends AsyncWordSpec
    with AkkaSuite
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  "Calling into on a Source" should {
    "throw an IllegalArgumentException for timeout <= 0" in {
      forAll(TestData.nonPosDuration) { timeout =>
        an[IllegalArgumentException] shouldBe thrownBy {
          Source.single("abc").into(Sink.ignore, timeout)
        }
      }
    }

    "result in a TimeoutException if the ProcessSink does not respond in time" in {
      val timeout = 100.milliseconds
      Source
        .single("abc")
        .into(Sink.ignore, timeout)
        .runWith(Sink.head)
        .failed
        .map { case Respondee.TimeoutException(t) => t shouldBe timeout }
    }
  }

  "Calling push and pop" should {
    "first push each elements to the propagated context and then pop it" in {
      val process =
        Process[String, (String, Int)]()
          .map(_.toUpperCase)
          .push
          .map(_.length)
          .pop

      val response  = Promise[(String, Int)]()
      val respondee = system.spawnAnonymous(Respondee[(String, Int)](response, 1.second))

      Source
        .single(("abc", respondee))
        .via(process)
        .runWith(Sink.head)
        .map {
          case ((s, n), _) =>
            s shouldBe "ABC"
            n shouldBe 3
        }
    }

    "first push and transform each elements to the propagated context and then pop and transform it" in {
      val process =
        Process[String, (String, Int)]()
          .push(_.toUpperCase, _ * 2)
          .map(_.length)
          .pop

      val response  = Promise[(String, Int)]()
      val respondee = system.spawnAnonymous(Respondee[(String, Int)](response, 1.second))

      Source
        .single(("abc", respondee))
        .via(process)
        .runWith(Sink.head)
        .map {
          case ((s, n), _) =>
            s shouldBe "ABC"
            n shouldBe 6
        }
    }
  }
}