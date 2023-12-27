package io.yellowstonesoftware.spake2plus4s

import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve
import org.bouncycastle.math.ec.{ECCurve, ECPoint}
import scodec.bits.HexStringSyntax

import java.math.BigInteger

object P256SpakeParameters extends SpakeParameters {

  override val wSizeBytes: Int = 40
  override val curve: ECCurve = new SecP256R1Curve()
  override val p: BigInt = curve.getOrder

  // curve generator https://www.rfc-editor.org/rfc/rfc6090#appendix-D
  override val P: ECPoint = curve.createPoint(
    new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
    new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)
  )

  // https://www.rfc-editor.org/rfc/rfc9383.html#section-4-5
  val MencodedCompressed = hex"02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f"
  val NencodedCompressed = hex"03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49"
  override lazy val M: ECPoint = curve.decodePoint(MencodedCompressed.toArray)
  override lazy val N: ECPoint = curve.decodePoint(NencodedCompressed.toArray)
}
