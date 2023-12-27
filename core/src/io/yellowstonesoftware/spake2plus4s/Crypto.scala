package io.yellowstonesoftware.spake2plus4s

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.{HKDFBytesGenerator, PKCS5S2ParametersGenerator}
import org.bouncycastle.crypto.params.{HKDFParameters, KeyParameter}

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Mac, SecretKey}
import scala.util.Try

trait CryptoPrimitives {
  def hash(input: Array[Byte]): Try[Array[Byte]]
  val hashOutputLengthBytes: Int
  def hmacSecretKey(input: Array[Byte]): Try[SecretKey]
  def hmac(key: SecretKey, input: Array[Byte]): Try[Array[Byte]]

  def keyDerivationFn(
    inputKeyMaterial: Array[Byte],
    salt: Array[Byte],
    info: Array[Byte],
    lengthBytes: Int,
  ): Try[Array[Byte]]

  def passwordKeyDerivationFn(
    password: Array[Byte],
    salt: Array[Byte],
    iterations: Int,
    lengthBytes: Int,
  ): Try[Array[Byte]]
}

object SHA256CryptoPrimitives extends CryptoPrimitives {
  override def hash(input: Array[Byte]): Try[Array[Byte]] = Try {
    val md = MessageDigest.getInstance("SHA256", BouncyCastleProvider)
    md.digest(input)
  }

  override val hashOutputLengthBytes: Int = 256 / 8

  override def hmacSecretKey(input: Array[Byte]): Try[SecretKey] = Try {
    new SecretKeySpec(input, "HmacSHA256")
  }

  override def hmac(key: SecretKey, input: Array[Byte]): Try[Array[Byte]] = Try {
    val md = Mac.getInstance("HmacSHA256", BouncyCastleProvider)
    md.init(key)
    md.update(input)
    md.doFinal()
  }

  override def keyDerivationFn(
    inputKeyMaterial: Array[Byte],
    salt: Array[Byte],
    info: Array[Byte],
    lengthBytes: Int
  ): Try[Array[Byte]] = Try {
    val okm = new Array[Byte](lengthBytes)
    val params = new HKDFParameters(inputKeyMaterial, salt, info)
    val hkdf = new HKDFBytesGenerator(SHA256Digest.newInstance())
    hkdf.init(params)
    hkdf.generateBytes(okm, 0, lengthBytes)
    okm
  }

  override def passwordKeyDerivationFn(
    password: Array[Byte],
    salt: Array[Byte],
    iterations: Int,
    lengthBytes: Int,
  ): Try[Array[Byte]] = Try {
    val generator = new PKCS5S2ParametersGenerator(new SHA256Digest())
    generator.init(
      password,
      salt,
      iterations,
    )
    generator.generateDerivedParameters(lengthBytes * 8 * 2).asInstanceOf[KeyParameter].getKey
  }
}
