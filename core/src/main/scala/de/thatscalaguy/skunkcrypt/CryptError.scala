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

/** Errors raised while decrypting a column value. They extend `RuntimeException` so they surface through Skunk's synchronous codec path and can be caught by
  * consumers.
  */
sealed abstract class CryptError(message: String, cause: Throwable) extends RuntimeException(message, cause)

/** The stored value does not match the expected `<iv>.<keyIndex>.<data>` layout (e.g. legacy plaintext or data written by a different library).
  */
final class MalformedCiphertext
    extends CryptError(
      "Malformed ciphertext: expected '<iv>.<keyIndex>.<data>'",
      null
    )

/** The value was well-formed but could not be decrypted — an unknown key index, a wrong key, or a failed GCM authentication tag.
  */
final class DecryptionFailure(cause: Throwable) extends CryptError(s"Decryption failed: ${cause.getMessage}", cause)
