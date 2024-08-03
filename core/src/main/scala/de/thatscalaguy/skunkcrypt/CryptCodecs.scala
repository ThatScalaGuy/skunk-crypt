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

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

trait CryptCodecs {
  val GCM_IV_LENGTH = 12
  val GCM_TAG_LENGTH = 16

  private[skunkcrypt] def encrypt(implicit c: CryptContext): String => String
  private[skunkcrypt] def decrypt(implicit c: CryptContext): String => String

  private[skunkcrypt] def genEncrypt(
      iv: Array[Byte],
      secretKeys: Array[SecretKey],
      value: String
  ) = {
    val secretKey = secretKeys.last
    val ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
    val encryptedBytes = cipher.doFinal(value.getBytes())
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
    val ivSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    new String(cipher.doFinal(encryptedBytes))
  }
}

private[skunkcrypt] trait Base {
  self: CryptCodecs =>
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

object crypt extends CryptCodecs with Base {
  private val random = new SecureRandom()

  def encrypt(implicit c: CryptContext) = value => {
    val iv = new Array[Byte](GCM_IV_LENGTH)
    random.nextBytes(iv)
    genEncrypt(iv, c.secretKeys, value)
  }

  def decrypt(implicit c: CryptContext) = value =>
    value.split("\\.").toList match {
      case iv :: keyIndex :: encrypted :: Nil =>
        val ivBytes = Base64.getDecoder.decode(iv)
        val secretKey = c.secretKeys(keyIndex.toInt)
        genDecrypt(ivBytes, secretKey, encrypted)
      case _ => throw new Exception("Invalid input")
    }
}

object cryptd extends CryptCodecs with Base {

  def encrypt(implicit c: CryptContext) = value => {
    val iv = new Array[Byte](GCM_IV_LENGTH)
    genEncrypt(iv, c.secretKeys, value)
  }

  def decrypt(implicit c: CryptContext) = value =>
    value.split("\\.").toList match {
      case iv :: keyIndex :: encrypted :: Nil =>
        val ivBytes = Base64.getDecoder.decode(iv)
        val secretKey = c.secretKeys(keyIndex.toInt)
        genDecrypt(ivBytes, secretKey, encrypted)
      case _ => throw new Exception("Invalid input")
    }
}
