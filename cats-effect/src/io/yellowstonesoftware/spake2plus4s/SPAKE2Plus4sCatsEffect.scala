package io.yellowstonesoftware.spake2plus4s

import cats.effect.Sync
import cats.effect.std.Random
import cats.syntax.all.*

import java.math.BigInteger
import java.security.SecureRandom

object SPAKE2Plus4sCatsEffect {
  def random[F[_]: Sync: Random](max: BigInt): F[BigInt] = {
    // TODO replace with Random[F].nextBytes
    Sync[F].delay(new BigInteger(max.bitLength - 1, new SecureRandom()))
  }

  def proverInit[F[_]: Sync: Random](
    w0: BigInt,
    spakeParameters: SpakeParameters
  ): F[(BigInt, Array[Byte])] = {
    random(spakeParameters.p).map(r => SPAKE2Plus4s.proverInit(w0, r, spakeParameters))
  }

  def verifierFinish[F[_]: Sync: Random](
    w0: BigInt,
    X: Array[Byte],
    L: Array[Byte],
    spakeParameters: SpakeParameters,
  ): F[YandZandV] = {
    random(spakeParameters.p).map(r => SPAKE2Plus4s.verifierFinish(w0, r, X, L, spakeParameters))
  }
}
