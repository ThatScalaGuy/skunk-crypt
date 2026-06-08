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

import munit.FunSuite
import skunk.Codec

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class CryptCodecsSuite extends FunSuite {

  val keyHex = "c0e5c54c2a40c95b40d6e837a9c147d4cd7cadeccc555e679efed48f726a5fef"
  implicit val c: CryptContext =
    CryptContext.keyFromHex(keyHex).fold(e => fail(e), identity)

  // Encode a value to the stored string, then decode it back.
  def roundtrip[A](codec: Codec[A], value: A): A =
    codec.decode(0, codec.encode(value).map(_.map(_.value))) match {
      case Right(a) => a
      case Left(e)  => fail(e.message)
    }

  def stored[A](codec: Codec[A], value: A): String =
    codec.encode(value).head.get.value

  test("key validation: valid 256-bit hex is accepted") {
    assert(CryptContext.keyFromHex(keyHex).isRight)
  }

  test("key validation: non-hex is rejected") {
    assert(CryptContext.keyFromHex("zz" * 32).isLeft)
  }

  test("key validation: wrong key length is rejected") {
    assert(CryptContext.keyFromHex("ab" * 30).isLeft) // 30 bytes
    assert(CryptContext.keyFromHex("abc").isLeft)      // odd length
  }

  test("key validation: empty key list is rejected") {
    assert(CryptContext.keysFromHex().isLeft)
  }

  test("key validation: reports the offending key index") {
    val result = CryptContext.keysFromHex(keyHex, "nothex")
    assert(clue(result).left.exists(_.contains("key #1")))
  }

  test("crypt (non-deterministic): same plaintext yields different ciphertext") {
    assertNotEquals(stored(crypt.text, "Hello"), stored(crypt.text, "Hello"))
  }

  test("crypt: round-trips") {
    assertEquals(roundtrip(crypt.text, "Hello, 世界"), "Hello, 世界")
  }

  test("cryptd (deterministic): same plaintext yields identical ciphertext") {
    assertEquals(stored(cryptd.text, "Hello"), stored(cryptd.text, "Hello"))
  }

  test("cryptd: distinct plaintexts use distinct IVs (no fixed-IV reuse)") {
    val ivA = stored(cryptd.text, "AAAA").split("\\.")(0)
    val ivB = stored(cryptd.text, "BBBB").split("\\.")(0)
    assertNotEquals(ivA, ivB)
  }

  test("cryptd: round-trips") {
    assertEquals(roundtrip(cryptd.text, "Hello, 世界"), "Hello, 世界")
  }

  test("new codecs round-trip (crypt)") {
    assertEquals(roundtrip(crypt.bool, true), true)
    val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    assertEquals(roundtrip(crypt.uuid, id), id)
    assertEquals(roundtrip(crypt.numeric, BigDecimal("123.45")), BigDecimal("123.45"))
    assertEquals(roundtrip(crypt.date, LocalDate.of(2026, 6, 8)), LocalDate.of(2026, 6, 8))
    val ts = LocalDateTime.of(2026, 6, 8, 13, 0, 0)
    assertEquals(roundtrip(crypt.timestamp, ts), ts)
    val tstz = OffsetDateTime.parse("2026-06-08T13:00:00+02:00")
    assertEquals(roundtrip(crypt.timestamptz, tstz), tstz)
  }

  test("new codecs round-trip (cryptd)") {
    assertEquals(roundtrip(cryptd.int4, 123), 123)
    assertEquals(roundtrip(cryptd.bool, false), false)
  }

  test("decrypting a malformed value raises MalformedCiphertext") {
    intercept[MalformedCiphertext] {
      crypt.text.decode(0, List(Some("not-a-valid-ciphertext")))
    }
  }

  test("decrypting with a wrong key raises DecryptionFailure") {
    val other =
      CryptContext.keyFromHex("a" * 64).fold(e => fail(e), identity)
    val ciphertext = stored(crypt.text, "secret")
    intercept[DecryptionFailure] {
      crypt.text(other).decode(0, List(Some(ciphertext)))
    }
  }
}
