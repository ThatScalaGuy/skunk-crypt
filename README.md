<div align="center">

# skunk-crypt 🦨🔐

**Transparent, application-level AES-GCM encryption for PostgreSQL columns — as drop-in [Skunk](https://typelevel.org/skunk/) codecs.**

*Plaintext in your application. Ciphertext in your database. Encryption is just another codec.*

[![Maven Central](https://img.shields.io/maven-central/v/de.thatscalaguy/skunk-crypt_3?style=flat-square&logo=apachemaven&logoColor=white&label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/de.thatscalaguy/skunk-crypt_3)
[![Cats Friendly](https://typelevel.org/cats/img/cats-badge-tiny.png)](https://typelevel.org/cats/#cats-friendly-libraries)
[![CI](https://img.shields.io/github/actions/workflow/status/ThatScalaGuy/skunk-crypt/ci.yml?branch=main&style=flat-square&logo=github&label=CI)](https://github.com/ThatScalaGuy/skunk-crypt/actions/workflows/ci.yml)
[![javadoc](https://javadoc.io/badge2/de.thatscalaguy/skunk-crypt_3/scaladoc.svg?style=flat-square&label=API%20docs)](https://javadoc.io/doc/de.thatscalaguy/skunk-crypt_3)

[![Scala 2.13 | 3](https://img.shields.io/badge/Scala-2.13%20%7C%203-DC322F?style=flat-square&logo=scala&logoColor=white)](https://www.scala-lang.org/)
[![JDK 8+](https://img.shields.io/badge/JDK-8%2B-007396?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)

</div>

## ✨ Highlights

- 🔒 **AES-256-GCM** authenticated encryption — values are confidential *and* tamper-evident.
- 🧩 **Drop-in Skunk codecs** — swap `text`/`int4`/… for `crypt.text`/`crypt.int4`; the rest of your query is unchanged.
- 🔍 **Deterministic mode** for equality search, using a synthetic IV (AES-GCM-SIV style) — searchable without the fixed-IV footgun.
- 🔑 **Built-in key rotation** — encrypt with the newest key, transparently decrypt with any previous one.
- 🧬 **Rich type support** — text, ints, floats, `Boolean`, `UUID`, `BigDecimal`, dates and timestamps.
- ⚡ **Zero ceremony** — one implicit `CryptContext`, no effect wrappers, no schema changes beyond `TEXT` columns.
- ✅ **Validated by construction** — keys are checked up front; decryption failures are typed.

## 🧩 Compatibility

| Dependency  | Version                          |
| ----------- | -------------------------------- |
| Scala       | 2.13, 3.3                        |
| Skunk       | 1.0                              |
| Cats Effect | 3.x                              |
| JDK         | 8+                               |
| PostgreSQL  | any — encrypted columns are `TEXT` |

> Skunk, Cats and Cats Effect are declared as `provided` dependencies, so
> skunk-crypt inherits the exact versions already on your classpath and never
> drags in a conflicting one.

## 📦 Installation

**sbt**

```scala
libraryDependencies += "de.thatscalaguy" %% "skunk-crypt" % "0.0.1"
```

**Mill**

```scala
ivy"de.thatscalaguy::skunk-crypt:0.0.1"
```

**scala-cli**

```scala
//> using dep de.thatscalaguy::skunk-crypt:0.0.1
```

You also need Skunk itself on the classpath (it is a `provided` dependency):

```scala
libraryDependencies += "org.tpolecat" %% "skunk-core" % "1.0.0"
```

## 🚀 Quick Start

### Generate a key

Keys are raw AES keys, hex-encoded — 64 hex characters for AES-256 (32 or 48 are
also accepted, for AES-128/192):

```sh
openssl rand -hex 32
```

Keep the key out of source control — load it from an environment variable, a
secrets manager, or your config of choice.

### Define a `CryptContext`

Construction is validated and returns an `Either`, so a malformed or wrong-length
key fails fast with a reason instead of blowing up later inside a query:

```scala
import de.thatscalaguy.skunkcrypt.*

given CryptContext =
  CryptContext
    .keysFromHex(sys.env("DB_ENC_KEY"))
    .fold(reason => sys.error(s"Invalid encryption key: $reason"), identity)
```

### Use the codecs

Encrypted columns are stored as `TEXT`, regardless of their logical type:

```sql
CREATE TABLE users (
  email TEXT,
  age   TEXT
)
```

```scala
import cats.effect.*
import skunk.*
import skunk.implicits.*
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.metrics.Meter
import de.thatscalaguy.skunkcrypt.*

object Demo extends IOApp.Simple:

  given Tracer[IO] = Tracer.Implicits.noop
  given Meter[IO]  = Meter.Implicits.noop

  // Generate with: openssl rand -hex 32
  given CryptContext =
    CryptContext
      .keysFromHex(sys.env("DB_ENC_KEY"))
      .fold(reason => sys.error(s"Invalid encryption key: $reason"), identity)

  val session: Resource[IO, Session[IO]] =
    Session
      .Builder[IO]
      .withHost("localhost")
      .withPort(5432)
      .withUserAndPassword("postgres", "postgres")
      .withDatabase("postgres")
      .single

  def run: IO[Unit] = session.use: s =>
    for
      _ <- s.execute(
             sql"INSERT INTO users (email, age) VALUES (${cryptd.text}, ${crypt.int4})".command
           )(("alice@example.com", 30))
      // The database now holds ciphertext; we read it back as plain values:
      rows <- s.execute(
                sql"SELECT email, age FROM users".query(cryptd.text ~ crypt.int4)
              )
      _ <- IO.println(rows) // List((alice@example.com, 30))
    yield ()
```

Because `email` was written with the deterministic codec, you can also look it up
by its encrypted value:

```scala
// the parameter is encrypted deterministically, so it matches the stored cipher text
val byEmail: Query[String, Int] =
  sql"SELECT age FROM users WHERE email = ${cryptd.text}".query(crypt.int4)
```

## 🔀 `crypt` vs `cryptd`

Both objects expose the same set of codecs; pick per column based on whether you
need to query by the encrypted value.

| Object   | Mode              | Same input → same cipher text? | Use it for                                  |
| -------- | ----------------- | ------------------------------ | ------------------------------------------- |
| `crypt`  | Non-deterministic | No (random IV)                 | The safe default — anything you don't search |
| `cryptd` | Deterministic     | Yes (synthetic IV)             | Columns you need to match with `WHERE x = ?` |

## 🔑 Key Rotation

`keysFromHex` accepts more than one key. Encryption always uses the **last** key;
the index of the key used is embedded in the stored value, so any earlier key can
still decrypt the rows it originally encrypted:

```scala
// new key encrypts; both keys decrypt
given CryptContext =
  CryptContext.keysFromHex(oldKeyHex, newKeyHex).fold(sys.error, identity)
```

This gives a simple manual rotation path: append a new key, and existing rows keep
decrypting until you re-encrypt them. Only ever **append** keys — never reorder or
remove them, or the embedded indices of existing rows will no longer match.

## 🧬 Supported Types

Available on both `crypt` and `cryptd`:

| Codec         | Scala type             |
| ------------- | ---------------------- |
| `text`        | `String`               |
| `int2`        | `Short`                |
| `int4`        | `Int`                  |
| `int8`        | `Long`                 |
| `float4`      | `Float`                |
| `float8`      | `Double`               |
| `bool`        | `Boolean`              |
| `uuid`        | `java.util.UUID`       |
| `numeric`     | `BigDecimal`           |
| `date`        | `java.time.LocalDate`  |
| `timestamp`   | `java.time.LocalDateTime` |
| `timestamptz` | `java.time.OffsetDateTime` |

## 🛡️ Security

- Values are encrypted with **AES-256-GCM**, an authenticated cipher: a modified or
  truncated cipher text fails to decrypt rather than returning garbage.
- The stored format is `base64(iv).keyIndex.base64(cipherText)`. The IV and key
  index travel with the value, so rotation and per-row IVs work without extra
  bookkeeping.
- `crypt` (non-deterministic) is the safe default: a fresh random IV per write means
  equal values are indistinguishable in the database.
- `cryptd` (deterministic) derives its IV from the plain text (a synthetic IV, as in
  AES-GCM-SIV), so equal values encrypt identically and stay searchable while
  distinct values still get distinct keystreams. By design it reveals **which rows
  share the same value** — only use it where that is acceptable.
- skunk-crypt encrypts column *values*; it does not hide column names, row counts,
  or access patterns, and it is not a substitute for transport (TLS) or
  at-rest disk encryption.

## 🧯 Error Handling

Key construction returns `Either[String, CryptContext]` — a bad key never reaches a
query. Decryption raises a typed `CryptError` (both subtypes extend
`RuntimeException`, so they propagate through Skunk's codec path):

| Error                | Meaning                                                        |
| -------------------- | ------------------------------------------------------------- |
| `MalformedCiphertext` | The stored value isn't `iv.keyIndex.data` (e.g. legacy plaintext) |
| `DecryptionFailure`   | Wrong key, unknown key index, or a failed authentication tag  |

## 🧪 Testing

```sh
sbt test
```

Pure codec tests run anywhere; the Skunk integration suite uses
[Testcontainers](https://testcontainers.com/) and needs a running Docker daemon to
start a PostgreSQL instance.

## 📖 Documentation

Full guide and API reference: <https://thatscalaguy.github.io/skunk-crypt/> ·
[Scaladoc](https://javadoc.io/doc/de.thatscalaguy/skunk-crypt_3)

## 💼 Commercial Support

skunk-crypt is built and maintained by **[ThatScalaGuy](https://www.thatscalaguy.de)** —
_software that's correct by construction._

Sven Herrmann helps teams ship dependable systems with functional programming on
Scala 3 and the Typelevel stack (Cats Effect, fs2, http4s, Skunk). Available for:

- 🧩 **Functional backends** — production services your team can trust for years
- 📊 **Data science & ML** — analytics and pipelines with Spark and Scala/Python
- 📝 **Requirements engineering** — turning ambiguous ideas into clear specs
- 🎓 **Training** — Scala 3, Cats Effect, fs2 streaming, Gleam, and AI agents

Need encryption, Postgres, or Typelevel help on your project?
**Get in touch at [thatscalaguy.de](https://www.thatscalaguy.de).**

## 🤝 Contributing

Issues and pull requests are welcome at
[ThatScalaGuy/skunk-crypt](https://github.com/ThatScalaGuy/skunk-crypt). Please run
`sbt headerCheckAll scalafmtCheckAll test` before opening a PR.

## 📄 License

Licensed under the [Apache License 2.0](LICENSE).

> **Upgrading from 0.0.1:** the deterministic mode previously used a fixed IV. Data
> written by `cryptd` before this change must be re-encrypted (read with the old
> version, write with the new). `crypt` data is unaffected.
