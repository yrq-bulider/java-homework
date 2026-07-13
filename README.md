# easy-db

A C/S NoSQL key-value database implemented in Java for the Java course design (课程设计).

## Overview

`easy-db` is a lightweight in-memory key-value store with disk persistence. The server exposes a TCP text protocol (one command per line, `\n`-delimited). A simple Shell client is included for interactive use.

## Project Layout

```
easy-db/
├── pom.xml                # Maven build (JDK 8, Gson, JUnit, shade)
├── docs/                  # Design spec, implementation plan
└── (src/)                 # Source code (added in later tasks)
```

## Build

Requires JDK 8 and Maven 3.6+.

```bash
mvn package
```

This produces `target/easy-db-server.jar` — a self-contained fat JAR with `server.EasyDbServer` configured as the `Main-Class`.

## Run

```bash
java -jar target/easy-db-server.jar
```

The server listens on `127.0.0.1:9999` by default.

## Shell Client

A separate Shell tool connects to the running server and lets you issue commands interactively:

```bash
java -cp target/easy-db-server.jar client.ShellClient
```

## Supported Commands

This repository (Member A — 改 / write side) implements:

| Command | Description                          |
| ------- | ------------------------------------ |
| `SET k v`    | Set a single key to a string value |
| `MSET k1 v1 k2 v2 ...` | Set multiple keys at once |
| `DEL k`      | Delete a single key                 |
| `MDEL k1 k2 ...` | Delete multiple keys             |
| `FLUSH`      | Remove **all** keys                 |
| `PING`       | Health check; replies `PONG`        |
| `QUIT`       | Close the connection                |

The following read-side commands are implemented separately by Member B (改 / 查 — query side) and are **not** in scope for this repository:

- `GET k` — read a key
- `KEYS pattern` — list keys
- `EXISTS k` — check key existence

## Persistence

Data is written to `data/easy-db.json` and reloaded on startup. The exact snapshot strategy (write-through, periodic flush, etc.) is refined in later tasks.

## Testing

```bash
mvn test
```

JUnit 4 is used for unit tests.

## Team

- **Member A (组长)** — project skeleton, persistence, `SET` / `DEL` family, Shell client.
- **Member B** — `GET` / `KEYS` / `EXISTS` and other query commands.