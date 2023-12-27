package io.yellowstonesoftware.spake2plus4s

import io.yellowstonesoftware.spake2plus4s.SPAKE2Plus4s.bigInt2ByteVector
import scodec.bits.{ByteOrdering, ByteVector, HexStringSyntax}

import scala.util.{Failure, Success}

case class Passcode(id: Int) extends AnyVal {
  def littleEndian: Array[Byte] =
    ByteVector.fromInt(id, ordering = ByteOrdering.LittleEndian).toArray
}

class Spake2Plus4sSuite extends munit.FunSuite {
  java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  test("calculate w0 and w1 and L") {
    // test vectors from: https://github.com/project-chip/connectedhomeip/issues/11125
    val salt = "SPAKE2P Key Salt".getBytes("UTF-8")
    SPAKE2Plus4s.registration(PBKDFInput(Passcode(34567890).littleEndian, salt, 100), SHA256CryptoPrimitives, P256SpakeParameters) match {
      case Failure(e) =>
        fail("failed to calculatew0w1", e)
      case Success(RegistrationValues(w0, w1, l)) =>
        println(w0)
        assertEquals(bigInt2ByteVector(w0), hex"0x0aff2fab0980e98d9d6d33a17ac2f15886cd87f6cdcb34200a072f5f6129f8ad")
        assertEquals(bigInt2ByteVector(w1), hex"0xef0d4d1d61fdf08cd28ae176d02b1344ebbd38f4df9500a222f51c1924fd56a1")
        assertEquals(ByteVector(l), hex"04eae21d4b206f567bf357e91df2da29d1a2b75a9e07519cab893b97e29a4bf43d999022374f3b11ecc4a1d725c43f8194d9871381ef8acf4472af453d1389ff71")
    }
  }

  test("proverInit") {
    val w0 = BigInt(RFCTestVectors.w0.toHex, 16)
    val x = BigInt(RFCTestVectors.x.toHex, 16)
    val (xp, bigX) = SPAKE2Plus4s.proverInit(w0, x, P256SpakeParameters)
    assertEquals(x, xp)
    assertEquals(ByteVector(bigX), RFCTestVectors.X)
  }

  test("calculateY") {
    val w0 = BigInt(RFCTestVectors.w0.toHex, 16)
    val y = BigInt(RFCTestVectors.y.toHex, 16)
    val fY = SPAKE2Plus4s.calculateY(w0, y, P256SpakeParameters)
    assertEquals(ByteVector(fY), RFCTestVectors.Y)
  }

  test("proverFinish") {
    val w0 = BigInt(RFCTestVectors.w0.toHex, 16)
    val w1 = BigInt(RFCTestVectors.w1.toHex, 16)
    val x = BigInt(RFCTestVectors.x.toHex, 16)
    val Y = RFCTestVectors.Y.toArray
    SPAKE2Plus4s.proverFinish(w0, w1, x, Y, P256SpakeParameters) match {
      case VandZ(v, z) =>
        assertEquals(ByteVector(z), RFCTestVectors.Z)
        assertEquals(ByteVector(v), RFCTestVectors.V)
    }
  }

  test("verifierFinish") {
    val w0 = BigInt(RFCTestVectors.w0.toHex, 16)
    val y = BigInt(RFCTestVectors.y.toHex, 16)
    val X = RFCTestVectors.X
    val L = RFCTestVectors.L
    SPAKE2Plus4s.verifierFinish(w0, y, X.toArray, L.toArray, P256SpakeParameters) match {
      case YandZandV(y, z, v) =>
        assertEquals(ByteVector(y), RFCTestVectors.Y)
        assertEquals(ByteVector(z), RFCTestVectors.Z)
        assertEquals(ByteVector(v), RFCTestVectors.V)
    }
  }

  test("calculateTT") {
    val w0 = BigInt(RFCTestVectors.w0.toHex, 16)
    val r = SPAKE2Plus4s.calculateTT(
      context = RFCTestVectors.context,
      idProver = RFCTestVectors.idProver,
      idVerifier = RFCTestVectors.idVerifier,
      X = RFCTestVectors.X,
      Y = RFCTestVectors.Y,
      V = RFCTestVectors.V,
      Z = RFCTestVectors.Z,
      w0,
      P256SpakeParameters,
    )
    assertEquals(ByteVector(r), RFCTestVectors.TT)
  }

  test("calculateKeySchedule") {
    val r = SPAKE2Plus4s.calculateKeySchedule(
      TT = RFCTestVectors.TT.toArray,
      SHA256CryptoPrimitives,
    )
    r match {
      case Success(KeySchedule(kConfirmP, kConfirmV, kShared)) =>
        assertEquals(ByteVector(kShared), RFCTestVectors.kShared)
        assertEquals(ByteVector(kConfirmP), RFCTestVectors.kConfirmP)
        assertEquals(ByteVector(kConfirmV), RFCTestVectors.kConfirmV)
      case Failure(e) => fail("calculateKeySchedule", e)
    }
  }

  test("confirmP") {
    SPAKE2Plus4s.confirmP(
      kConfirmP = RFCTestVectors.kConfirmP.toArray,
      Y = RFCTestVectors.Y.toArray,
      SHA256CryptoPrimitives,
    ).map { r =>
      assertEquals(ByteVector(r), RFCTestVectors.confirmP)
    }
  }

  test("confirmV") {
    SPAKE2Plus4s.confirmV(
      kConfirmV = RFCTestVectors.kConfirmV.toArray,
      X = RFCTestVectors.X.toArray,
      SHA256CryptoPrimitives,
    ).map { r =>
      assertEquals(ByteVector(r), RFCTestVectors.confirmV)
    }
  }
}

// Test vectors: https://www.rfc-editor.org/rfc/rfc9383.html#appendix-C
object RFCTestVectors {
  val context: ByteVector = ByteVector("SPAKE2+-P256-SHA256-HKDF-SHA256-HMAC-SHA256 Test Vectors".getBytes("UTF-8"))
  val idProver: String = "client"
  val idVerifier: String = "server"
  val w0: ByteVector = hex"0xbb8e1bbcf3c48f62c08db243652ae55d3e5586053fca77102994f23ad95491b3"
  val w1: ByteVector = hex"0x7e945f34d78785b8a3ef44d0df5a1a97d6b3b460409a345ca7830387a74b1dba"
  val L: ByteVector = hex"0x04eb7c9db3d9a9eb1f8adab81b5794c1f13ae3e225efbe91ea487425854c7fc00f00bfedcbd09b2400142d40a14f2064ef31dfaa903b91d1faea7093d835966efd"
  val x: ByteVector = hex"0xd1232c8e8693d02368976c174e2088851b8365d0d79a9eee709c6a05a2fad539"
  val X: ByteVector = hex"0x04ef3bd051bf78a2234ec0df197f7828060fe9856503579bb1733009042c15c0c1de127727f418b5966afadfdd95a6e4591d171056b333dab97a79c7193e341727"
  val shareP: ByteVector = X
  val y: ByteVector = hex"0x717a72348a182085109c8d3917d6c43d59b224dc6a7fc4f0483232fa6516d8b3"
  val Y: ByteVector = hex"0x04c0f65da0d11927bdf5d560c69e1d7d939a05b0e88291887d679fcadea75810fb5cc1ca7494db39e82ff2f50665255d76173e09986ab46742c798a9a68437b048"
  val shareV: ByteVector = Y
  val Z: ByteVector = hex"0x04bbfce7dd7f277819c8da21544afb7964705569bdf12fb92aa388059408d50091a0c5f1d3127f56813b5337f9e4e67e2ca633117a4fbd559946ab474356c41839"
  val V: ByteVector = hex"0x0458bf27c6bca011c9ce1930e8984a797a3419797b936629a5a937cf2f11c8b9514b82b993da8a46e664f23db7c01edc87faa530db01c2ee405230b18997f16b68"
  val TT: ByteVector = hex"0x38000000000000005350414b45322b2d503235362d5348413235362d484b44462d5348413235362d484d41432d534841323536205465737420566563746f72730600000000000000636c69656e740600000000000000736572766572410000000000000004886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f5ff355163e43ce224e0b0e65ff02ac8e5c7be09419c785e0ca547d55a12e2d20410000000000000004d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b4907d60aa6bfade45008a636337f5168c64d9bd36034808cd564490b1e656edbe7410000000000000004ef3bd051bf78a2234ec0df197f7828060fe9856503579bb1733009042c15c0c1de127727f418b5966afadfdd95a6e4591d171056b333dab97a79c7193e341727410000000000000004c0f65da0d11927bdf5d560c69e1d7d939a05b0e88291887d679fcadea75810fb5cc1ca7494db39e82ff2f50665255d76173e09986ab46742c798a9a68437b048410000000000000004bbfce7dd7f277819c8da21544afb7964705569bdf12fb92aa388059408d50091a0c5f1d3127f56813b5337f9e4e67e2ca633117a4fbd559946ab474356c4183941000000000000000458bf27c6bca011c9ce1930e8984a797a3419797b936629a5a937cf2f11c8b9514b82b993da8a46e664f23db7c01edc87faa530db01c2ee405230b18997f16b682000000000000000bb8e1bbcf3c48f62c08db243652ae55d3e5586053fca77102994f23ad95491b3"
  val kMain: ByteVector = hex"0x4c59e1ccf2cfb961aa31bd9434478a1089b56cd11542f53d3576fb6c2a438a29"
  val kConfirmP: ByteVector = hex"0x871ae3f7b78445e34438fb284504240239031c39d80ac23eb5ab9be5ad6db58a"
  val kConfirmV: ByteVector = hex"0xccd53c7c1fa37b64a462b40db8be101cedcf838950162902054e644b400f1680"
  val confirmP: ByteVector = hex"0x926cc713504b9b4d76c9162ded04b5493e89109f6d89462cd33adc46fda27527"
  val confirmV: ByteVector = hex"0x9747bcc4f8fe9f63defee53ac9b07876d907d55047e6ff2def2e7529089d3e68"
  val kShared: ByteVector = hex"0x0c5f8ccd1413423a54f6c1fb26ff01534a87f893779c6e68666d772bfd91f3e7"
}