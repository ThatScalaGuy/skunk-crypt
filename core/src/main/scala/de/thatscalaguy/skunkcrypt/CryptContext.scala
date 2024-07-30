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

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import scala.util.Try

final case class CryptContext(secretKeys: Array[SecretKey])

object CryptContext {
  def keyFromHex(hexString: String): Option[CryptContext] =
    Try {
      new SecretKeySpec(
        hexString.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte),
        "AES"
      )
    }.toOption.map(key => CryptContext(Array(key)))

  def keysFromHex(hexStrings: String*): Option[CryptContext] = {
    val keys = hexStrings.map(hex => keyFromHex(hex))
    keys.reduceLeft { (acc, key) =>
      for {
        a <- acc
        k <- key
      } yield CryptContext(a.secretKeys ++ k.secretKeys)
    }
  }
}
