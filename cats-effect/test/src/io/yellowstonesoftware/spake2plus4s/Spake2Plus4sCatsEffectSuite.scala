package io.yellowstonesoftware.spake2plus4s

import cats.effect.IO
import cats.effect.std.Random

class Spake2Plus4sCatsEffectSuite extends munit.CatsEffectSuite {

  test("random") {
    implicit val random: Random[IO] = Random.scalaUtilRandom[IO].unsafeRunSync()

    (1 to 1000).foreach( _ =>
      assert(
        SPAKE2Plus4sCatsEffect.random[IO](P256SpakeParameters.p).unsafeRunSync() < P256SpakeParameters.p
      )
    )
  }
}
