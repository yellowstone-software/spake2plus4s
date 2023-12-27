package io.yellowstonesoftware.spake2plus4s

import org.bouncycastle.math.ec.{ECCurve, ECPoint}
import scodec.bits.{ByteOrdering, ByteVector, HexStringSyntax}

import scala.util.Try

case class RegistrationValues(w0: BigInt, w1: BigInt, L: Array[Byte])
case class VandZ(V: Array[Byte], Z: Array[Byte])
case class YandZandV(Y: Array[Byte], Z: Array[Byte], V: Array[Byte])
case class KeySchedule(kConfirmP: Array[Byte], kConfirmV: Array[Byte], kShared: Array[Byte])

trait SpakeParameters {
  val wSizeBytes: Int

  val curve: ECCurve
  val p: BigInt // curve order
  val P: ECPoint // curve generator

  // point generation seeds
  val M: ECPoint
  val N: ECPoint
  lazy val Mencoded: ByteVector = ByteVector(M.getEncoded(false))
  lazy val Nencoded: ByteVector = ByteVector(N.getEncoded(false))
}

case class PBKDFInput(password: Array[Byte], salt: Array[Byte], iterations: Int)

trait SPAKE2Plus4s {
  def registration(
    input: PBKDFInput,
    crypto: CryptoPrimitives,
    spakeParameters: SpakeParameters
  ): Try[RegistrationValues]

  def calculateY(
    w0: BigInt,
    y: BigInt,
    spakeParameters: SpakeParameters,
  ): Array[Byte]

  def proverInit(w0: BigInt, x: BigInt, spakeParameters: SpakeParameters): (BigInt, Array[Byte])

  def proverFinish(w0: BigInt, w1: BigInt, x: BigInt, Y: Array[Byte], spakeParameters: SpakeParameters): VandZ

  def verifierFinish(w0: BigInt, y: BigInt, X: Array[Byte], L: Array[Byte], spakeParameters: SpakeParameters): YandZandV

  def calculateTT(
    context: ByteVector,
    idProver: String,
    idVerifier: String,
    X: ByteVector,
    Y: ByteVector,
    V: ByteVector,
    Z: ByteVector,
    w0: BigInt,
    spakeParameters: SpakeParameters,
  ): Array[Byte]

  def calculateKeySchedule(
    TT: Array[Byte],
    crypto: CryptoPrimitives
  ): Try[KeySchedule]
}

object SPAKE2Plus4s extends SPAKE2Plus4s {

  def bigInt2ByteVector(i: BigInt): ByteVector = {
    val ba = i.toByteArray
    //    if (ba.length % 8 != 0)
    if (ba(0) == 0)
      ByteVector(ba).drop(1) // drop the signed byte
    else
      ByteVector(ba)
  }

  override def registration(
    input: PBKDFInput,
    crypto: CryptoPrimitives,
    spakeParameters: SpakeParameters
  ): Try[RegistrationValues] = {
    import spakeParameters.*
    import input.*
    crypto.passwordKeyDerivationFn(password, salt, iterations, wSizeBytes)
      .flatMap { bytes =>
        Try {
          val w0s = new Array[Byte](wSizeBytes)
          val w1s = new Array[Byte](wSizeBytes)
          Array.copy(bytes, 0, w0s, 0, wSizeBytes)
          Array.copy(bytes, wSizeBytes, w1s, 0, wSizeBytes)

          val w0 = BigInt(w0s) % p
          val w1 = BigInt(w1s) % p
          val L = ByteVector(P.multiply(w1.bigInteger).getEncoded(false))

          RegistrationValues(w0, w1, L.toArray)
        }
      }
  }

  override def calculateY(w0: BigInt, y: BigInt, spakeParameters: SpakeParameters): Array[Byte] = {
    val firstTerm = spakeParameters.P.multiply(y.bigInteger)
    val secondTerm = spakeParameters.N.multiply(w0.bigInteger)
    firstTerm.add(secondTerm).getEncoded(false)
  }

  override def proverInit(w0: BigInt, x: BigInt, spakeParameters: SpakeParameters): (BigInt, Array[Byte]) = {
    val firstTerm = spakeParameters.P.multiply(x.bigInteger)
    val secondTerm = spakeParameters.M.multiply(w0.bigInteger)
    (x, firstTerm.add(secondTerm).getEncoded(false))
  }

  override def proverFinish(w0: BigInt, w1: BigInt, x: BigInt, Y: Array[Byte], spakeParameters: SpakeParameters): VandZ = {
    val YPoint = spakeParameters.curve.decodePoint(Y)
    val w0N = spakeParameters.N.multiply(w0.bigInteger)
    val Yw0N = YPoint.subtract(w0N)
    val Z = Yw0N.multiply(x.bigInteger).getEncoded(false)
    val V = Yw0N.multiply(w1.bigInteger).getEncoded(false)
    // TODO h coefficient
    VandZ(V, Z)
  }

  override def verifierFinish(w0: BigInt, y: BigInt, X: Array[Byte], L: Array[Byte], spakeParameters: SpakeParameters): YandZandV = {
    val Y = calculateY(w0, y, spakeParameters)
    val XPoint = spakeParameters.curve.decodePoint(X)
    val LPoint = spakeParameters.curve.decodePoint(L)
    val w0M = spakeParameters.M.multiply(w0.bigInteger)
    val Z = XPoint.subtract(w0M).multiply(y.bigInteger).getEncoded(false)
    val V = LPoint.multiply(y.bigInteger).getEncoded(false)
    // TODO h coefficient
    YandZandV(Y, Z, V)
  }

  // https://www.rfc-editor.org/rfc/rfc9383.html#name-transcript-computation
  override def calculateTT(
    context: ByteVector,
    idProver: String,
    idVerifier: String,
    X: ByteVector,
    Y: ByteVector,
    V: ByteVector,
    Z: ByteVector,
    w0: BigInt,
    spakeParameters: SpakeParameters,
  ): Array[Byte] = {
    def addEntry(e: ByteVector): ByteVector = {
      (e.length match {
        case 0 => hex"0x0000000000000000"
        case n => ByteVector.fromLong(n, ordering = ByteOrdering.LittleEndian)
      }) ++ e
    }

    val l = List(
      context,
      ByteVector(idProver.getBytes("UTF-8")),
      ByteVector(idVerifier.getBytes("UTF-8")),
      spakeParameters.Mencoded,
      spakeParameters.Nencoded,
      X,
      Y,
      Z,
      V,
      bigInt2ByteVector(w0)
    )
    l.foldLeft(ByteVector.empty) { case (acc, t) => acc ++ addEntry(t) }.toArray
  }

  // https://www.rfc-editor.org/rfc/rfc9383.html#name-key-schedule-computation
  override def calculateKeySchedule(TT: Array[Byte], crypto: CryptoPrimitives): Try[KeySchedule] = {
    for {
      kMain <- crypto.hash(TT)
      kConfirm <- crypto.keyDerivationFn(kMain, Array.empty, "ConfirmationKeys".getBytes("UTF-8"), crypto.hashOutputLengthBytes * 2)
      kConfirmP = kConfirm.slice(0, crypto.hashOutputLengthBytes)
      kConfirmV = kConfirm.slice(crypto.hashOutputLengthBytes, crypto.hashOutputLengthBytes * 2)
      kShared <- crypto.keyDerivationFn(kMain, Array.empty, "SharedKey".getBytes("UTF-8"), crypto.hashOutputLengthBytes)
    } yield KeySchedule(kConfirmP, kConfirmV, kShared)
  }

  def confirmP(kConfirmP: Array[Byte], Y: Array[Byte], crypto: CryptoPrimitives): Try[Array[Byte]] = {
    crypto.hmacSecretKey(kConfirmP).flatMap(crypto.hmac(_, Y))
  }

  def confirmV(kConfirmV: Array[Byte], X: Array[Byte], crypto: CryptoPrimitives): Try[Array[Byte]] = {
    crypto.hmacSecretKey(kConfirmV).flatMap(crypto.hmac(_, X))
  }
}
