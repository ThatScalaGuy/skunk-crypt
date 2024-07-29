package de.thatscalaguy.skunkcrypt

import cats.syntax.all._
import skunk.Codec
import skunk.data.Type
import java.util.Base64
import skunk.codec.numeric.safe
import javax.crypto.SecretKey
import javax.crypto.Cipher
import scala.util.Try
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.spec.GCMParameterSpec

final case class CryptContext(secretKey: SecretKey)

object CryptContext {
  def keyFromHex(hexString: String): Option[CryptContext] =
    Try {
      new SecretKeySpec(
        hexString.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte),
        "AES"
      )
    }.toOption.map(CryptContext.apply)
}

trait CryptCodecs {

  private[skunkcrypt] def encrypt(implicit c: CryptContext): String => String
  private[skunkcrypt] def decrypt(implicit c: CryptContext): String => String

  def text(implicit c: CryptContext): Codec[String] =
    Codec.simple(
      v => encrypt(c)(v.toString),
      value => decrypt(c)(value).asRight,
      Type.text
    )

  def int2(implicit c: CryptContext): Codec[Short] =
    Codec.simple(
      value => encrypt(c)(value.toString),
      safe(value => decrypt(c)(value).toShort),
      Type.text
    )

  def int4(implicit c: CryptContext): Codec[Int] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => decrypt(c)(value).toInt),
    Type.text
  )

  def int8(implicit c: CryptContext): Codec[Long] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => decrypt(c)(value).toLong),
    Type.text
  )

  def float4(implicit c: CryptContext): Codec[Float] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => decrypt(c)(value).toFloat),
    Type.text
  )

  def float8(implicit c: CryptContext): Codec[Double] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => decrypt(c)(value).toDouble),
    Type.text
  )
}

object crypt extends CryptCodecs {
  private val random = new SecureRandom()
  private val GCM_TAG_LENGTH = 16
  private val GCM_IV_LENGTH = 12

  def encrypt(implicit c: CryptContext) = value => {
    val iv = new Array[Byte](GCM_IV_LENGTH)
    random.nextBytes(iv)
    val ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, c.secretKey, ivSpec)
    val encryptedBytes = cipher.doFinal(value.getBytes())
    Base64.getEncoder.encodeToString(iv) + "." + Base64.getEncoder
      .encodeToString(encryptedBytes)
  }

  def decrypt(implicit c: CryptContext) = value =>
    value.split("\\.").toList match {
      case iv :: encrypted :: Nil =>
        val ivBytes = Base64.getDecoder.decode(iv)
        val encryptedBytes = Base64.getDecoder.decode(encrypted)
        val ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, ivBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, c.secretKey, ivSpec)
        new String(cipher.doFinal(encryptedBytes))
      case _ => throw new Exception("Invalid input")
    }
}

object cryptd extends CryptCodecs {
  private val GCM_TAG_LENGTH = 16
  private val GCM_IV_LENGTH = 12

  def encrypt(implicit c: CryptContext) = value => {
    val iv = new Array[Byte](GCM_IV_LENGTH)

    val ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, c.secretKey, ivSpec)
    val encryptedBytes = cipher.doFinal(value.getBytes())
    Base64.getEncoder.encodeToString(iv) + "." + Base64.getEncoder
      .encodeToString(encryptedBytes)
  }

  def decrypt(implicit c: CryptContext) = value =>
    value.split("\\.").toList match {
      case iv :: encrypted :: Nil =>
        val ivBytes = Base64.getDecoder.decode(iv)
        val encryptedBytes = Base64.getDecoder.decode(encrypted)
        val ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, ivBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, c.secretKey, ivSpec)
        new String(cipher.doFinal(encryptedBytes))
      case _ => throw new Exception("Invalid input")
    }
}
