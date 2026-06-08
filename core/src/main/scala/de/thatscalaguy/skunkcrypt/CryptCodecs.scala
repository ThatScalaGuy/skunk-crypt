/*
 * Copyright 2024 ThatScalaGuy
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

package de.thatscalaguy.skunkcrypt

import cats.syntax.all.*
import skunk.Codec
import skunk.codec.numeric.safe
import skunk.data.Type

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

trait CryptCodecs {
  val GCM_IV_LENGTH  = 12
  val GCM_TAG_LENGTH = 16

  private[skunkcrypt] def encrypt(implicit c: CryptContext): String => String
  private[skunkcrypt] def decrypt(implicit c: CryptContext): String => String

  private[skunkcrypt] def genEncrypt(
      iv: Array[Byte],
      secretKeys: Array[SecretKey],
      value: String
  ) = {
    val secretKey = secretKeys.last
    val ivSpec    = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    val cipher    = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
    val encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8))
    Base64.getEncoder.encodeToString(
      iv
    ) + "." + (secretKeys.length - 1) + "." + Base64.getEncoder
      .encodeToString(encryptedBytes)
  }

  private[skunkcrypt] def genDecrypt(
      iv: Array[Byte],
      secretKey: SecretKey,
      encrypted: String
  ) = {
    val encryptedBytes = Base64.getDecoder.decode(encrypted)
    val ivSpec         = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    val cipher         = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
  }

  private[skunkcrypt] def parseDecrypt(
      value: String
  )(implicit c: CryptContext): String =
    value.split("\\.").toList match {
      case iv :: keyIndex :: encrypted :: Nil =>
        try {
          val ivBytes   = Base64.getDecoder.decode(iv)
          val secretKey = c.secretKeys(keyIndex.toInt)
          genDecrypt(ivBytes, secretKey, encrypted)
        } catch {
          case e: Throwable => throw new DecryptionFailure(e)
        }
      case _ => throw new MalformedCiphertext
    }

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

  def bool(implicit c: CryptContext): Codec[Boolean] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => decrypt(c)(value).toBoolean),
    Type.text
  )

  def uuid(implicit c: CryptContext): Codec[UUID] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => UUID.fromString(decrypt(c)(value))),
    Type.text
  )

  def numeric(implicit c: CryptContext): Codec[BigDecimal] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => BigDecimal(decrypt(c)(value))),
    Type.text
  )

  def date(implicit c: CryptContext): Codec[LocalDate] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => LocalDate.parse(decrypt(c)(value))),
    Type.text
  )

  def timestamp(implicit c: CryptContext): Codec[LocalDateTime] = Codec.simple(
    value => encrypt(c)(value.toString),
    safe(value => LocalDateTime.parse(decrypt(c)(value))),
    Type.text
  )

  def timestamptz(implicit c: CryptContext): Codec[OffsetDateTime] =
    Codec.simple(
      value => encrypt(c)(value.toString),
      safe(value => OffsetDateTime.parse(decrypt(c)(value))),
      Type.text
    )
}

object crypt extends CryptCodecs {
  private val random = new SecureRandom()

  def encrypt(implicit c: CryptContext) = value => {
    val iv = new Array[Byte](GCM_IV_LENGTH)
    random.nextBytes(iv)
    genEncrypt(iv, c.secretKeys, value)
  }

  def decrypt(implicit c: CryptContext) = value => parseDecrypt(value)
}

object cryptd extends CryptCodecs {

  // Deterministic mode: derive the IV from the plaintext (synthetic IV, as in
  // AES-GCM-SIV) so that equal plaintexts encrypt to equal ciphertexts (keeping
  // the value searchable) while distinct plaintexts get distinct keystreams.
  // A fixed IV would reuse the GCM keystream across all values and leak their
  // XOR relationships.
  def encrypt(implicit c: CryptContext) = value => {
    val key = c.secretKeys.last
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key.getEncoded, "HmacSHA256"))
    val iv = mac
      .doFinal(value.getBytes(StandardCharsets.UTF_8))
      .take(GCM_IV_LENGTH)
    genEncrypt(iv, c.secretKeys, value)
  }

  def decrypt(implicit c: CryptContext) = value => parseDecrypt(value)
}
