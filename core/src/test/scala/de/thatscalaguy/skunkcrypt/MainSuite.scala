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

import cats.effect.*
import cats.effect.IO
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import de.thatscalaguy.skunkcrypt.crypt
import munit.CatsEffectSuite
import org.testcontainers.containers.wait.strategy.Wait
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import skunk.*
import skunk.SSL
import skunk.Session
import skunk.codec.all.*
import skunk.implicits.*

class MainSuite extends CatsEffectSuite with TestContainerForAll {

  def session(port: Int) = Session
    .single[IO](
      host = "localhost",
      port = port,
      user = "postgres",
      database = "postgres",
      password = Some("postgres"),
      ssl = SSL.None
    )

  override def afterContainersStart(containers: Containers): Unit = {
    session(containers.container.getMappedPort(5432))
      .use { session =>
        session.execute(
          sql"CREATE TABLE test (string TEXT, numbers TEXT)".command
        )
      }
      .unsafeRunSync()
  }

  override val containerDef = GenericContainer.Def(
    dockerImage = "postgres:16",
    env = Map("POSTGRES_PASSWORD" -> "postgres"),
    exposedPorts = Seq(5432),
    waitStrategy = Wait
      .forListeningPort()
      .withStartupTimeout(java.time.Duration.ofSeconds(60))
  )

  test("Main should exit succesfully") {
    withContainers { case database: GenericContainer =>
      implicit val c = CryptContext
        .keysFromHex(
          "c0e5c54c2a40c95b40d6e837a9c147d4cd7cadeccc555e679efed48f726a5fef",
          "c0e5c54c2a40c95b40d6e837a9c147d4cd7cadeccc555e679efed48f726a5fef"
        )
        .get
      session(database.container.getMappedPort(5432))
        .use { session =>
          for {
            // _ <- session.execute(
            //   sql"INSERT INTO test (string, numbers) VALUES (${text}, ${crypt.int4})".command
            // )("hpc3AZ+t1m7mDBf2.e11YUVCQUkPdytj441OjImPhnElN+wSOLL7liXcB+TeRbrsESuGdidbndfu3", 123)
            _ <- session.execute(
              sql"INSERT INTO test (string, numbers) VALUES (${cryptd.text}, ${cryptd.int4})".command
            )("Hello", 123)
            _ <- session
              .execute(
                sql"SELECT * FROM test".query(cryptd.text ~ text)
              )
              .map(_.foreach(println))

            _ <- session
              .execute(
                sql"SELECT * FROM test"
                  .query(cryptd.text ~ cryptd.int4)
              )
              .map(_.foreach(println))
          } yield ()

        }
        .unsafeRunSync()
    }
  }

}
