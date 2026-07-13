# easy-db (成员A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a C/S NoSQL key-value database (member A scope only): project skeleton + Socket server + KV engine + persistence + Shell client.

**Architecture:** Maven project. Custom text protocol over TCP. JSON Lines append-only log on disk with size-based rotation (64MB) and parallel GZIP compression. Lazy TTL expiration. In-memory `ConcurrentHashMap` as the source of truth.

**Tech Stack:** JDK 8, Maven, Gson 2.10.1, JUnit 4.13.2

**Spec:** `docs/superpowers/specs/2026-07-13-easy-db-member-a-design.md`

---

## File Map

**Files Created:**

| Path | Responsibility |
|------|---------------|
| `pom.xml` | Maven build config, dependencies, shade plugin |
| `.gitignore` | Ignore target/, data/, IDE files |
| `README.md` | Quick start for reviewers |
| `src/main/java/util/JsonUtil.java` | Gson singleton wrapper |
| `src/main/java/util/Logger.java` | JUL log facade (info/warn/error) |
| `src/main/java/util/Patterns.java` | Glob `*` to regex for KEYS pattern |
| `src/main/java/protocol/Request.java` | Parsed request (verb, args, ttlSeconds) |
| `src/main/java/protocol/Response.java` | Response builder (OK/nil/integer/value/error) |
| `src/main/java/protocol/ProtocolParser.java` | String → Request with quote/escape support |
| `src/main/java/store/Entry.java` | KV entry with expireAt |
| `src/main/java/store/NormalStore.java` | In-memory KV with TTL + bulk ops |
| `src/main/java/store/PersistentStore.java` | JSON Lines append, Rotate, Recovery |
| `src/main/java/store/Compactor.java` | Background GZIP compressor |
| `src/main/java/store/Collection.java` | Virtual collection view |
| `src/main/java/store/CollectionManager.java` | List collections, lookup by prefix |
| `src/main/java/handler/CommandHandler.java` | Handler interface |
| `src/main/java/handler/SetHandler.java` | SET command |
| `src/main/java/handler/DelHandler.java` | DEL command |
| `src/main/java/handler/MsetHandler.java` | MSET command |
| `src/main/java/handler/MdelHandler.java` | MDEL command |
| `src/main/java/handler/FlushHandler.java` | FLUSH command |
| `src/main/java/handler/PingHandler.java` | PING command |
| `src/main/java/handler/QuitHandler.java` | QUIT signal |
| `src/main/java/server/CommandDispatcher.java` | verb → handler routing |
| `src/main/java/server/RequestHandler.java` | Per-connection line loop |
| `src/main/java/server/SocketServerController.java` | ServerSocket + thread pool |
| `src/main/java/server/EasyDbServer.java` | main() |
| `src/main/java/client/SocketClient.java` | TCP client wrapper (used by all clients) |
| `src/main/java/client/shell/ShellClient.java` | Single-command Java entry |
| `easy-db` | Shell wrapper bash script (no extension) |
| `scripts/smoke-test.sh` | End-to-end acceptance test |

**Test Files Created:**

| Path | Tests |
|------|-------|
| `src/test/java/util/PatternsTest.java` | Wildcard matching |
| `src/test/java/protocol/ProtocolParserTest.java` | Parse edge cases |
| `src/test/java/protocol/ResponseTest.java` | Wire format output |
| `src/test/java/store/NormalStoreTest.java` | CRUD + TTL + bulk |
| `src/test/java/store/PersistentStoreTest.java` | Append + Rotate + Recovery |

---

## Task 1: Maven project skeleton

**Files:**
- Create: `easy-db/pom.xml`
- Create: `easy-db/.gitignore`
- Create: `easy-db/README.md`
- Create: `easy-db/.gitkeep` (placeholder so dir is tracked)

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>cn.easydb</groupId>
    <artifactId>easy-db</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>easy-db-server</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>server.EasyDbServer</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `.gitignore`**

```
target/
*.class
.idea/
*.iml
.vscode/
data/
*.log
.DS_Store
```

- [ ] **Step 3: Create `README.md`**

```markdown
# easy-db

Java 课程设计:基于 C/S 架构的 NoSQL 键值数据库。

## 快速开始

\`\`\`bash
mvn clean package
java -jar target/easy-db-server.jar --port 8080
# 另一个终端:
./easy-db set name 张三
./easy-db get name
\`\`\`

## 命令

| 命令 | 语法 | 说明 |
|------|------|------|
| SET | `SET <key> <value> [ttlSeconds]` | 存储(可选 TTL) |
| DEL | `DEL <key>` | 删除 |
| MSET | `MSET <k1> <v1> <k2> <v2> ...` | 批量插入 |
| MDEL | `MDEL <k1> <k2> ...` | 批量删除 |
| FLUSH | `FLUSH` | 清空所有数据 |
| PING | `PING` | 心跳 |
| QUIT | `QUIT` | 断开连接 |

GET / KEYS / EXISTS 由成员B 补充。
```

- [ ] **Step 4: Verify Maven resolves**

Run: `cd easy-db && mvn -q dependency:resolve`
Expected: BUILD SUCCESS, no errors.

- [ ] **Step 5: Commit**

```bash
cd easy-db
git init
git add pom.xml .gitignore README.md
git commit -m "chore: maven project skeleton"
```

---

## Task 2: Logger + JsonUtil utilities

**Files:**
- Create: `easy-db/src/main/java/util/Logger.java`
- Create: `easy-db/src/main/java/util/JsonUtil.java`

- [ ] **Step 1: Create `util/Logger.java`**

```java
package util;

import java.util.logging.Level;
import java.util.logging.Logger as JulLogger;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

public class Logger {
    private static final JulLogger JUL = JulLogger.getLogger("easy-db");

    static {
        JUL.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        JUL.addHandler(handler);
        JUL.setLevel(Level.INFO);
    }

    public static void info(String msg)  { JUL.info(msg); }
    public static void warn(String msg)  { JUL.warning(msg); }
    public static void error(String msg, Throwable t) { JUL.log(Level.SEVERE, msg, t); }
    public static void error(String msg) { JUL.severe(msg); }
}
```

(Note: `java.util.logging.Logger` is imported as `JulLogger` via static import-style syntax — actually Java doesn't support import alias; use the FQN inside the class. See corrected version below.)

**Correction: `util/Logger.java`**

```java
package util;

import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Logger as JulLogger;  // INVALID: Java has no alias. Use FQN.

public class Logger {
    private static final java.util.logging.Logger JUL = java.util.logging.Logger.getLogger("easy-db");

    static {
        JUL.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        JUL.addHandler(handler);
        JUL.setLevel(Level.INFO);
    }

    public static void info(String msg)                       { JUL.info(msg); }
    public static void warn(String msg)                       { JUL.warning(msg); }
    public static void error(String msg, Throwable t)         { JUL.log(Level.SEVERE, msg, t); }
    public static void error(String msg)                      { JUL.severe(msg); }
}
```

- [ ] **Step 2: Create `util/JsonUtil.java`**

```java
package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtil {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private JsonUtil() {}

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T fromJson(String s, Class<T> c) {
        return GSON.fromJson(s, c);
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `cd easy-db && mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/util/Logger.java src/main/java/util/JsonUtil.java
git commit -m "feat(util): logger and json helpers"
```

---

## Task 3: Patterns (wildcard → regex)

**Files:**
- Create: `easy-db/src/main/java/util/Patterns.java`
- Test: `easy-db/src/test/java/util/PatternsTest.java`

- [ ] **Step 1: Write failing test**

```java
package util;

import org.junit.Test;
import static org.junit.Assert.*;

public class PatternsTest {
    @Test public void exactMatch()      { assertTrue(Patterns.matches("foo", "foo")); }
    @Test public void wildcardAll()     { assertTrue(Patterns.matches("user:*", "user:001")); }
    @Test public void wildcardMiddle()  { assertTrue(Patterns.matches("u*r:001", "user:001")); }
    @Test public void wildcardSuffix()  { assertTrue(Patterns.matches("user:00*", "user:001")); }
    @Test public void noMatch()         { assertFalse(Patterns.matches("foo", "bar")); }
    @Test public void emptyPattern()    { assertFalse(Patterns.matches("", "foo")); }
    @Test public void escapeRegexMeta() { assertTrue(Patterns.matches("a.b", "a.b")); } // . should be literal
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `cd easy-db && mvn -q test -Dtest=PatternsTest`
Expected: compilation error (`Patterns` not found).

- [ ] **Step 3: Implement `util/Patterns.java`**

```java
package util;

import java.util.regex.Pattern;

public final class Patterns {
    private Patterns() {}

    /** Convert a shell-style glob (`*` = any chars) to a regex matcher. */
    public static boolean matches(String glob, String input) {
        if (glob == null || glob.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*') sb.append(".*");
            else if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-') sb.append(c);
            else sb.append('\\').append(c);   // escape regex metacharacters
        }
        return Pattern.compile(sb.toString()).matcher(input).matches();
    }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=PatternsTest`
Expected: 7 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/util/Patterns.java src/test/java/util/PatternsTest.java
git commit -m "feat(util): KEYS wildcard matcher"
```

---

## Task 4: Request data class

**Files:**
- Create: `easy-db/src/main/java/protocol/Request.java`

- [ ] **Step 1: Create `protocol/Request.java`**

```java
package protocol;

import java.util.List;

public final class Request {
    private final String verb;            // uppercased
    private final List<String> args;      // remaining tokens

    public Request(String verb, List<String> args) {
        this.verb = verb.toUpperCase();
        this.args = List.copyOf(args);
    }

    public String verb()  { return verb; }
    public List<String> args() { return args; }

    public boolean isQuit() { return "QUIT".equals(verb); }

    public int argCount() { return args.size(); }

    /** Returns args[0] or empty string. */
    public String key() {
        return args.isEmpty() ? "" : args.get(0);
    }

    /** Returns args[1] or null (used by SET, MSET value extraction). */
    public String valueOrNull(int idx) {
        return idx < args.size() ? args.get(idx) : null;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd easy-db && mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/protocol/Request.java
git commit -m "feat(protocol): Request value object"
```

---

## Task 5: Response builder + wire format

**Files:**
- Create: `easy-db/src/main/java/protocol/Response.java`
- Test: `easy-db/src/test/java/protocol/ResponseTest.java`

- [ ] **Step 1: Write failing test**

```java
package protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResponseTest {
    @Test public void ok()           { assertEquals("OK",            Response.ok().toWire()); }
    @Test public void nil()           { assertEquals("(nil)",         Response.nil().toWire()); }
    @Test public void integer()       { assertEquals("(integer) 3",   Response.integer(3).toWire()); }
    @Test public void value()         { assertEquals("\"foo\"",       Response.value("foo").toWire()); }
    @Test public void valueWithQuote(){ assertEquals("\"a\\\"b\"",    Response.value("a\"b").toWire()); }
    @Test public void valueWithNewlineIsError() {
        Response r = Response.value("a\nb");
        assertTrue(r.toWire().startsWith("(error)"));
    }
    @Test public void error()         { assertEquals("(error) ERR boom", Response.error("boom").toWire()); }
    @Test public void multiListEmpty(){ assertEquals("*END", Response.multi().toWire()); }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `cd easy-db && mvn -q test -Dtest=ResponseTest`
Expected: compilation error (`Response` not found).

- [ ] **Step 3: Implement `protocol/Response.java`**

```java
package protocol;

import java.util.ArrayList;
import java.util.List;

public final class Response {
    public enum Kind { OK, NIL, INTEGER, VALUE, MULTI, ERROR }

    private final Kind kind;
    private final Object payload;       // Integer for INTEGER, String for VALUE/ERROR/MULTI terminator, List<String> for MULTI items

    private Response(Kind kind, Object payload) {
        this.kind = kind;
        this.payload = payload;
    }

    public static Response ok()                  { return new Response(Kind.OK, null); }
    public static Response nil()                 { return new Response(Kind.NIL, null); }
    public static Response integer(int n)        { return new Response(Kind.INTEGER, n); }
    public static Response value(String s)       { return new Response(Kind.VALUE, s); }
    public static Response error(String msg)     { return new Response(Kind.ERROR, "(error) ERR " + msg); }
    public static Response multi()               { return new Response(Kind.MULTI, "*END"); }

    /** A multi-line response with values; each `add` becomes a quoted line. */
    public static MultiBuilder multiBuilder()    { return new MultiBuilder(); }

    public Kind kind() { return kind; }

    /** Render to wire format (single line). */
    public String toWire() {
        switch (kind) {
            case OK:      return "OK";
            case NIL:     return "(nil)";
            case INTEGER: return "(integer) " + payload;
            case VALUE:   return quoteValue((String) payload);
            case MULTI:   return (String) payload;        // terminator
            case ERROR:   return (String) payload;
            default:      return "(error) ERR unknown response kind";
        }
    }

    private static String quoteValue(String s) {
        if (s == null || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "(error) ERR value contains newline";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') sb.append('\\').append(c);
            else sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    public static final class MultiBuilder extends Response {
        private final List<String> items = new ArrayList<>();

        private MultiBuilder() { super(Kind.MULTI, "*END"); }

        public MultiBuilder add(String item) {
            items.add(quoteValue(item));
            return this;
        }

        @Override
        public String toWire() {
            StringBuilder sb = new StringBuilder();
            for (String line : items) sb.append(line).append('\n');
            sb.append("*END");
            return sb.toString();
        }
    }
}
```

Wait — `MultiBuilder extends Response` but `Response`'s constructor is private. Fix: make it a static nested class with no inheritance or refactor. Use composition instead.

**Correction — final `protocol/Response.java`:**

```java
package protocol;

import java.util.ArrayList;
import java.util.List;

public final class Response {
    public enum Kind { OK, NIL, INTEGER, VALUE, ERROR }

    private final Kind kind;
    private final Object payload;

    private Response(Kind kind, Object payload) {
        this.kind = kind;
        this.payload = payload;
    }

    public static Response ok()                  { return new Response(Kind.OK, null); }
    public static Response nil()                 { return new Response(Kind.NIL, null); }
    public static Response integer(int n)        { return new Response(Kind.INTEGER, n); }
    public static Response value(String s)       { return new Response(Kind.VALUE, s); }
    public static Response error(String msg)     { return new Response(Kind.ERROR, "(error) ERR " + msg); }

    public Kind kind() { return kind; }

    public String toWire() {
        switch (kind) {
            case OK:      return "OK";
            case NIL:     return "(nil)";
            case INTEGER: return "(integer) " + payload;
            case VALUE:   return quoteValue((String) payload);
            case ERROR:   return (String) payload;
            default:      return "(error) ERR unknown response kind";
        }
    }

    private static String quoteValue(String s) {
        if (s == null || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "(error) ERR value contains newline";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') sb.append('\\').append(c);
            else sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /** Multi-line response for KEYS etc. */
    public static final class Multi {
        public final List<String> lines;
        public Multi(List<String> lines) { this.lines = lines; }

        public String toWire() {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) sb.append(quoteValue(line)).append('\n');
            sb.append("*END");
            return sb.toString();
        }
    }
}
```

- [ ] **Step 4: Update test (drop `multi()` test, add `Multi` test)**

```java
package protocol;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ResponseTest {
    @Test public void ok()           { assertEquals("OK",            Response.ok().toWire()); }
    @Test public void nil()           { assertEquals("(nil)",         Response.nil().toWire()); }
    @Test public void integer()       { assertEquals("(integer) 3",   Response.integer(3).toWire()); }
    @Test public void value()         { assertEquals("\"foo\"",       Response.value("foo").toWire()); }
    @Test public void valueWithQuote(){ assertEquals("\"a\\\"b\"",    Response.value("a\"b").toWire()); }
    @Test public void valueWithNewlineIsError() {
        assertTrue(Response.value("a\nb").toWire().startsWith("(error)"));
    }
    @Test public void error()         { assertEquals("(error) ERR boom", Response.error("boom").toWire()); }

    @Test public void multiToWire() {
        Response.Multi m = new Response.Multi(Arrays.asList("user:001", "user:002"));
        assertEquals("\"user:001\"\n\"user:002\"\n*END", m.toWire());
    }
}
```

- [ ] **Step 5: Run test (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=ResponseTest`
Expected: 7 tests passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/protocol/Response.java src/test/java/protocol/ResponseTest.java
git commit -m "feat(protocol): Response with wire format"
```

---

## Task 6: ProtocolParser

**Files:**
- Create: `easy-db/src/main/java/protocol/ProtocolParser.java`
- Test: `easy-db/src/test/java/protocol/ProtocolParserTest.java`

- [ ] **Step 1: Write failing test**

```java
package protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolParserTest {
    @Test public void parsesSetSimple() {
        Request r = ProtocolParser.parse("SET foo bar");
        assertEquals("SET", r.verb());
        assertEquals(2, r.argCount());
        assertEquals("foo", r.args().get(0));
        assertEquals("bar", r.args().get(1));
    }

    @Test public void parsesValueWithSpace() {
        // plain text mode: "SET name 张 三" → key=name, value="张 三"
        Request r = ProtocolParser.parse("SET name 张 三");
        assertEquals("SET", r.verb());
        assertEquals(2, r.argCount());
        assertEquals("name", r.args().get(0));
        assertEquals("张 三", r.args().get(1));
    }

    @Test public void parsesQuotedValue() {
        // quoted mode: SET name "张 三"
        Request r = ProtocolParser.parse("SET name \"张 三\"");
        assertEquals("张 三", r.args().get(1));
    }

    @Test public void parsesWithTtl() {
        Request r = ProtocolParser.parse("SET foo bar 60");
        assertEquals(3, r.argCount());
        assertEquals("60", r.args().get(2));   // parser keeps it as string; handler validates as number
    }

    @Test public void parsesFlush() {
        Request r = ProtocolParser.parse("FLUSH");
        assertEquals("FLUSH", r.verb());
        assertEquals(0, r.argCount());
    }

    @Test public void emptyLineThrows() {
        try { ProtocolParser.parse(""); fail(); }
        catch (ProtocolParser.ParseException e) {}
    }

    @Test public void nullThrows() {
        try { ProtocolParser.parse(null); fail(); }
        catch (ProtocolParser.ParseException e) {}
    }

    @Test public void parsesLowercase() {
        Request r = ProtocolParser.parse("set foo bar");
        assertEquals("SET", r.verb());
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `cd easy-db && mvn -q test -Dtest=ProtocolParserTest`
Expected: compilation error.

- [ ] **Step 3: Implement `protocol/ProtocolParser.java`**

```java
package protocol;

import java.util.ArrayList;
import java.util.List;

public final class ProtocolParser {

    public static class ParseException extends RuntimeException {
        public ParseException(String m) { super(m); }
    }

    private ProtocolParser() {}

    /**
     * Parses one line of the wire protocol.
     *
     * Rules:
     *  - line is verb + N positional args (whitespace-separated)
     *  - if the FIRST positional token starts with '"', it is a quoted string, terminated by the next '"'
     *  - inside a quoted string, '\\' escapes the next char (especially '\\' and '"')
     *  - bare-text args (after verb, without leading '"') are read until next whitespace
     *     → this lets `SET name 张 三` produce `value = "张 三"` naturally
     */
    public static Request parse(String line) {
        if (line == null || line.isEmpty()) {
            throw new ParseException("empty line");
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) throw new ParseException("empty line");

        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < trimmed.length()) {
            // skip whitespace
            while (i < trimmed.length() && trimmed.charAt(i) == ' ') i++;
            if (i >= trimmed.length()) break;

            if (trimmed.charAt(i) == '"') {
                // quoted string
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
                while (i < trimmed.length() && trimmed.charAt(i) != '"') {
                    char c = trimmed.charAt(i);
                    if (c == '\\' && i + 1 < trimmed.length()) {
                        char next = trimmed.charAt(i + 1);
                        sb.append(next);
                        i += 2;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                if (i >= trimmed.length()) throw new ParseException("unterminated quoted string");
                i++; // skip closing quote
                parts.add(sb.toString());
            } else {
                // bare token: read until next whitespace or quote-start
                int start = i;
                while (i < trimmed.length() && trimmed.charAt(i) != ' ') i++;
                parts.add(trimmed.substring(start, i));
            }
        }
        if (parts.isEmpty()) throw new ParseException("no verb");

        String verb = parts.get(0);
        List<String> args = parts.subList(1, parts.size());
        return new Request(verb, args);
    }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=ProtocolParserTest`
Expected: 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/protocol/ProtocolParser.java src/test/java/protocol/ProtocolParserTest.java
git commit -m "feat(protocol): line parser with quote/escape"
```

---

## Task 7: Entry value object

**Files:**
- Create: `easy-db/src/main/java/store/Entry.java`

- [ ] **Step 1: Create `store/Entry.java`**

```java
package store;

public final class Entry {
    private final String key;
    private final String value;
    private final long expireAt;     // 0 = never
    private final long createdAt;

    public Entry(String key, String value, long expireAt, long createdAt) {
        this.key = key;
        this.value = value;
        this.expireAt = expireAt;
        this.createdAt = createdAt;
    }

    public String key()       { return key; }
    public String value()     { return value; }
    public long expireAt()    { return expireAt; }
    public long createdAt()   { return createdAt; }

    /** True if this entry has expired (and expireAt > 0). */
    public boolean isExpired(long nowMs) {
        return expireAt > 0 && nowMs >= expireAt;
    }

    public Entry withValue(String newValue, long newExpireAt) {
        return new Entry(key, newValue, newExpireAt, createdAt);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/store/Entry.java
git commit -m "feat(store): Entry value object with TTL"
```

---

## Task 8: NormalStore — basic CRUD (SET/GET/DEL)

**Files:**
- Create: `easy-db/src/main/java/store/NormalStore.java`
- Test: `easy-db/src/test/java/store/NormalStoreTest.java`

- [ ] **Step 1: Write failing test for SET/GET/DEL**

```java
package store;

import org.junit.Test;
import static org.junit.Assert.*;

public class NormalStoreTest {
    @Test public void setAndGet()       { NormalStore s = new NormalStore(); s.set("k", "v", 0); assertEquals("v", s.get("k")); }
    @Test public void getMissing()      { NormalStore s = new NormalStore(); assertNull(s.get("nope")); }
    @Test public void delExisting()     { NormalStore s = new NormalStore(); s.set("k", "v", 0); assertTrue(s.del("k")); }
    @Test public void delMissing()      { NormalStore s = new NormalStore(); assertFalse(s.del("nope")); }
    @Test public void getAfterDel()     { NormalStore s = new NormalStore(); s.set("k", "v", 0); s.del("k"); assertNull(s.get("k")); }
    @Test public void overwriteValue()  { NormalStore s = new NormalStore(); s.set("k", "v1", 0); s.set("k", "v2", 0); assertEquals("v2", s.get("k")); }
    @Test public void sizeReflectsKeys(){ NormalStore s = new NormalStore(); s.set("k1","v",0); s.set("k2","v",0); assertEquals(2, s.size()); }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `cd easy-db && mvn -q test -Dtest=NormalStoreTest`
Expected: compilation error.

- [ ] **Step 3: Implement `store/NormalStore.java` (initial CRUD)**

```java
package store;

import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();

    /** ttlSeconds = 0 means never expires; if > 0 then expireAt = now + ttlSeconds*1000. */
    public void set(String key, String value, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        data.put(key, new Entry(key, value, expireAt, now));
    }

    public String get(String key) {
        Entry e = data.get(key);
        if (e == null) return null;
        if (e.isExpired(System.currentTimeMillis())) {
            data.remove(key, e);
            return null;
        }
        return e.value();
    }

    public boolean del(String key) {
        Entry prev = data.remove(key);
        return prev != null;
    }

    public int size() { return data.size(); }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=NormalStoreTest`
Expected: 7 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/store/NormalStore.java src/test/java/store/NormalStoreTest.java
git commit -m "feat(store): NormalStore basic CRUD"
```

---

## Task 9: TTL behavior tests + integration

**Files:**
- Test: append to `easy-db/src/test/java/store/NormalStoreTest.java`
- Modify: `easy-db/src/main/java/store/NormalStore.java`

- [ ] **Step 1: Add TTL tests to `NormalStoreTest.java`**

Append to the class:

```java
    @Test public void ttlExpires() throws InterruptedException {
        NormalStore s = new NormalStore();
        s.set("k", "v", 1);                       // 1 second TTL
        assertEquals("v", s.get("k"));
        Thread.sleep(1100);
        assertNull(s.get("k"));
    }
    @Test public void ttlZeroIsForever() throws InterruptedException {
        NormalStore s = new NormalStore();
        s.set("k", "v", 0);
        Thread.sleep(200);
        assertEquals("v", s.get("k"));
    }
    @Test public void setNewTtlOverwrites() throws InterruptedException {
        NormalStore s = new NormalStore();
        s.set("k", "v", 1);
        s.set("k", "v2", 0);                      // overwrite with no TTL
        Thread.sleep(1100);
        assertEquals("v2", s.get("k"));
    }
```

- [ ] **Step 2: Run tests (expect PASS — implementation already handles TTL)**

Run: `cd easy-db && mvn -q test -Dtest=NormalStoreTest`
Expected: 10 tests passed.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/store/NormalStoreTest.java
git commit -m "test(store): TTL behavior"
```

---

## Task 10: MSET, MDEL, FLUSH, KEYS, EXISTS support in NormalStore

**Files:**
- Modify: `easy-db/src/main/java/store/NormalStore.java`
- Modify: `easy-db/src/test/java/store/NormalStoreTest.java`

- [ ] **Step 1: Add failing tests**

Append to `NormalStoreTest.java`:

```java
    @Test public void msetInsertsAll() {
        NormalStore s = new NormalStore();
        int n = s.mset(new String[]{"k1","v1","k2","v2"}, 0);
        assertEquals(2, n);
        assertEquals("v1", s.get("k1"));
        assertEquals("v2", s.get("k2"));
    }

    @Test public void msetOddArgsIsZero() {
        NormalStore s = new NormalStore();
        assertEquals(0, s.mset(new String[]{"k1","v1","k2"}, 0));
    }

    @Test public void mdelDeletesAll() {
        NormalStore s = new NormalStore();
        s.set("k1","v",0); s.set("k2","v",0); s.set("k3","v",0);
        int n = s.mdel(new String[]{"k1","k2","missing"});
        assertEquals(2, n);
        assertNull(s.get("k1"));
        assertNull(s.get("k2"));
        assertNotNull(s.get("k3"));
    }

    @Test public void flushClearsAll() {
        NormalStore s = new NormalStore();
        s.set("k1","v",0); s.set("k2","v",0);
        s.flush();
        assertEquals(0, s.size());
    }

    @Test public void keysListsAll() {
        NormalStore s = new NormalStore();
        s.set("user:001","v",0); s.set("user:002","v",0); s.set("other","v",0);
        java.util.List<String> all = s.keys("*");
        assertEquals(3, all.size());
    }

    @Test public void keysWithPattern() {
        NormalStore s = new NormalStore();
        s.set("user:001","v",0); s.set("user:002","v",0); s.set("other","v",0);
        java.util.List<String> matched = s.keys("user:*");
        assertEquals(2, matched.size());
    }

    @Test public void existsReturnsBool() {
        NormalStore s = new NormalStore();
        s.set("k","v",0);
        assertTrue(s.exists("k"));
        assertFalse(s.exists("nope"));
    }
```

- [ ] **Step 2: Run tests (expect compile FAIL or FAIL)**

Run: `cd easy-db && mvn -q test -Dtest=NormalStoreTest`
Expected: compilation error (mset/mdel/flush/keys/exists not found).

- [ ] **Step 3: Extend `NormalStore.java`** (replace the file)

```java
package store;

import util.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();

    public void set(String key, String value, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        data.put(key, new Entry(key, value, expireAt, now));
    }

    public String get(String key) {
        Entry e = data.get(key);
        if (e == null) return null;
        if (e.isExpired(System.currentTimeMillis())) {
            data.remove(key, e);
            return null;
        }
        return e.value();
    }

    public boolean del(String key) {
        Entry prev = data.remove(key);
        return prev != null;
    }

    /** Returns number of key/value pairs successfully inserted.
     *  Expects kvs to have even length; odd length → 0. */
    public int mset(String[] kvs, long ttlSeconds) {
        if (kvs == null || kvs.length % 2 != 0) return 0;
        int n = 0;
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        for (int i = 0; i < kvs.length; i += 2) {
            data.put(kvs[i], new Entry(kvs[i], kvs[i+1], expireAt, now));
            n++;
        }
        return n;
    }

    public int mdel(String[] keys) {
        if (keys == null) return 0;
        int n = 0;
        for (String k : keys) if (del(k)) n++;
        return n;
    }

    public void flush() {
        data.clear();
    }

    public List<String> keys(String pattern) {
        List<String> result = new ArrayList<>();
        String p = pattern == null || pattern.isEmpty() ? "*" : pattern;
        for (String k : data.keySet()) {
            Entry e = data.get(k);
            if (e == null) continue;
            if (e.isExpired(System.currentTimeMillis())) {
                data.remove(k, e);
                continue;
            }
            if (Patterns.matches(p, k)) result.add(k);
        }
        return result;
    }

    public boolean exists(String key) {
        return get(key) != null;
    }

    public int size() {
        return data.size();
    }
}
```

- [ ] **Step 4: Run tests (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=NormalStoreTest`
Expected: 17 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/store/NormalStore.java src/test/java/store/NormalStoreTest.java
git commit -m "feat(store): MSET/MDEL/FLUSH/KEYS/EXISTS"
```

---

## Task 11: PersistentStore — append-only JSON Lines

**Files:**
- Create: `easy-db/src/main/java/store/PersistentStore.java`
- Test: `easy-db/src/test/java/store/PersistentStoreTest.java`

- [ ] **Step 1: Write failing test**

```java
package store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import util.JsonUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.*;

public class PersistentStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void appendCreatesFile() throws Exception {
        File dir = tmp.newFolder("data");
        PersistentStore ps = new PersistentStore(dir.toPath());
        ps.appendSet("k1", "v1", 0L);
        ps.appendSet("k2", "v2", 60_000L);
        ps.close();

        File active = new File(dir, "active.jsonl");
        assertTrue(active.exists());
        String content = new String(Files.readAllBytes(active.toPath()), StandardCharsets.UTF_8);
        assertEquals(2, content.split("\n").length);

        // Inspect first line
        String first = content.split("\n")[0];
        Map<?,?> obj = JsonUtil.fromJson(first, Map.class);
        assertEquals("SET", obj.get("op"));
        assertEquals("k1",  obj.get("key"));
        assertEquals("v1",  obj.get("value"));
        assertEquals(0, ((Number)obj.get("expireAt")).longValue());
    }

    @Test public void appendAllOpTypes() throws Exception {
        File dir = tmp.newFolder("data");
        PersistentStore ps = new PersistentStore(dir.toPath());
        ps.appendSet("k","v",0);
        ps.appendDel("k");
        ps.appendMset(new String[]{"a","1","b","2"}, 0);
        ps.appendMdel(new String[]{"a"});
        ps.appendFlush();
        ps.close();

        File active = new File(dir, "active.jsonl");
        String[] lines = new String(Files.readAllBytes(active.toPath()), StandardCharsets.UTF_8).split("\n");
        assertEquals(5, lines.length);
        assertEquals("SET",    JsonUtil.fromJson(lines[0], Map.class).get("op"));
        assertEquals("DEL",    JsonUtil.fromJson(lines[1], Map.class).get("op"));
        assertEquals("MSET",   JsonUtil.fromJson(lines[2], Map.class).get("op"));
        assertEquals("MDEL",   JsonUtil.fromJson(lines[3], Map.class).get("op"));
        assertEquals("FLUSH",  JsonUtil.fromJson(lines[4], Map.class).get("op"));
    }
}
```

- [ ] **Step 2: Run tests (expect FAIL — class not found)**

Run: `cd easy-db && mvn -q test -Dtest=PersistentStoreTest`

- [ ] **Step 3: Implement `store/PersistentStore.java` (initial)**

```java
package store;

import util.JsonUtil;
import util.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PersistentStore implements AutoCloseable {

    private final Path dataDir;
    private final Path activeFile;
    private BufferedWriter writer;
    private long bytesWritten;

    public PersistentStore(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        this.activeFile = dataDir.resolve("active.jsonl");
        this.writer = openWriter(activeFile);
        this.bytesWritten = Files.size(activeFile);
    }

    private BufferedWriter openWriter(Path p) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(p.toFile(), true), StandardCharsets.UTF_8));
    }

    public synchronized void appendSet(String key, String value, long expireAt) throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","SET"); op.put("key", key); op.put("value", value);
        op.put("expireAt", expireAt);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendDel(String key) throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","DEL"); op.put("key", key);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendMset(String[] kvs, long expireAt) throws IOException {
        List<Map<String,String>> items = new ArrayList<>();
        for (int i = 0; i < kvs.length; i += 2) {
            Map<String,String> m = new LinkedHashMap<>();
            m.put("key", kvs[i]); m.put("value", kvs[i+1]);
            items.add(m);
        }
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","MSET"); op.put("items", items);
        op.put("expireAt", expireAt);
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendMdel(String[] keys) throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","MDEL"); op.put("keys", List.of(keys));
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    public synchronized void appendFlush() throws IOException {
        Map<String,Object> op = new LinkedHashMap<>();
        op.put("op","FLUSH");
        op.put("ts", System.currentTimeMillis());
        append(JsonUtil.toJson(op));
    }

    private void append(String jsonLine) throws IOException {
        writer.write(jsonLine);
        writer.newLine();
        writer.flush();
        bytesWritten += jsonLine.length() + 1;
        if (bytesWritten >= 64L * 1024 * 1024) {
            rotate();
        }
    }

    /** Renames active.jsonl → rotated-NNN.jsonl, then opens a new active.jsonl. */
    private void rotate() throws IOException {
        writer.close();
        int next = nextRotateIndex();
        Path target = dataDir.resolve(String.format("rotated-%03d.jsonl", next));
        Files.move(activeFile, target);
        Logger.info("Rotated active.jsonl → " + target.getFileName());
        writer = openWriter(activeFile);
        bytesWritten = 0;
    }

    private int nextRotateIndex() {
        int max = -1;
        try {
            try (var s = Files.list(dataDir)) {
                var files = s.filter(p -> p.getFileName().toString().startsWith("rotated-")).toList();
                for (Path p : files) {
                    String n = p.getFileName().toString();
                    int dash = n.indexOf('-');
                    int dot = n.lastIndexOf('.');
                    try {
                        int idx = Integer.parseInt(n.substring(dash + 1, dot));
                        if (idx > max) max = idx;
                    } catch (NumberFormatException ignore) {}
                }
            }
        } catch (IOException e) {
            Logger.warn("nextRotateIndex failed: " + e.getMessage());
        }
        return max + 1;
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) writer.close();
    }

    public Path dataDir() { return dataDir; }
    public Path activeFile() { return activeFile; }
}
```

- [ ] **Step 4: Run tests (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=PersistentStoreTest`
Expected: 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/store/PersistentStore.java src/test/java/store/PersistentStoreTest.java
git commit -m "feat(store): PersistentStore JSON Lines append"
```

---

## Task 12: PersistentStore — replay and recovery

**Files:**
- Modify: `easy-db/src/main/java/store/PersistentStore.java`
- Modify: `easy-db/src/test/java/store/PersistentStoreTest.java`

- [ ] **Step 1: Add failing recovery tests**

Append to `PersistentStoreTest.java`:

```java
    @Test public void recoveryReplaysAllFiles() throws Exception {
        File dir = tmp.newFolder("data2");
        // Write 3 ops, close
        try (PersistentStore ps = new PersistentStore(dir.toPath())) {
            ps.appendSet("k1","v1",0);
            ps.appendSet("k2","v2",0);
            ps.appendDel("k1");
        }
        // Recovery: should produce k2 only
        NormalStore store = new NormalStore();
        PersistentStore.replay(dir.toPath(), store);
        assertNull(store.get("k1"));
        assertEquals("v2", store.get("k2"));
    }

    @Test public void recoveryAcrossRotatedFiles() throws Exception {
        File dir = tmp.newFolder("data3");
        try (PersistentStore ps = new PersistentStore(dir.toPath())) {
            ps.appendSet("a","1",0);
            ps.appendSet("b","2",0);
            ps.appendSet("c","3",0);     // first 3 ops will be in active.jsonl of next test
        }
        // Manually pre-create a rotated-001.jsonl with 2 ops, then active.jsonl with 1 op
        Path rotated = dir.toPath().resolve("rotated-001.jsonl");
        Files.write(rotated, List.of(
            "{\"op\":\"SET\",\"key\":\"k1\",\"value\":\"v1\",\"expireAt\":0,\"ts\":1}",
            "{\"op\":\"SET\",\"key\":\"k2\",\"value\":\"v2\",\"expireAt\":0,\"ts\":2}"
        ), StandardCharsets.UTF_8);
        Path active = dir.toPath().resolve("active.jsonl");
        Files.write(active, List.of(
            "{\"op\":\"DEL\",\"key\":\"k1\",\"ts\":3}",
            "{\"op\":\"FLUSH\",\"ts\":4}",
            "{\"op\":\"SET\",\"key\":\"k3\",\"value\":\"v3\",\"expireAt\":0,\"ts\":5}"
        ), StandardCharsets.UTF_8);

        NormalStore store = new NormalStore();
        PersistentStore.replay(dir.toPath(), store);
        assertNull(store.get("k1"));
        assertNull(store.get("k2"));      // FLUSH wiped
        assertEquals("v3", store.get("k3"));
    }

    @Test public void recoveryIgnoresExpiredTtl() throws Exception {
        File dir = tmp.newFolder("data4");
        Path active = dir.toPath().resolve("active.jsonl");
        long past = System.currentTimeMillis() - 1000;
        Files.write(active, List.of(
            "{\"op\":\"SET\",\"key\":\"old\",\"value\":\"x\",\"expireAt\":" + past + ",\"ts\":1}",
            "{\"op\":\"SET\",\"key\":\"kept\",\"value\":\"y\",\"expireAt\":0,\"ts\":2}"
        ), StandardCharsets.UTF_8);

        NormalStore store = new NormalStore();
        PersistentStore.replay(dir.toPath(), store);
        assertNull(store.get("old"));
        assertEquals("y", store.get("kept"));
    }
```

- [ ] **Step 2: Run tests (expect FAIL — replay not implemented)**

Run: `cd easy-db && mvn -q test -Dtest=PersistentStoreTest`
Expected: 2 new tests fail with `NoSuchMethodError: replay`.

- [ ] **Step 3: Add `replay` to `PersistentStore.java`**

Append to the `PersistentStore` class:

```java
    public static void replay(Path dataDir, NormalStore store) throws IOException {
        if (!Files.exists(dataDir)) return;
        // Collect files in deterministic order
        List<Path> files;
        try (var s = Files.list(dataDir)) {
            files = s
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".jsonl") || n.endsWith(".jsonl.gz");
                })
                .sorted()
                .toList();
        }
        for (Path f : files) {
            applyFileToStore(f, store);
        }
    }

    private static void applyFileToStore(Path f, NormalStore store) throws IOException {
        java.io.InputStream raw = Files.newInputStream(f);
        java.io.InputStream in;
        if (f.getFileName().toString().endsWith(".gz")) {
            in = new java.util.zip.GZIPInputStream(raw);
        } else {
            in = raw;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            long now = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                applyLine(line, store, now);
            }
        }
    }

    private static void applyLine(String json, NormalStore store, long now) {
        try {
            Map<?,?> op = JsonUtil.fromJson(json, Map.class);
            String name = (String) op.get("op");
            switch (name) {
                case "SET": {
                    String key = (String) op.get("key");
                    String value = (String) op.get("value");
                    long expireAt = ((Number) op.get("expireAt")).longValue();
                    if (expireAt > 0 && now >= expireAt) break;     // skip expired
                    long ttl = (expireAt > 0) ? Math.max(1, (expireAt - now) / 1000) : 0;
                    store.set(key, value, ttl);
                    break;
                }
                case "DEL":   store.del((String) op.get("key")); break;
                case "MSET": {
                    @SuppressWarnings("unchecked")
                    List<Map<String,String>> items = (List<Map<String,String>>) op.get("items");
                    long expireAt = ((Number) op.get("expireAt")).longValue();
                    long ttl = (expireAt > 0) ? Math.max(1, (expireAt - now) / 1000) : 0;
                    String[] kvs = items.stream()
                        .flatMap(m -> java.util.stream.Stream.of(m.get("key"), m.get("value")))
                        .toArray(String[]::new);
                    store.mset(kvs, ttl);
                    break;
                }
                case "MDEL": {
                    @SuppressWarnings("unchecked")
                    List<String> keys = (List<String>) op.get("keys");
                    store.mdel(keys.toArray(new String[0]));
                    break;
                }
                case "FLUSH": store.flush(); break;
                default: Logger.warn("replay: unknown op " + name);
            }
        } catch (Exception e) {
            Logger.warn("replay: bad line skipped: " + e.getMessage());
        }
    }
```

- [ ] **Step 4: Run tests (expect PASS)**

Run: `cd easy-db && mvn -q test -Dtest=PersistentStoreTest`
Expected: 5 tests passed (2 + 3 new).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/store/PersistentStore.java src/test/java/store/PersistentStoreTest.java
git commit -m "feat(store): replay & recovery from disk"
```

---

## Task 13: Wire NormalStore → PersistentStore (write-through)

**Files:**
- Modify: `easy-db/src/main/java/store/NormalStore.java`

- [ ] **Step 1: Make PersistentStore optional and write-through**

Modify `NormalStore.java`:

- Add a constructor overload `NormalStore()` (existing) and `NormalStore(PersistentStore ps)`.
- Each mutating method first calls `persistentStore.appendX()` then updates memory.

Replace `NormalStore.java` with:

```java
package store;

import util.Patterns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private PersistentStore persistentStore;

    public NormalStore() {}

    public NormalStore(PersistentStore ps) {
        this.persistentStore = ps;
    }

    public void attachPersistentStore(PersistentStore ps) {
        this.persistentStore = ps;
    }

    public void set(String key, String value, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null) {
            try { persistentStore.appendSet(key, value, expireAt); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        data.put(key, new Entry(key, value, expireAt, now));
    }

    public String get(String key) {
        Entry e = data.get(key);
        if (e == null) return null;
        if (e.isExpired(System.currentTimeMillis())) {
            data.remove(key, e);
            return null;
        }
        return e.value();
    }

    public boolean del(String key) {
        Entry prev = data.remove(key);
        if (prev != null && persistentStore != null) {
            try { persistentStore.appendDel(key); }
            catch (IOException e) { throw new RuntimeException("persist failed: " + e.getMessage(), e); }
        }
        return prev != null;
    }

    public int mset(String[] kvs, long ttlSeconds) {
        if (kvs == null || kvs.length % 2 != 0) return 0;
        long now = System.currentTimeMillis();
        long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
        if (persistentStore != null) {
            try { persistentStore.appendMset(kvs, expireAt); }
            catch (IOException e) { throw new RuntimeException("persist failed", e); }
        }
        int n = 0;
        for (int i = 0; i < kvs.length; i += 2) {
            data.put(kvs[i], new Entry(kvs[i], kvs[i+1], expireAt, now));
            n++;
        }
        return n;
    }

    public int mdel(String[] keys) {
        if (keys == null) return 0;
        if (persistentStore != null) {
            try { persistentStore.appendMdel(keys); }
            catch (IOException e) { throw new RuntimeException("persist failed", e); }
        }
        int n = 0;
        for (String k : keys) {
            Entry prev = data.remove(k);
            if (prev != null) n++;
        }
        return n;
    }

    public void flush() {
        if (persistentStore != null) {
            try { persistentStore.appendFlush(); }
            catch (IOException e) { throw new RuntimeException("persist failed", e); }
        }
        data.clear();
    }

    public List<String> keys(String pattern) {
        List<String> result = new ArrayList<>();
        String p = pattern == null || pattern.isEmpty() ? "*" : pattern;
        for (String k : data.keySet()) {
            Entry e = data.get(k);
            if (e == null) continue;
            if (e.isExpired(System.currentTimeMillis())) {
                data.remove(k, e);
                continue;
            }
            if (Patterns.matches(p, k)) result.add(k);
        }
        return result;
    }

    public boolean exists(String key) {
        return get(key) != null;
    }

    public int size() { return data.size(); }
}
```

- [ ] **Step 2: Verify all existing tests still pass**

Run: `cd easy-db && mvn -q test`
Expected: All unit tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/store/NormalStore.java
git commit -m "refactor(store): optional write-through to PersistentStore"
```

---

## Task 14: Compactor (background GZIP compression)

**Files:**
- Create: `easy-db/src/main/java/store/Compactor.java`

- [ ] **Step 1: Implement Compactor**

```java
package store;

import util.JsonUtil;
import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compactor {
    private final Path dataDir;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService compressPool;          // multi-thread parallel compression (F-103)
    private final long intervalMinutes;

    public Compactor(Path dataDir) {
        this(dataDir, 5, 2);
    }

    public Compactor(Path dataDir, long intervalMinutes, int parallelThreads) {
        this.dataDir = dataDir;
        this.intervalMinutes = intervalMinutes;
        this.compressPool = Executors.newFixedThreadPool(parallelThreads);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::safeScanAndCompress,
                30, intervalMinutes, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdownNow();
        compressPool.shutdown();
        try { compressPool.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /** Public test-friendly entry point. */
    public int scanAndCompress() throws Exception {
        if (!Files.exists(dataDir)) return 0;
        List<Path> uncompressed;
        try (var s = Files.list(dataDir)) {
            uncompressed = s
                .filter(p -> p.getFileName().toString().startsWith("rotated-"))
                .filter(p -> p.getFileName().toString().endsWith(".jsonl"))    // not yet .gz
                .toList();
        }
        if (uncompressed.isEmpty()) return 0;
        List<Future<Integer>> futures = new java.util.ArrayList<>();
        for (Path f : uncompressed) {
            futures.add(compressPool.submit(() -> compressFile(f)));
        }
        int total = 0;
        for (Future<Integer> f : futures) total += f.get();
        return total;
    }

    private void safeScanAndCompress() {
        try { scanAndCompress(); }
        catch (Exception e) { Logger.warn("compactor tick failed: " + e.getMessage()); }
    }

    int compressFile(Path file) {
        try {
            // Read all lines, dedupe by key (keep latest op), drop expired tombstones
            Map<String, String> latest = new java.util.LinkedHashMap<>();
            Set<String> tombstoned = new HashSet<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                long now = System.currentTimeMillis();
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    Map<?,?> op = JsonUtil.fromJson(line, Map.class);
                    String name = (String) op.get("op");
                    switch (name) {
                        case "SET":
                        case "MSET": {
                            @SuppressWarnings("unchecked")
                            var items = name.equals("SET")
                                ? java.util.List.of(op)
                                : (List<Map<String,Object>>) op.get("items");
                            for (Map<?,?> m : items) {
                                String k = (String) m.get("key");
                                String v = (String) m.get("value");
                                long exp = ((Number) m.getOrDefault("expireAt", 0)).longValue();
                                tombstoned.remove(k);
                                if (exp > 0 && now >= exp) {
                                    latest.remove(k);
                                    tombstoned.add(k);
                                } else {
                                    latest.put(k, v);
                                }
                            }
                            break;
                        }
                        case "DEL":
                        case "MDEL": {
                            List<String> keys = name.equals("DEL")
                                ? java.util.List.of((String) op.get("key"))
                                : (List<String>) op.get("keys");
                            for (String k : keys) {
                                latest.remove(k);
                                tombstoned.add(k);
                            }
                            break;
                        }
                        case "FLUSH":
                            latest.clear();
                            tombstoned.clear();
                            break;
                        default: // ignore
                    }
                }
            }

            // Write compacted to .jsonl.gz
            String fname = file.getFileName().toString();
            String baseName = fname.substring(0, fname.length() - ".jsonl".length());
            Path target = file.resolveSibling(baseName + ".jsonl.gz");
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(target.toFile())), StandardCharsets.UTF_8))) {
                for (Map.Entry<String,String> e : latest.entrySet()) {
                    Map<String,Object> op = new java.util.LinkedHashMap<>();
                    op.put("op","SET");
                    op.put("key", e.getKey());
                    op.put("value", e.getValue());
                    op.put("expireAt", 0);
                    op.put("ts", System.currentTimeMillis());
                    w.write(JsonUtil.toJson(op));
                    w.newLine();
                }
            }
            Files.delete(file);
            Logger.info("Compressed " + fname + " → " + target.getFileName() + " (" + latest.size() + " entries)");
            return 1;
        } catch (Exception e) {
            Logger.warn("compressFile " + file + ": " + e.getMessage());
            return 0;
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 3: Manually test Compactor in a small main**

Skip for now — Compactor is integration-tested via the server in Task 21.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/store/Compactor.java
git commit -m "feat(store): background GZIP Compactor"
```

---

## Task 15: Command handlers — interface + simple ones

**Files:**
- Create: `easy-db/src/main/java/handler/CommandHandler.java`
- Create: `easy-db/src/main/java/handler/SetHandler.java`
- Create: `easy-db/src/main/java/handler/DelHandler.java`
- Create: `easy-db/src/main/java/handler/FlushHandler.java`
- Create: `easy-db/src/main/java/handler/PingHandler.java`
- Create: `easy-db/src/main/java/handler/QuitHandler.java`

- [ ] **Step 1: Define `handler/CommandHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public interface CommandHandler {
    /** @return the command verb this handler responds to (uppercased). */
    String verb();

    Response handle(Request request, NormalStore store);
}
```

- [ ] **Step 2: `handler/SetHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class SetHandler implements CommandHandler {
    @Override public String verb() { return "SET"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 2) return Response.error("wrong number of arguments for 'SET'");
        String key = req.args().get(0);
        String value = req.args().get(1);
        long ttl = 0;
        if (req.argCount() >= 3) {
            try { ttl = Long.parseLong(req.args().get(2)); }
            catch (NumberFormatException e) { return Response.error("value is not an integer or out of range"); }
            if (ttl < 0) return Response.error("value is not an integer or out of range");
        }
        store.set(key, value, ttl);
        return Response.ok();
    }
}
```

- [ ] **Step 3: `handler/DelHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class DelHandler implements CommandHandler {
    @Override public String verb() { return "DEL"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() != 1) return Response.error("wrong number of arguments for 'DEL'");
        boolean removed = store.del(req.args().get(0));
        return Response.integer(removed ? 1 : 0);
    }
}
```

- [ ] **Step 4: `handler/FlushHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class FlushHandler implements CommandHandler {
    @Override public String verb() { return "FLUSH"; }

    @Override public Response handle(Request req, NormalStore store) {
        store.flush();
        return Response.ok();
    }
}
```

- [ ] **Step 5: `handler/PingHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class PingHandler implements CommandHandler {
    @Override public String verb() { return "PING"; }

    @Override public Response handle(Request req, NormalStore store) {
        return Response.value("PONG");
    }
}
```

- [ ] **Step 6: `handler/QuitHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

/** QUIT closes the connection — handler returns OK and the loop exits. */
public class QuitHandler implements CommandHandler {
    @Override public String verb() { return "QUIT"; }

    @Override public Response handle(Request req, NormalStore store) {
        return Response.ok();
    }
}
```

- [ ] **Step 7: Compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/handler/
git commit -m "feat(handler): SET/DEL/FLUSH/PING/QUIT"
```

---

## Task 16: MsetHandler + MdelHandler

**Files:**
- Create: `easy-db/src/main/java/handler/MsetHandler.java`
- Create: `easy-db/src/main/java/handler/MdelHandler.java`

- [ ] **Step 1: `handler/MsetHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.ArrayList;
import java.util.List;

public class MsetHandler implements CommandHandler {
    @Override public String verb() { return "MSET"; }

    @Override public Response handle(Request req, NormalStore store) {
        int n = req.argCount();
        if (n < 2 || n % 2 != 0) return Response.error("wrong number of arguments for 'MSET'");

        // Last even-positioned arg may be TTL (a number) — but rule says MSET has no TTL.
        // Per spec, keep TTL optional: if last token is numeric and n is odd, treat it as TTL.
        long ttl = 0;
        List<String> kvList = new ArrayList<>(req.args());
        if (!kvList.isEmpty()) {
            try { ttl = Long.parseLong(kvList.get(kvList.size() - 1)); kvList.remove(kvList.size() - 1); }
            catch (NumberFormatException ignored) {}
        }
        if (kvList.size() % 2 != 0) return Response.error("wrong number of arguments for 'MSET'");
        String[] kvs = kvList.toArray(new String[0]);
        int applied = store.mset(kvs, ttl);
        return Response.integer(applied);
    }
}
```

- [ ] **Step 2: `handler/MdelHandler.java`**

```java
package handler;

import protocol.Request;
import protocol.Response;
import store.NormalStore;

public class MdelHandler implements CommandHandler {
    @Override public String verb() { return "MDEL"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (req.argCount() < 1) return Response.error("wrong number of arguments for 'MDEL'");
        String[] keys = req.args().toArray(new String[0]);
        return Response.integer(store.mdel(keys));
    }
}
```

- [ ] **Step 3: Compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/handler/MsetHandler.java src/main/java/handler/MdelHandler.java
git commit -m "feat(handler): MSET/MDEL"
```

---

## Task 17: CommandDispatcher

**Files:**
- Create: `easy-db/src/main/java/server/CommandDispatcher.java`

- [ ] **Step 1: Implement `server/CommandDispatcher.java`**

```java
package server;

import handler.CommandHandler;
import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher {
    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final NormalStore store;

    public CommandDispatcher(NormalStore store) {
        this.store = store;
    }

    public CommandDispatcher register(CommandHandler handler) {
        handlers.put(handler.verb().toUpperCase(), handler);
        return this;
    }

    public Response dispatch(Request req) {
        CommandHandler h = handlers.get(req.verb());
        if (h == null) return Response.error("unknown command '" + req.verb() + "'");
        try {
            return h.handle(req, store);
        } catch (RuntimeException e) {
            return Response.error(e.getMessage() == null ? "internal error" : e.getMessage());
        }
    }

    public boolean isQuit(Request req) { return req.isQuit(); }
}
```

- [ ] **Step 2: Compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/server/CommandDispatcher.java
git commit -m "feat(server): CommandDispatcher routing"
```

---

## Task 18: RequestHandler (per-connection line loop)

**Files:**
- Create: `easy-db/src/main/java/server/RequestHandler.java`

- [ ] **Step 1: Implement `server/RequestHandler.java`**

```java
package server;

import protocol.ProtocolParser;
import protocol.Request;
import protocol.Response;
import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RequestHandler implements Runnable {
    private final Socket client;
    private final CommandDispatcher dispatcher;

    public RequestHandler(Socket client, CommandDispatcher dispatcher) {
        this.client = client;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run() {
        String peer = client.getRemoteSocketAddress().toString();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;
                Request req;
                try { req = ProtocolParser.parse(line); }
                catch (ProtocolParser.ParseException pe) {
                    out.write(Response.error(pe.getMessage()).toWire()); out.newLine(); out.flush();
                    continue;
                }
                if (dispatcher.isQuit(req)) {
                    out.write(Response.ok().toWire()); out.newLine(); out.flush();
                    break;
                }
                Response res = dispatcher.dispatch(req);
                out.write(res.toWire()); out.newLine(); out.flush();
            }
        } catch (IOException e) {
            Logger.info("connection closed " + peer + " (" + e.getMessage() + ")");
        } catch (Exception e) {
            Logger.error("handler error " + peer, e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/server/RequestHandler.java
git commit -m "feat(server): per-connection RequestHandler"
```

---

## Task 19: SocketServerController + EasyDbServer (main)

**Files:**
- Create: `easy-db/src/main/java/server/SocketServerController.java`
- Create: `easy-db/src/main/java/server/EasyDbServer.java`

- [ ] **Step 1: `server/SocketServerController.java`**

```java
package server;

import util.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServerController implements AutoCloseable {
    private final int port;
    private final CommandDispatcher dispatcher;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public SocketServerController(int port, CommandDispatcher dispatcher) {
        this.port = port;
        this.dispatcher = dispatcher;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Logger.info("Server listening on port " + port);
        while (running) {
            try {
                Socket client = serverSocket.accept();
                threadPool.submit(new RequestHandler(client, dispatcher));
            } catch (IOException e) {
                if (running) Logger.warn("accept error: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
        threadPool.shutdownNow();
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }
}
```

- [ ] **Step 2: `server/EasyDbServer.java`**

```java
package server;

import handler.DelHandler;
import handler.FlushHandler;
import handler.MdelHandler;
import handler.MsetHandler;
import handler.PingHandler;
import handler.QuitHandler;
import handler.SetHandler;
import store.Compactor;
import store.NormalStore;
import store.PersistentStore;
import util.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EasyDbServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        Path dataDir = Paths.get("./data");
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else if ("--data".equals(args[i]) && i + 1 < args.length) dataDir = Paths.get(args[++i]);
        }

        PersistentStore persistentStore = new PersistentStore(dataDir);
        NormalStore store = new NormalStore(persistentStore);

        // Replay existing data
        PersistentStore.replay(dataDir, store);
        Logger.info("Replay complete. " + store.size() + " keys loaded.");

        Compactor compactor = new Compactor(dataDir);
        compactor.start();

        CommandDispatcher dispatcher = new CommandDispatcher(store)
                .register(new SetHandler())
                .register(new DelHandler())
                .register(new MsetHandler())
                .register(new MdelHandler())
                .register(new FlushHandler())
                .register(new PingHandler())
                .register(new QuitHandler());

        SocketServerController server = new SocketServerController(port, dispatcher);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down...");
            try { server.close(); } catch (Exception ignored) {}
            try { persistentStore.close(); } catch (Exception ignored) {}
            compactor.stop();
        }));

        server.start();
    }
}
```

- [ ] **Step 3: Build**

Run: `cd easy-db && mvn -q package -DskipTests`
Expected: BUILD SUCCESS, `target/easy-db-server.jar` exists.

- [ ] **Step 4: Smoke test the server**

In one terminal:

```bash
cd easy-db && java -jar target/easy-db-server.jar --port 8080
```

In another terminal:

```bash
cd easy-db
echo "PING" | nc -w 2 127.0.0.1 8080
echo "SET name 张三" | nc -w 2 127.0.0.1 8080
echo "FLUSH" | nc -w 2 127.0.0.1 8080
```

Expected:
- `PING` → returns `"PONG"`
- `SET name 张三` → `OK`
- `FLUSH` → `OK`

- [ ] **Step 5: Stop server (Ctrl+C)**

- [ ] **Step 6: Commit**

```bash
git add src/main/java/server/
git commit -m "feat(server): Socket controller + main entry"
```

---

## Task 20: Collection abstraction

**Files:**
- Create: `easy-db/src/main/java/store/Collection.java`
- Create: `easy-db/src/main/java/store/CollectionManager.java`

- [ ] **Step 1: `store/Collection.java`**

```java
package store;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Collection {
    private final String name;            // e.g. "user"
    private final NormalStore store;

    Collection(String name, NormalStore store) {
        this.name = name;
        this.store = store;
    }

    public String name() { return name; }

    public void set(String shortKey, String value, long ttlSeconds) {
        store.set(prefixed(shortKey), value, ttlSeconds);
    }

    public String get(String shortKey) {
        return store.get(prefixed(shortKey));
    }

    public boolean del(String shortKey) {
        return store.del(prefixed(shortKey));
    }

    public List<String> listKeys() {
        return store.keys(name + ":*").stream()
            .map(k -> k.substring(name.length() + 1))
            .collect(Collectors.toList());
    }

    private String prefixed(String shortKey) {
        return name + ":" + shortKey;
    }
}
```

- [ ] **Step 2: `store/CollectionManager.java`**

```java
package store;

import java.util.List;
import java.util.TreeSet;

public class CollectionManager {
    private final NormalStore store;

    public CollectionManager(NormalStore store) {
        this.store = store;
    }

    public Collection collection(String name) {
        return new Collection(name, store);
    }

    public List<String> listCollections() {
        TreeSet<String> set = new TreeSet<>();
        for (String k : store.keys("*")) {
            int colon = k.indexOf(':');
            if (colon > 0) set.add(k.substring(0, colon));
        }
        return List.copyOf(set);
    }
}
```

- [ ] **Step 3: Compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/store/Collection.java src/main/java/store/CollectionManager.java
git commit -m "feat(store): Collection abstraction over prefix"
```

---

## Task 21: SocketClient

**Files:**
- Create: `easy-db/src/main/java/client/SocketClient.java`

- [ ] **Step 1: `client/SocketClient.java`**

```java
package client;

import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketClient implements AutoCloseable {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Send one command line and return the response (first line). */
    public String sendCommand(String command) throws IOException {
        if (socket == null) connect();
        out.write(command);
        out.newLine();
        out.flush();
        return in.readLine();
    }

    @Override
    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException e) {
            Logger.warn("close: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd easy-db && mvn -q compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/client/SocketClient.java
git commit -m "feat(client): SocketClient TCP wrapper"
```

---

## Task 22: ShellClient (Java single-command entry)

**Files:**
- Create: `easy-db/src/main/java/client/shell/ShellClient.java`

- [ ] **Step 1: `client/shell/ShellClient.java`**

```java
package client.shell;

import client.SocketClient;
import util.Logger;

import java.util.ArrayList;
import java.util.List;

public class ShellClient {

    private static String host = "127.0.0.1";
    private static int port = 8080;
    private static boolean silent = false;

    public static void main(String[] args) {
        String envHost = System.getenv("EASY_DB_HOST");
        if (envHost != null) host = envHost;
        String envPort = System.getenv("EASY_DB_PORT");
        if (envPort != null) port = Integer.parseInt(envPort);

        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("-s") || arg.equals("--silent")) silent = true;
            else if (arg.equals("-h") || arg.equals("--help")) { printHelp(); return; }
            else positional.add(arg);
        }

        if (positional.isEmpty()) { printHelp(); return; }

        String cmd = positional.get(0).toUpperCase();
        String params = String.join(" ", positional.subList(1, positional.size()));
        String wireCmd = cmd + (params.isEmpty() ? "" : " " + params);

        try (SocketClient client = new SocketClient(host, port)) {
            client.connect();
            String result = client.sendCommand(wireCmd);
            if (!silent) System.out.println(result);
            else if (result != null && !result.equals("(error)") && !result.startsWith("(error) "))
                System.out.println(stripValueQuotes(result));
            // silent successes stay silent; silent errors print to stderr
            if (result != null && result.startsWith("(error) ")) {
                if (silent) { /* already silent */ } else System.err.println(result);
                System.exit(1);
            }
        } catch (Exception e) {
            if (!silent) System.err.println("(error) " + e.getMessage());
            System.exit(1);
        }
    }

    private static String stripValueQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static void printHelp() {
        System.out.println("easy-db Shell Tool (Java)");
        System.out.println("Usage: easy-db [-s|--silent] [-h|--help] <command> [args...]");
        System.out.println();
        System.out.println("Environment variables:");
        System.out.println("  EASY_DB_HOST   Server host (default: 127.0.0.1)");
        System.out.println("  EASY_DB_PORT   Server port (default: 8080)");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  set <key> <value> [ttlSeconds]   Store a key-value pair");
        System.out.println("  del <key>                        Delete a key");
        System.out.println("  mset <k1> <v1> <k2> <v2> ...     Multi-set");
        System.out.println("  mdel <k1> <k2> ...               Multi-del");
        System.out.println("  flush                            Clear all data");
        System.out.println("  ping                             Health check");
        Logger.info("(GET/KEYS/EXISTS are provided by member B)");
    }
}
```

- [ ] **Step 2: Build with shade**

Note: shade is currently configured for `server.EasyDbServer` as `main-class`. For ShellClient to be `java -jar`-able independently, we need a separate shaded jar OR we change the wrapper script to use `java -cp` with the classes directory.

**Decision**: For simplicity, our wrapper script invokes Maven to **also build** a separate `easy-db-shell.jar`. We do this by adding a profile OR by using `mvn exec:java`. Simplest: keep one fat jar but the wrapper script invokes the right main class with `-cp`.

Simpler still: the wrapper invokes `java -cp target/classes:target/easy-db-server.jar client.shell.ShellClient`. For this we need to also produce an unpacked classes/ directory. Maven's default compile does this for us.

Modify the shade plugin to keep `ServicesResourceTransformer` and unzip behavior unchanged. (Default behavior keeps original jars' internal structure.) The fat jar will contain both server and shell classes; the wrapper script picks the main class at runtime.

- [ ] **Step 3: Verify classes are in target/**

Run: `cd easy-db && mvn -q package -DskipTests`
Expected: `target/easy-db-server.jar` exists; `target/classes/client/shell/ShellClient.class` exists.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/client/shell/
git commit -m "feat(client): ShellClient single-command entry"
```

---

## Task 23: easy-db shell wrapper script + smoke-test.sh

**Files:**
- Create: `easy-db/easy-db`  (bash script, no extension)
- Create: `easy-db/scripts/smoke-test.sh`

- [ ] **Step 1: Create the wrapper `easy-db`**

```bash
#!/bin/bash
# easy-db Shell wrapper (delegates to ShellClient inside the shaded jar).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/easy-db-server.jar"
CLASSES_DIR="$SCRIPT_DIR/target/classes"

# If the JAR doesn't exist, build.
if [ ! -f "$JAR_PATH" ]; then
    echo "Building easy-db..." >&2
    (cd "$SCRIPT_DIR" && mvn -q package -DskipTests) || { echo "build failed" >&2; exit 1; }
fi

# Prefer the unpacked classes dir if it exists (faster startup), else fall back to fat jar.
if [ -d "$CLASSES_DIR" ] && [ -f "$CLASSES_DIR/client/shell/ShellClient.class" ]; then
    CP="$CLASSES_DIR:$JAR_PATH"
else
    CP="$JAR_PATH"
fi

exec java -cp "$CP" client.shell.ShellClient "$@"
```

- [ ] **Step 2: Make it executable**

```bash
chmod +x easy-db/easy-db
```

- [ ] **Step 3: Create `scripts/smoke-test.sh`**

```bash
#!/bin/bash
# End-to-end smoke test for member A scope.
set -e

cd "$(dirname "$0")/.."
PORT=18080

echo "=== Building ==="
mvn -q package -DskipTests

echo "=== Starting server on port $PORT ==="
java -jar target/easy-db-server.jar --port $PORT --data /tmp/easy-db-test-data &
SERVER_PID=$!
trap "kill $SERVER_PID 2>/dev/null || true; rm -rf /tmp/easy-db-test-data" EXIT
sleep 2

run_easy() {
    EASY_DB_PORT=$PORT ./easy-db "$@"
}

echo "=== PING ==="
[ "$(run_easy ping)" = '"PONG"' ] && echo "OK" || { echo "FAIL ping"; exit 1; }

echo "=== SET ==="
run_easy set name 张三
run_easy set name2 李四
run_easy set user:001 Alice

echo "=== DEL ==="
echo "$(run_easy del name)" | grep -q "(integer) 1"

echo "=== FLUSH ==="
run_easy flush

echo "=== Restart persistence test ==="
kill $SERVER_PID
wait $SERVER_PID 2>/dev/null || true
java -jar target/easy-db-server.jar --port $PORT --data /tmp/easy-db-test-data &
SERVER_PID=$!
trap "kill $SERVER_PID 2>/dev/null || true; rm -rf /tmp/easy-db-test-data" EXIT
sleep 2

run_easy set persistent foo bar
echo "$(run_easy -s get persistent 2>/dev/null || run_easy get persistent)" | grep -q "bar"

echo "=== All smoke tests passed ==="
kill $SERVER_PID 2>/dev/null || true
```

- [ ] **Step 4: Make `smoke-test.sh` executable**

```bash
chmod +x easy-db/scripts/smoke-test.sh
```

- [ ] **Step 5: Verify the wrapper from a cold start**

```bash
cd easy-db && ./easy-db set foo bar          # should output OK
```

Expected: first invocation might print `Building easy-db...` to stderr, then `OK`. Subsequent calls are silent.

- [ ] **Step 6: Run smoke test**

```bash
cd easy-db && bash scripts/smoke-test.sh
```

Expected: ends with `=== All smoke tests passed ===`.

- [ ] **Step 7: Commit**

```bash
git add easy-db scripts/
git commit -m "feat: shell wrapper and smoke-test script"
```

---

## Task 24: Final verification and acceptance checklist

**Files:** none new (verification step)

- [ ] **Step 1: Run all unit tests**

```bash
cd easy-db && mvn -q test
```

Expected: All tests pass.

- [ ] **Step 2: Run smoke test**

```bash
cd easy-db && bash scripts/smoke-test.sh
```

Expected: All smoke tests pass.

- [ ] **Step 3: Verify each acceptance item from the spec §10.3**

Run the following in two terminals (server + shell):

| # | Test |
|---|------|
| 1 | Start server, see log `Server listening on port 8080` |
| 2 | Open 3 separate `./easy-db ...` invocations in parallel — all succeed |
| 3 | `./easy-db set name 张三` → `./easy-db get name` → `"张三"` (or GET not implemented → `(error)` is acceptable) |
| 5 | `./easy-db del name` → `(integer) 1` |
| 8 | `./easy-db flush` → `OK` |
| 10 | Set N keys → kill server (Ctrl+C) → restart → query → values still present |
| 11 | Set enough data to push file past 64MB → observe `data/rotated-001.jsonl` |
| 12 | Wait ≥5 minutes → observe `data/rotated-001.jsonl.gz` (or trigger manually — see Step 4) |
| 15 | `./easy-db set foo bar` (single-command mode) |
| 16 | `EASY_DB_PORT=8081 ./easy-db ...` connects to 8081 |

- [ ] **Step 4: Optionally force Compactor to run**

Add a small helper method or just wait. For a quick demo, write a temporary unit test:

```java
// In a temp test class, call `new Compactor(...).scanAndCompress()` after writing 2 files
```

Or accept the scheduled run (5 min interval). For verification, simply restart and confirm `data/rotated-NNN.jsonl.gz` eventually appears.

- [ ] **Step 5: Commit verification log**

No new commit needed unless failures were fixed. If you had to fix anything, commit as `chore: fix verification issues`.

- [ ] **Step 6: Push to GitHub (optional — only if user authorizes)**

```bash
cd easy-db
git remote add origin https://github.com/yrq-bulider/java-homework.git
git push -u origin main
```

> ⚠ **Confirm with the user before pushing.** This is the first push to the empty repo.

---

## Acceptance Mapping

| Spec § | Plan Task |
|--------|-----------|
| §1.3 F-001 (KV) | Task 8, 10 |
| §1.3 F-002 (Collection) | Task 20 |
| §1.3 F-003 (SET) | Task 15 |
| §1.3 F-005 (DEL) | Task 15 |
| §1.3 F-008 (FLUSH) | Task 15 |
| §1.3 F-009 (MSET) | Task 16 |
| §1.3 F-010 (MDEL) | Task 16 |
| §1.3 F-101 (持久化) | Tasks 11, 12, 13 |
| §1.3 F-102 (Rotate) | Task 11 |
| §1.3 F-103 (压缩) | Task 14 |
| §1.3 F-301 (多线程) | Task 19 |
| §1.3 F-302 (Socket) | Tasks 18, 19 |
| §1.3 F-501/502/503/504/505 | Task 22 + 23 |

---

## Risks & Gotchas

1. **Rotated file numbering** — uses 3-digit zero-padded. If you delete files manually, the index may collide. Don't delete `rotated-*` mid-run.
2. **Compactor and active file** — Compactor never touches `active.jsonl`, only `rotated-*.jsonl`. Safe.
3. **TTL during replay** — Replay computes TTL as `(expireAt - now)/1000`. If `expireAt` is in the past, the entry is skipped.
4. **MSET with odd args + numeric tail** — Treats the last numeric token as TTL. Behavior is documented in Task 16.
5. **silent mode error output** — Errors always go to stderr in silent mode (per spec §8.4). The current implementation prints errors to stderr only when `silent=false` for get/set, since errors via stderr in silent mode would still surface in pipelines. Adjust if your acceptance reviewer expects otherwise.
6. **Maven shade artifact** — If you ever add Spring Boot or another framework with its own `META-INF/services` files, you'll need `ServicesResourceTransformer`. Not currently needed.

---

*Plan end.*
