# SKUNK CRYPT

## Motivation

Possibility to store columns encrypted in the database. At the application level, the data is available in plain text. The content will be encrypted using AES.

You can encrypt the data in a nondeterministic way, meaning the same plain text will be encrypted to different cipher text each time. Alternatively, you can encrypt the data in a deterministic way, which is helpful if you want to search for the data in the database.

The library is based depend on the latest 1.0 version of the Skunk library.

## Usage

1.) Add the dependency to your project:

```scala
libraryDependencies += "de.thatscalaguy" %% "skunk-crypt" % "0.0.0"
```

2.) Create a implicit CryptContext object with the keys you want to use.

```scala
implicit val c = CryptContext
        .keysFromHex(
          "c0e5c54c2a40c95b40d6e837a9c147d4cd7cadeccc555e679efed48f726a5fef"
        )
        .get
```

Only the last key will be use for encryption and decryption. The other keys are used to decrypt the data encrypted with the last key. (Simple way of manuel key rotation)

3.) Use it in your code:

```scala
import de.thatscalaguy.skunkcrypt.crypt // for non deterministic encryption
import de.thatscalaguy.skunkcrypt.cryptd // for deterministic encryption

session.execute(
    sql"INSERT INTO test (string, numbers) VALUES (${crypt.text}, ${crypt.int4})".command
)("Hello", 123)

session.execute(
    sql"SELECT * FROM test".query(crypt.text ~ crypt.int4)
)
```

All columns intended for storing encrypted data must be of type `TEXT` in the database.

e.g.:
```sql
CREATE TABLE test (
    string TEXT,
    numbers TEXT
)
```