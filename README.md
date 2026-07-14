# easy-db

基于 C/S 架构的 NoSQL 键值数据库，Java 课程设计项目。

## 概述

`easy-db` 是一个完整的键值存储系统，实现了内存存储、磁盘持久化、LSM-Tree 存储结构、集群模式（Master-Slave + 动态选举），以及多种客户端交互方式（Shell、CLI 交互工具、RESTful API、Swing GUI）。

服务端通过 TCP 文本协议对外暴露接口（一行一条命令，以 `\n` 分隔）。

## 项目结构

```
easy-db/
├── pom.xml                     # Maven 构建配置（JDK 8, Gson, JUnit, shade）
├── README.md                   # 本文件
├── easy-db                     # Shell 包装脚本
├── easy-db-cli                 # CLI 交互工具包装脚本
├── docs/                       # 设计文档、实现计划
├── scripts/                    # 冒烟测试脚本
└── src/
    ├── main/java/
    │   ├── server/             # 服务端入口、Socket 监听、请求分发
    │   ├── protocol/           # 通信协议（解析、请求/响应格式）
    │   ├── store/              # 存储引擎
    │   │   └── lsm/            # LSM-Tree 引擎（MemTable + SSTable + BloomFilter）
    │   ├── handler/            # 命令处理器（12个命令）
    │   ├── cluster/            # 集群模式（选举、心跳、复制）
    │   ├── client/             # 客户端
    │   │   ├── shell/          # Shell 单命令客户端
    │   │   ├── cli/            # 交互式 REPL 客户端
    │   │   └── gui/            # Swing GUI 客户端
    │   ├── rest/               # RESTful HTTP API
    │   └── util/               # 工具类（JSON、日志、通配符）
    └── test/java/              # 单元测试
```

## 构建

需要 JDK 8 和 Maven 3.6+。

```bash
mvn package
```

生成 `target/easy-db-server.jar`——包含所有依赖的 fat JAR，主类为 `server.EasyDbServer`。

## 启动服务

```bash
java -jar target/easy-db-server.jar
```

服务默认监听 `127.0.0.1:8080`，可通过参数修改：

```bash
# 自定义端口和数据目录
java -jar target/easy-db-server.jar --port 8080 --data ./mydata

# 启用 LSM-Tree 存储引擎
java -jar target/easy-db-server.jar --storage lsm

# 启用集群模式
java -jar target/easy-db-server.jar --port 8080 --cluster --cluster-config ./cluster-config.json
```

## 存储引擎

### 默认模式（内存 HashMap + JSON Lines 持久化）

- 数据以 ConcurrentHashMap 在内存中维护，作为唯一数据源
- JSON Lines 格式追加写入 `data/active.jsonl`（write-through）
- `active.jsonl` 达到 64MB 自动 rotate 为 `rotated-NNN.jsonl`
- Compactor 后台线程将 rotated 文件合并去重后 GZIP 压缩
- 启动时重放所有日志文件恢复内存状态

### LSM-Tree 模式（`--storage lsm`）

参考 LSMT 设计的存储引擎：

| 组件 | 实现 |
| ---- | ---- |
| **WAL** | `active.jsonl` 追加日志 |
| **MemTable** | `ConcurrentSkipListMap` 排序内存表 |
| **SSTable** | 排序不可变数据文件 + 稀疏索引 + Bloom Filter |
| **Compaction** | Size-tiered：小文件归并为大文件 |

## 集群模式

支持 Master-Slave 架构 + 动态选举（简化 Raft 协议）：

- **角色**：Leader / Follower / Candidate
- **选举**：心跳超时触发选举，获多数票成为 Leader
- **数据复制**：Leader 将写操作同步到 Follower
- **转发**：Follower 收到写请求转发给 Leader

集群配置文件示例（`cluster-config.json`）：

```json
{
  "nodeId": "node1",
  "clusterPort": 9080,
  "peers": [
    {"nodeId": "node1", "host": "127.0.0.1", "port": 8080, "clusterPort": 9080},
    {"nodeId": "node2", "host": "127.0.0.1", "port": 8081, "clusterPort": 9081}
  ]
}
```

集群状态查询：

```bash
./easy-db cluster info
```

## 支持的命令

| 命令 | 语法 | 说明 |
| ---- | ---- | ---- |
| `SET` | `SET k v [ttlSeconds]` | 设置键值对，可选 TTL |
| `GET` | `GET k` | 读取键值 |
| `DEL` | `DEL k` | 删除单个键 |
| `EXISTS` | `EXISTS k` | 检查键是否存在 |
| `KEYS` | `KEYS [pattern]` | 按模式匹配列出键名 |
| `MSET` | `MSET k1 v1 k2 v2 ...` | 批量设置多对键值 |
| `MDEL` | `MDEL k1 k2 ...` | 批量删除多个键 |
| `MUPD` | `MUPD k1 v1 k2 v2 ...` | 批量更新（仅更新已存在的键） |
| `FLUSH` | `FLUSH` | 清空所有数据 |
| `PING` | `PING` | 健康检查，返回 `"PONG"` |
| `QUIT` | `QUIT` | 关闭连接 |
| `CLUSTER` | `CLUSTER INFO\|ROLE\|LEADER` | 集群状态查询 |

## 客户端工具

### Shell 客户端（单命令模式）

```bash
./easy-db set name zhangsan
./easy-db get name
./easy-db -s ping              # 静默模式，只输出数据
```

支持环境变量 `EASY_DB_HOST` / `EASY_DB_PORT`。

### CLI 交互工具（REPL 模式）

```bash
./easy-db-cli                   # 默认连接 127.0.0.1:8080
./easy-db-cli 192.168.1.1 8080  # 自定义地址端口
```

交互式操作，支持命令历史和 `help`/`history`/`exit` 元命令。

### RESTful API

```bash
java -cp target/easy-db-server.jar rest.RestApiServer [port]
```

端点：

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| `GET` | `/api/ping` | 健康检查 |
| `GET` | `/api/keys/{key}` | 读取键值 |
| `POST` | `/api/keys/{key}` | 创建/更新键（body: `{"value":"v","ttl":60}`） |
| `PUT` | `/api/keys/{key}` | 更新已存在的键 |
| `DELETE` | `/api/keys/{key}` | 删除键 |
| `GET` | `/api/keys?pattern=*` | 列出匹配的键 |
| `POST` | `/api/keys/batch` | 批量操作 |

### GUI 客户端（Swing）

```bash
java -cp target/easy-db-server.jar client.gui.GuiClient
```

功能：连接管理、命令发送、响应显示、键值浏览表。

## 测试

```bash
mvn test                       # 单元测试
bash scripts/smoke-test.sh     # 端到端冒烟测试（需先启动服务）
```

使用 JUnit 4 进行单元测试，覆盖协议解析、内存存储、持久化恢复、LSM-Tree 等场景。

## 技术栈

- **JDK**: 8
- **构建**: Maven + maven-shade-plugin
- **依赖**: Gson 2.10.1（JSON 序列化）
- **测试**: JUnit 4.13.2
- **客户端**: Swing（GUI）、`com.sun.net.httpserver`（REST）
- **存储**: ConcurrentHashMap / ConcurrentSkipListMap + JSON Lines / SSTable
