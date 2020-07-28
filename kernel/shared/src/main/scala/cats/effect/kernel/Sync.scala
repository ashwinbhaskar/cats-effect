/*
 * Copyright 2020 Typelevel
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

package cats.effect.kernel

import cats.{Defer, MonadError}
import cats.data.OptionT

import scala.concurrent.duration.FiniteDuration

trait Sync[F[_]] extends MonadError[F, Throwable] with Clock[F] with Defer[F] {

  private[this] val Delay = Sync.Type.Delay
  private[this] val Blocking = Sync.Type.Blocking
  private[this] val InterruptibleOnce = Sync.Type.InterruptibleOnce
  private[this] val InterruptibleMany = Sync.Type.InterruptibleMany

  def delay[A](thunk: => A): F[A] =
    suspend(Delay)(thunk)

  def defer[A](thunk: => F[A]): F[A] =
    flatMap(delay(thunk))(x => x)

  def blocking[A](thunk: => A): F[A] =
    suspend(Blocking)(thunk)

  def interruptible[A](many: Boolean)(thunk: => A): F[A] =
    suspend(if (many) InterruptibleOnce else InterruptibleMany)(thunk)

  def suspend[A](hint: Sync.Type)(thunk: => A): F[A]
}

object Sync {

  def apply[F[_]](implicit F: Sync[F]): F.type = F

  implicit def syncForOptionT[F[_]](implicit F: Sync[F]): Sync[OptionT[F, *]] =
    new Sync[OptionT[F, *]] {
      private[this] val delegate = OptionT.catsDataMonadErrorForOptionT[F, Throwable]

      def pure[A](x: A): OptionT[F, A] =
        delegate.pure(x)

      def handleErrorWith[A](fa: OptionT[F, A])(f: Throwable => OptionT[F, A]): OptionT[F, A] =
        delegate.handleErrorWith(fa)(f)

      def raiseError[A](e: Throwable): OptionT[F, A] =
        delegate.raiseError(e)

      def monotonic: OptionT[F, FiniteDuration] =
        OptionT.liftF(F.monotonic)

      def realTime: OptionT[F, FiniteDuration] =
        OptionT.liftF(F.realTime)

      def flatMap[A, B](fa: OptionT[F, A])(f: A => OptionT[F, B]): OptionT[F, B] =
        delegate.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => OptionT[F, Either[A, B]]): OptionT[F, B] =
        delegate.tailRecM(a)(f)

      def suspend[A](hint: Type)(thunk: => A): OptionT[F, A] =
        OptionT.liftF(F.suspend(hint)(thunk))
    }

  sealed trait Type extends Product with Serializable

  object Type {
    case object Delay extends Type
    case object Blocking extends Type
    case object InterruptibleOnce extends Type
    case object InterruptibleMany extends Type
  }
}