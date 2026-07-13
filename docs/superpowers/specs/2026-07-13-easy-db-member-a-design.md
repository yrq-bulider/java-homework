# easy-db 设计文档 — 成员A范围

- **日期**: 2026-07-13
- **作者**: Claude (协助成员A / 组长)
- **范围**: 仅成员A负责的模块(基础框架 + 增/删 + Shell 工具 + 持久化)
- **状态**: 已批准,待实施

---

## 1. 背景与目标

Java 课程设计作业,实现一个基于 C/S 架构的 NoSQL 键值数据库系统 **easy-db**。

### 1.1 课程目标

- 服务端支持多线程并发处理客户端连接
- 提供数据的增/删/改/查、持久化存储、索引加速、缓存优化
- 客户端提供 5 种交互方式(CLI、Shell、Java SDK、RESTful API、GUI)

### 1.2 本文档范围

仅覆盖 **成员A(组长)** 负责的内容。成员B(改/查、CLI/SDK/HTTP/GUI)的模块不在本文档范围,但为了让联调可演示,本项目会预留 `easy-db` API 给成员B,**不实现他们的具体功能**。

### 1.3 成员A的功能矩阵

| 需求 | 描述 | 本文覆盖? |
|------|------|----------|
| F-001 数据模型 KV | Key=String, Value=String(序列化为 JSON 或裸字符串) | ✅ |
| F-002 Collection 概念 | 通过键名前缀归类,提供 CollectionManager 视图 | ✅ |
| F-003 SET | `SET key value [ttlSeconds]` | ✅ |
| F-005 DEL | `DEL key` | ✅ |
| F-008 FLUSH | `FLUSH` | ✅ |
| F-009 批量插入 | `MSET k1 v1 k2 v2 ...` | ✅ |
| F-010 批量删除 | `MDEL k1 k2 ...` | ✅ |
| F-101 落盘写入 | 同步追加到 JSON Lines 文件 | ✅ |
| F-102 文件 Rotate | 达到 64MB 自动切换 | ✅ |
| F-103 多线程压缩 | 后台 ScheduledExecutorService 并行压缩归档 | ✅ |
| F-301 多线程 | `Executors.newCachedThreadPool()` | ✅ |
| F-302 Socket 通信 | TCP + 自定义文本协议 | ✅ |
| F-501 统一入口命令 | `easy-db <command> [args...]` | ✅ |
| F-502 环境变量 | `EASY_DB_HOST` / `EASY_DB_PORT` | ✅ |
| F-503 核心命令 | set/get/del/keys/exists/flush(其中 keys/exists/get 由 B 提供 Socket 命令实现,我们只是**不**实现命令处理器) | ⚠️ 部分 |
| F-504 静默模式 | `-s / --silent` | ✅ |
| F-505 管道组合 | 输出格式设计为管道友好 | ✅ |

> 注意:本文档实现了 server 端的命令执行管道(`CommandDispatcher`),但只**注册 A 的命令**。`GET`/`KEYS`/`EXISTS` 命令需要成员B的 handler 实现 — 联调时由 B 补充,或者 A 留 register 入口。

---

## 2. 总体架构

```
┌─────────────────┐                   ┌──────────────────────────────┐
│ Shell Client    │   easy-db shell   │  SocketServerController     │
│ (ShellClient)   │   ──── TCP ────►  │  (Java 线程池)              │
└─────────────────┘                   │                              │
                                      │  ▼ 每个连接                   │
┌─────────────────┐                   │  RequestHandler (runnable)  │
│ Other Clients   │  custom protocol  │  ▼ read line,parse          │
│ (CLI/SDK/GUI/HTTP)│                 │  CommandDispatcher          │
│ ──── B 成员做 ───│                  │  ▼ route to handler          │
└─────────────────┘                   │  Handler → Store API         │
                                      │  ▼                           │
                                      │  NormalStore                 │
                                      │  ▼ write-through             │
                                      │  PersistentStore             │
                                      │  + Compactor (后台线程)       │
                                      └──────────────────────────────┘
                                                │ data/
                                                ▼
                                      easy-db/data/
                                      ├─ active.jsonl         ← 当前写入
                                      ├─ rotated-001.jsonl    ← 待压缩
                                      └─ rotated-001.jsonl.gz ← 压缩归档
```

数据流(写路径):
1. 客户端发 `SET key value\n`
2. SocketServerController 的 accept 循环接收连接,丢给线程池
3. RequestHandler 读行,反序列化为 Request 对象
4. CommandDispatcher 查找 verb 对应的 handler
5. handler 调 NormalStore.set()
6. NormalStore 写内存 + 通过 PersistentStore 同步 append 到 active.jsonl
7. PersistentStore 写后检查文件大小,超过阈值触发 Rotate
8. RequestHandler 把响应字符串写回 Socket

数据流(读路径):
1. 客户端发 `GET key\n` → 上面 1-4 步相同
2. handler 调 NormalStore.get()
3. NormalStore 检查内存 Map + 检查 TTL → 返回 Entry 或 nil
4. handler 输出 `(nil)` 或 `"value"`,写回 Socket

---

## 3. 包结构与文件清单(成员A)

```
easy-db/
├── pom.xml                                          # Maven 配置(JDK 8, Gson 2.10.1)
├── README.md                                        # 快速开始
├── easy-db                                          # Shell 包装脚本(无扩展名)
├── docs/
│   └── superpowers/
│       └── specs/
│           └── 2026-07-13-easy-db-member-a-design.md   # 本文档
├── data/                                            # 运行时创建,持久化数据目录
├── src/
│   ├── main/java/
│   │   ├── server/
│   │   │   ├── EasyDbServer.java                    # main 入口,解析 --port
│   │   │   ├── SocketServerController.java          # ServerSocket + ExecutorService
│   │   │   ├── RequestHandler.java                  # 单连接的 Runnable
│   │   │   └── CommandDispatcher.java               # verb → handler 路由表
│   │   ├── store/
│   │   │   ├── NormalStore.java                     # 内存 KV 主表,线程安全
│   │   │   ├── Entry.java                           # KV 条目(含 TTL)
│   │   │   ├── CollectionManager.java               # Collection 抽象视图
│   │   │   ├── Collection.java                      # 虚拟 collection
│   │   │   ├── PersistentStore.java                 # JSON Lines append + Rotate
│   │   │   └── Compactor.java                       # 后台压缩服务
│   │   ├── handler/                                 # 命令处理器
│   │   │   ├── CommandHandler.java                  # 接口
│   │   │   ├── SetHandler.java
│   │   │   ├── DelHandler.java
│   │   │   ├── MsetHandler.java
│   │   │   ├── MdelHandler.java
│   │   │   ├── FlushHandler.java
│   │   │   ├── PingHandler.java
│   │   │   └── QuitHandler.java
│   │   ├── protocol/
│   │   │   ├── Request.java                         # 解析结果(cmd, key, value, ttlSeconds)
│   │   │   ├── Response.java                        # OK/(nil)/(integer)/value
│   │   │   └── ProtocolParser.java                  # 单行字符串 → Request
│   │   ├── client/
│   │   │   ├── SocketClient.java                    # TCP 客户端封装,共用于所有客户端
│   │   │   └── shell/
│   │   │       └── ShellClient.java                 # main(),单条命令模式
│   │   └── util/
│   │       ├── JsonUtil.java                        # Gson 单例
│   │       ├── Patterns.java                        # KEYS 通配符匹配(Simple wildcard → regex)
│   │       └── Logger.java                          # JUL 简单封装
│   └── test/java/
│       ├── store/
│       │   ├── NormalStoreTest.java                 # 单测:SET/GET/DEL/TTL
│       │   └── PersistentStoreRecoveryTest.java     # 集成:写→重启→读
│       └── protocol/
│           └── ProtocolParserTest.java              # 解析边界测试
└── scripts/
    └── smoke-test.sh                                # 验收 21 项中 A 的部分
```

预估 26 个 main 源代码文件 + 3 个测试文件 = 共 29 个 Java 文件,外加 2 个 shell 脚本(`easy-db` 包装脚本 + `smoke-test.sh`),符合方案 A 的预期。

---

## 4. 通信协议(最终版)

### 4.1 请求格式

一行,以 `\n` 结尾。命令名大小写不敏感。空格分隔。

| 命令 | 语法 | 说明 |
|------|------|------|
| SET | `SET <key> <value> [ttlSeconds]` | ttlSeconds 为可选整数(秒),传了则过期 |
| GET | `GET <key>` | 成员A不实现 handler,留接口 |
| DEL | `DEL <key>` | 删除单个 key |
| EXISTS | `EXISTS <key>` | 成员A不实现 |
| KEYS | `KEYS [pattern]` | 成员A不实现,pattern 默认 `*` |
| MSET | `MSET <k1> <v1> <k2> <v2> ...` | key/value 个数必须为偶数 |
| MDEL | `MDEL <k1> <k2> ...` | 至少 1 个 key |
| FLUSH | `FLUSH` | 清空所有数据 |
| PING | `PING` | 直接返回文本 `PONG`(不被 value 引用格式包裹) |
| QUIT | `QUIT` | 关闭连接 |

**特殊字符处理**:
- value 含空格 → 视为单个 token,不切分(`SET name 张 三` → key=name, value="张 三")
- value 含换行 → 返回错误(EASY-001),因为无法在单行协议中表达
- 命令名识别按第一个 token

### 4.2 响应格式

| 情况 | 格式 |
|------|------|
| 写操作成功 | `OK` |
| 读空值 | `(nil)` |
| 整数响应(EXISTS/DEL 返回数量等) | `(integer) N` |
| 字符串值 | `"value"`(用双引号包裹,内部 `\n` `\"` `\\` 转义) |
| 多行响应(KEYS) | 多行,每行一个 `"key"` |
| 错误 | `(error) ERR <message>` |

所有响应以 `\n` 结尾,除了 KEYS 多行(以 `*END\n` 终止)。

### 4.3 错误码

| 错误前缀 | 含义 |
|---------|------|
| `ERR unknown command` | 命令不存在(GET/KEYS/EXISTS 在 A 范围未注册) |
| `ERR wrong number of arguments for 'CMD'` | 参数数量不对 |
| `ERR value is not an integer or out of range` | TTL 非整数 |
| `ERR invalid pattern` | KEYS 通配符非法 |
| `ERR value contains newline` | value 不能含换行 |
| `ERR internal error` | 服务端异常 |

---

## 5. 核心数据结构

### 5.1 NormalStore

```java
public class NormalStore {
    private final ConcurrentHashMap<String, Entry> data;
    private final PersistentStore persistentStore;  // 委托持久化
    
    public String set(String key, String value, long ttlSeconds);
    public String get(String key);            // 返回 value 或 null
    public boolean del(String key);           // 返回是否实际删除
    public int mset(Map<String,String> kvs, long ttlSeconds);
    public int mdel(Collection<String> keys);
    public void flush();
}
```

### 5.2 Entry

```java
public class Entry {
    String key;
    String value;
    long expireAt;       // 0 = 永不过期;否则为 epoch millis
    long createdAt;
}
```

### 5.3 内存布局

```java
class NormalStore {
    ConcurrentHashMap<String, Entry> data;
    
    // TTL 用 ConcurrentHashMap 内 Entry.expireAt 检查(惰性过期)
    // 不引入额外的过期索引,因为 KV 量不会特别大(课程设计规模)
}
```

**为什么用 lazy expiration?** 课程设计规模下,不需要专门后台线程扫描过期,get 时检查即可。重启时再批量过滤掉已过期条目。

### 5.4 CollectionManager(虚拟视图)

```java
public class Collection {
    private final String name;       // "user"
    private final NormalStore store;
    
    public void set(String shortKey, String value, long ttlSeconds);
    public String get(String shortKey);
    public boolean del(String shortKey);
    public List<String> listKeys();   // 等价于 KEYS name:*
}

public class CollectionManager {
    private final NormalStore store;
    
    public Collection collection(String name);
    public List<String> listCollections();  // 通过扫描所有 key 前缀提取
}
```

**关键点**: 持久化的 key 始终是完整 key(`user:001`),Collection 只是逻辑视图,不创建独立存储空间。

---

## 6. 持久化详细设计

### 6.1 文件格式(JSON Lines)

每行一个 JSON 对象,UTF-8 编码,以 `\n` 分隔:

```json
{"op":"SET","key":"user:001","value":"张三","expireAt":0,"ts":1720780800000}
{"op":"DEL","key":"user:002","ts":1720780800001}
{"op":"MSET","items":[{"key":"k1","value":"v1"},{"key":"k2","value":"v2"}],"expireAt":0,"ts":1720780800002}
{"op":"MDEL","keys":["k1","k2"],"ts":1720780800003}
{"op":"FLUSH","ts":1720780800004}
```

字段:
- `op`: SET / DEL / MSET / MDEL / FLUSH
- `key` / `keys` / `value` / `items`: 对应数据
- `expireAt`: epoch millis,0 表示永不过期
- `ts`: 服务端时间戳,用于日志

### 6.2 写时持久化(write-through)

```
NormalStore.set(key, value, ttl):
    1. appendJsonLine({"op":"SET", ...})           // 同步,直到 OS 确认写入
    2. data.put(key, new Entry(...))               // 更新内存
    3. 检查文件大小 → 触发 Rotate(如需要)
```

**为什么不用 buffering?** 课程设计规模下,每次写都是一个完整请求,同步 append 性能足够。简化设计。

### 6.3 Rotate 触发

- 每次 append 后检查 `active.jsonl.length()`
- 若 ≥ 64 MB:
  1. 关闭当前 FileWriter
  2. 重命名为 `rotated-NNN.jsonl`(NNN 为递增 3 位数,001 起步)
  3. 创建新的 active.jsonl
  4. 把"rotate"事件写到日志(不写数据文件,因为仅是元数据)
- 64MB 是经验值,可以通过常量 `ROTATE_THRESHOLD_BYTES` 调整

### 6.4 Compactor(多线程压缩)

```java
public class Compactor {
    private final ScheduledExecutorService scheduler;
    private final Path dataDir;
    private final ExecutorService compressPool;  // 多线程并行压缩
    
    public void start();   // 启动定时任务(每 5 分钟扫描)
    public void compressFile(Path file);  // 单独压缩一个文件
}
```

压缩过程:
1. 读 `rotated-NNN.jsonl` 所有行
2. 按 key 聚合(用 ConcurrentHashMap<key, latestOp>),保留最新 op
3. 丢弃 expireAt < now 的 SET op
4. 写到一个新的 `rotated-NNN.jsonl.gz` 中(同样格式,只是被 GZIP 包装)
5. 删除原 uncompressed 文件
6. 多个文件并行压缩(F-103 要求"多线程压缩")

启动:
- `Compactor.start()` 在 `EasyDbServer.main` 中创建
- `Runtime.getRuntime().addShutdownHook()` 调用 `Compactor.stop()`

### 6.5 重启恢复

```java
public void loadFromDisk() {
    // 1. 扫描 data/ 下所有 .jsonl / .jsonl.gz
    // 2. 按文件名排序(active.jsonl 最后处理,因为它是当前最新)
    // 3. 对每个文件,逐行解析,replay 到 NormalStore
    //    但因为是按文件顺序 replay,且同 key 后写覆盖前写,等于按时间顺序应用 ops
    // 4. 完成后,NormalStore 内存状态与磁盘一致
}
```

**关键**: 重启时不重新写文件(否则每次启动都会重写全量)。只在 SET/DEL/FLUSH 时 append。

---

## 7. Socket 服务端

### 7.1 EasyDbServer(入口)

```java
public static void main(String[] args) {
    int port = parsePort(args);            // 默认 8080
    String dataDir = System.getProperty("easy-db.data", "./data");
    
    NormalStore store = new NormalStore(dataDir);
    store.loadFromDisk();
    
    new Compactor(store).start();
    new SocketServerController(port, store).start();
    
    Runtime.getRuntime().addShutdownHook(...);    // 优雅退出
}
```

### 7.2 SocketServerController

```java
public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    threadPool = Executors.newCachedThreadPool();
    
    while (running) {
        Socket client = serverSocket.accept();
        threadPool.submit(new RequestHandler(client, dispatcher));
    }
}
```

### 7.3 RequestHandler(每连接一个)

```java
public void run() {
    try (BufferedReader in = ...; PrintWriter out = ...) {
        String line;
        while ((line = in.readLine()) != null) {
            Request req = ProtocolParser.parse(line);
            if (req.isQuit()) break;
            Response res = dispatcher.dispatch(req);    // 路由+执行
            out.println(res.toWireFormat());             // 写回客户端
        }
    } catch (IOException e) {
        // log and close
    }
}
```

### 7.4 CommandDispatcher

```java
public class CommandDispatcher {
    private final Map<String, CommandHandler> handlers = new HashMap<>();
    
    public void register(String verb, CommandHandler handler);
    public Response dispatch(Request req);
}
```

默认注册的 verb:SET / DEL / MSET / MDEL / FLUSH / PING / QUIT。
**不注册** GET/KEYS/EXISTS — 这部分留接口给成员B 或 后续扩展。

---

## 8. Shell 工具

### 8.1 ShellClient.java

```java
public static void main(String[] args) {
    String host = System.getenv().getOrDefault("EASY_DB_HOST", "127.0.0.1");
    int port = Integer.parseInt(System.getenv().getOrDefault("EASY_DB_PORT", "8080"));
    
    // 解析 -s / --silent / -h / --help
    boolean silent = false;
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
        if (!result.isEmpty()) System.out.println(result);
    } catch (Exception e) {
        if (!silent) System.err.println("(error) " + e.getMessage());
        System.exit(1);
    }
}
```

### 8.2 包装脚本 `easy-db`(项目根)

```bash
#!/bin/bash
# easy-db Shell 包装脚本
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/easy-db-shell.jar"

# 如果 jar 不存在,尝试编译
if [ ! -f "$JAR_PATH" ]; then
    echo "Building easy-db-shell..." >&2
    (cd "$SCRIPT_DIR" && mvn package -DskipTests -q) || exit 1
fi

exec java -jar "$JAR_PATH" "$@"
```

### 8.3 静默模式示例

```
$ easy-db -s get name          →  只输出 value,不输出提示
$ easy-db -s keys *            →  逐行输出 key,便于管道
$ easy-db -s set name 张三     →  静默时只输出 OK(去掉 OK 用于管道更友好,见 8.4)
```

### 8.4 输出格式与管道友好性

- 单值响应:直接输出值,无前缀
- 多值响应:每行一个值
- 成功响应:输出 `OK`(用户可用 `2>&1` 区分)
- 错误:输出 `(error) ...`,退出码 1

---

## 9. 错误处理总览

| 场景 | 行为 |
|------|------|
| Accept 异常 | log + 继续 accept 下一个 |
| 单连接 read 异常 | 关闭该连接,不影响其他客户端 |
| handler 抛异常 | log + 返回 `(error) ERR internal error` |
| append 失败(磁盘满) | 返回 `(error) ERR disk write failed` |
| compress 失败 | log + 跳过该文件,下次再试 |
| TTL = 0 或负数 | 当作永不过期 |
| 超大 SET value(>10MB) | 返回错误(防止内存爆) |

---

## 10. 测试与验收

### 10.1 单元测试

- `NormalStoreTest`:SET/GET/DEL/MSET/MDEL/FLUSH/TTL 触发
- `PersistentStoreRecoveryTest`:写 → 模拟崩溃 → 重启 → 验证数据完整
- `ProtocolParserTest`:边界 case,空字符串,空格,负数 TTL,未知命令

### 10.2 集成测试(shell 脚本)

`scripts/smoke-test.sh`:
```bash
#!/bin/bash
set -e
# 1. 启动服务
java -jar target/easy-db-server.jar --port 8081 &
SERVER_PID=$!
sleep 1

# 2. 通过 easy-db 命令逐个跑验收点
./easy-db -s set user:001 张三               # → 写到静默输出
./easy-db -s get user:001                    # → 张三
./easy-db -s del user:001
./easy-db -s exists user:001                 # → (B 提供)
# ...

# 3. 优雅退出
kill $SERVER_PID
wait $SERVER_PID
```

### 10.3 验收清单(成员A对应)

| 验收点 | 测试方法 |
|--------|---------|
| 1. 服务端启动监听端口 | `lsof -i :8080` |
| 2. 多客户端并发 | `nc 127.0.0.1 8080` 打开 3 个窗口,同时 SET |
| 3. SET 存储键值对 | `./easy-db set name 张三` → `./easy-db get name` |
| 5. DEL 删除键值 | `./easy-db del name` |
| 8. FLUSH 清空 | `./easy-db flush` |
| 10. 重启数据不丢失 | set N 条 → kill → start → 全部能 get |
| 11. 文件 Rotate | 写 70MB → 观察 data/rotated-001.jsonl 出现 |
| 12. Rotate 后压缩 | 等 5 分钟 → data/rotated-001.jsonl.gz 出现 |
| 15. Shell 单命令执行 | `./easy-db set ...` |
| 16. Shell 环境变量 | `EASY_DB_PORT=8081 ./easy-db ...` |

---

## 11. 依赖与构建

### 11.1 pom.xml 关键依赖

```xml
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
    <plugins>
        <!-- shade 插件打 fat jar,便于 java -jar 直接运行 -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
        </plugin>
    </plugins>
</build>
```

### 11.2 运行方式

```bash
# 编译
mvn clean package

# 启动服务
java -jar target/easy-db-server.jar --port 8080

# 启动 Shell 客户端
./easy-db set name 张三
./easy-db get name
```

---

## 12. 与成员B的接口约定(暂存)

| 接口 | 提供方 | 说明 |
|------|--------|------|
| `NormalStore.get(String key)` | A | 已提供,B 直接用 |
| `NormalStore.keys(String pattern)` | A | 已提供,B 在 GET/KEYS handler 里调用 |
| `RestDispatcher` / `HttpRequest` 等 | B | 自由发挥 |
| `easy-db-shell` 仅提供 shell | A | B 自己做 cli.jar |

---

## 13. 风险与限制

| 风险 | 缓解 |
|------|------|
| 写时同步 append 性能差 | 课程设计规模可接受;文档中说明 |
| 多 value 的 MSET 不是原子 | 用同步锁包住所有 op |
| Compressor 并发压缩冲突 | 每个文件独立压缩,完成后原子重命名 |
| JVM 崩溃数据丢失 | 同步 append 保证已写的不丢;但未 sync 的可能丢 |
| Windows 文件锁 | 关闭 FileWriter 后再重命名,避免 Windows 锁文件 |
| value 含换行 | 协议级错误,需要用户用其他方式 |

---

## 14. 不做的事(YAGNI)

- ❌ 用户认证 / 权限
- ❌ 集群 / 主从
- ❌ LSM-Tree(增强项 E-002)
- ❌ 自定义二进制格式(方案A用 JSON Lines)
- ❌ GUI / CLI / SDK / RESTful API(由 B 做)
- ❌ 索引 / 缓存(由 B 做)
- ❌ TLS / 加密 socket
- ❌ 后台 TTL 扫描(用 lazy expiration)
- ❌ 配置文件(只用命令行参数 + 环境变量)

---

## 15. 验证清单(实施完后逐项过)

- [ ] `mvn clean package` 成功
- [ ] `java -jar target/easy-db-server.jar` 启动,日志显示监听端口
- [ ] `./easy-db set name 张三` → 看到 OK
- [ ] `./easy-db get name` → 看到 "张三"
- [ ] 同时启 2 个 nc 客户端,互不干扰
- [ ] `./easy-db del name` → 看到 OK
- [ ] `./easy-db flush` → 看到 OK,后续 get 都是 nil
- [ ] kill 服务后重启,前面 set 的数据能 get 到
- [ ] 写入让文件超过 64MB,看到 active.jsonl 被改名
- [ ] 等几分钟,看到 rotated-NNN.jsonl.gz 出现
- [ ] `EASY_DB_PORT=9999 ./easy-db ...` 连接到 9999
- [ ] `./easy-db -s ...` 只输出数据

---

*文档结束*
