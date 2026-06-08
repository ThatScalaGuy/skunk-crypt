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

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

final case class CryptContext(secretKeys: Array[SecretKey])

object CryptContext {

  // AES accepts 128-, 192- and 256-bit keys.
  private val validKeyLengths = Set(16, 24, 32)

  /** Build a context from a single hex-encoded AES key. Returns `Left` with the reason if the string is not valid hex or not a valid AES key length (32/48/64
    * hex characters for AES-128/192/256).
    */
  def keyFromHex(hexString: String): Either[String, CryptContext] =
    for {
      bytes <- Either
        .catchNonFatal(
          hexString.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
        )
        .leftMap(_ => s"Invalid hex key: '$hexString'")
      _ <- Either.cond(
        hexString.length % 2 == 0 && validKeyLengths(bytes.length),
        (),
        s"Invalid AES key length: ${bytes.length} bytes (expected 16, 24 or 32)"
      )
    } yield CryptContext(Array(new SecretKeySpec(bytes, "AES")))

  /** Build a context from multiple hex-encoded AES keys for key rotation. The last key is used for encryption; any key can decrypt values that were encrypted
    * while it was last (its index is embedded in the ciphertext), so only ever append keys — never reorder or remove them. Returns `Left` for the first invalid
    * key, including its position.
    */
  def keysFromHex(hexStrings: String*): Either[String, CryptContext] =
    for {
      _    <- Either.cond(hexStrings.nonEmpty, (), "At least one key is required")
      ctxs <- hexStrings.zipWithIndex.toList.traverse { case (hex, i) =>
        keyFromHex(hex).leftMap(reason => s"key #$i: $reason")
      }
    } yield CryptContext(ctxs.toArray.flatMap(_.secretKeys))
}
