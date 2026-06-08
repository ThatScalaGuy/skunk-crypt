# skunk-crypt 🦨🔐

Transparent, application-level **AES-256-GCM** encryption for PostgreSQL columns,
exposed as drop-in [Skunk](https://typelevel.org/skunk/) codecs. Your data is plain
text in the application and cipher text in the database — encryption is just another
codec.

## Highlights

- 🔒 **AES-256-GCM** authenticated encryption — confidential *and* tamper-evident.
- 🧩 **Drop-in codecs** — swap `text`/`int4`/… for `crypt.text`/`crypt.int4`.
- 🔍 **Deterministic mode** for equality search, using a synthetic IV (AES-GCM-SIV style).
- 🔑 **Built-in key rotation** — encrypt with the newest key, decrypt with any.
- 🧬 **Rich types** — text, ints, floats, `Boolean`, `UUID`, `BigDecimal`, dates and timestamps.

## Installation

This library is published for Scala 2.13 and 3.

```scala
libraryDependencies += "de.thatscalaguy" %% "skunk-crypt" % "@VERSION@"
```

Skunk, Cats and Cats Effect are `provided` dependencies, so skunk-crypt uses the
versions already on your classpath. Make sure Skunk itself is present:

```scala
libraryDependencies += "org.tpolecat" %% "skunk-core" % "1.0.0"
```

## Quick start

### 1. Generate a key

Keys are raw AES keys, hex-encoded — 64 hex characters for AES-256 (32 or 48 are
also accepted, for AES-128/192):

```sh
openssl rand -hex 32
```

### 2. Build a validated `CryptContext`

Construction returns an `Either`, so a malformed or wrong-length key fails fast with
a reason instead of surfacing later inside a query:

```scala
import de.thatscalaguy.skunkcrypt.*

given CryptContext =
  CryptContext
    .keysFromHex(sys.env("DB_ENC_KEY"))
    .fold(reason => sys.error(s"Invalid encryption key: $reason"), identity)
```

### 3. Use the codecs

Encrypted columns are stored as `TEXT`, regardless of their logical type:

```sql
CREATE TABLE users (
  email TEXT,
  age   TEXT
)
```

```scala
import skunk.*
import skunk.implicits.*
import de.thatscalaguy.skunkcrypt.*

// non-deterministic for `age`, deterministic (searchable) for `email`
val insert: Command[(String, Int)] =
  sql"INSERT INTO users (email, age) VALUES (${cryptd.text}, ${crypt.int4})".command

val all: Query[Void, (String, Int)] =
  sql"SELECT email, age FROM users".query(cryptd.text ~ crypt.int4)
```

## `crypt` vs `cryptd`

Both objects expose the same codecs.

- **`crypt`** — non-deterministic (random IV). The safe default; equal values are
  indistinguishable in the database. Use it for anything you don't query by.
- **`cryptd`** — deterministic; equal values encrypt identically (via a synthetic
  IV derived from the plain text), so the column can be matched with `WHERE x = ?`.
  By design it reveals which rows share the same value.

## Key rotation

`keysFromHex` accepts several keys. Encryption uses the **last** key; the index of
the key used is embedded in each stored value, so older keys keep decrypting older
rows. Only ever **append** keys — never reorder or remove them.

```scala
import de.thatscalaguy.skunkcrypt.*

given CryptContext =
  CryptContext.keysFromHex(oldKeyHex, newKeyHex).fold(sys.error, identity)
```

## Supported types

`text`, `int2`, `int4`, `int8`, `float4`, `float8`, `bool`, `uuid`, `numeric`
(`BigDecimal`), `date` (`LocalDate`), `timestamp` (`LocalDateTime`) and
`timestamptz` (`OffsetDateTime`).

## Errors

Decryption raises a typed `CryptError`:

- `MalformedCiphertext` — the stored value is not in the expected
  `iv.keyIndex.data` format.
- `DecryptionFailure` — wrong key, unknown key index, or a failed authentication tag.

See the [README](https://github.com/ThatScalaGuy/skunk-crypt) for the full security
model and the [Scaladoc](https://javadoc.io/doc/de.thatscalaguy/skunk-crypt_3) for
the API reference.
