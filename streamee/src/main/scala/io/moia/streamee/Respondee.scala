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

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{ ActorMaterializer, Materializer }
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps

/**
  * Actor completing the given `Promise` either successfully when receiving a [[Respondee.Response]]
  * or with a [[Respondee.TimeoutException]]. Similar to an expiring `Promise`, but location
  * transparent.
  */
object Respondee {

  sealed trait Command
  final case class Response[A] private (response: A) extends Command
  private final case object Timeout                  extends Command

  final case class TimeoutException(timeout: FiniteDuration)
      extends Exception(s"No response within $timeout!")

  /**
    * Factory for `Respondee` behaviors.
    *
    * @param response `Promise` to be completed
    * @param timeout maximum duration for successful completion
    * @tparam A response type
    * @return `Respondee` behavior
    */
  def apply[A](response: Promise[A], timeout: FiniteDuration): Behavior[Response[A]] =
    Behaviors
      .withTimers[Command] { timers =>
        timers.startSingleTimer("timeout", Timeout, timeout)

        Behaviors.receiveMessage {
          case Timeout =>
            response.failure(TimeoutException(timeout))
            Behaviors.stopped

          case Response(r: A @unchecked) =>
            response.success(r)
            Behaviors.stopped
        }
      }
      .narrow

  /**
    * Create a [[Respondee]] along with its `Promise`.
    *
    * @param timeout maximum duration for successful completion
    * @tparam A response type
    * @return [[Respondee]] and its `Promise`
    */
  def spawn[A](timeout: FiniteDuration)(implicit mat: Materializer): (Respondee[A], Promise[A]) = {
    val response = Promise[A]()
    val respondee =
      mat
        .asInstanceOf[ActorMaterializer]
        .system
        .spawnAnonymous(Respondee[A](response, timeout))
    (respondee, response)
  }
}
