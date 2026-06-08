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

import cats.effect.IO
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import org.testcontainers.containers.wait.strategy.Wait
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer
import skunk.*
import skunk.implicits.*

class MainSuite extends CatsEffectSuite with TestContainerForAll {
  implicit val tracer: Tracer[IO] = Tracer.Implicits.noop
  implicit val meter: Meter[IO]   = Meter.Implicits.noop

  override val containerDef = GenericContainer.Def(
    dockerImage = "postgres:16",
    env = Map("POSTGRES_PASSWORD" -> "postgres"),
    exposedPorts = Seq(5432),
    waitStrategy = Wait
      .forListeningPort()
      .withStartupTimeout(java.time.Duration.ofSeconds(60))
  )

  def session(port: Int) = Session
    .Builder[IO]
    .withHost("localhost")
    .withPort(port)
    .withUserAndPassword("postgres", "postgres")
    .withDatabase("postgres")
    .withSSL(SSL.None)
    .single

  val keyHex                   = "c0e5c54c2a40c95b40d6e837a9c147d4cd7cadeccc555e679efed48f726a5fef"
  implicit val c: CryptContext =
    CryptContext.keysFromHex(keyHex, keyHex).fold(e => fail(e), identity)

  override def afterContainersStart(containers: Containers): Unit = {
    session(
      containers.asInstanceOf[GenericContainer].container.getMappedPort(5432)
    ).use { session =>
      session.execute(
        sql"CREATE TABLE test (string TEXT, numbers TEXT)".command
      ) *>
        session.execute(
          sql"CREATE TABLE types (id TEXT, flag TEXT, amount TEXT, at TEXT)".command
        )
    }.void
      .unsafeRunSync()
  }

  test("round-trips text and int columns through Postgres") {
    withContainers { case database: GenericContainer =>
      session(database.container.getMappedPort(5432)).use { session =>
        for {
          _ <- session.execute(
            sql"INSERT INTO test (string, numbers) VALUES (${cryptd.text}, ${cryptd.int4})".command
          )(("Hello", 123))
          rows <- session.execute(
            sql"SELECT string, numbers FROM test".query(cryptd.text ~ cryptd.int4)
          )
        } yield assertEquals(rows, List("Hello" -> 123))
      }.unsafeRunSync()
    }
  }

  test("round-trips uuid, bool, numeric and timestamptz through Postgres") {
    withContainers { case database: GenericContainer =>
      val id     = java.util.UUID.randomUUID()
      val amount = BigDecimal("99.95")
      val at     = java.time.OffsetDateTime.parse("2026-06-08T13:00:00+02:00")
      session(database.container.getMappedPort(5432)).use { session =>
        for {
          _ <- session.execute(
            sql"INSERT INTO types (id, flag, amount, at) VALUES (${crypt.uuid}, ${crypt.bool}, ${crypt.numeric}, ${crypt.timestamptz})".command
          )((id, true, amount, at))
          rows <- session.execute(
            sql"SELECT id, flag, amount, at FROM types"
              .query(crypt.uuid ~ crypt.bool ~ crypt.numeric ~ crypt.timestamptz)
          )
        } yield assertEquals(rows, List((((id, true), amount), at)))
      }.unsafeRunSync()
    }
  }

}
