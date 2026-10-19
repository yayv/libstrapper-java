# libstrapper-java

[libstrapper](../libstrapper) 接口参数契约规范的 Java 实现。用一段 JSON 形态的**格式描述**去校验另一段
JSON 形态的**参数数据**，产出归一化后的取值和一份问题清单。

- 规范：[valueFormat](../libstrapper/spec/valueFormat.md)（值怎么描述）、
  [dataStruct](../libstrapper/spec/dataStruct.md)（一组参数怎么描述）、
  [fileFormat](../libstrapper/spec/fileFormat.md)（接口文档怎么写）
- 包名 `top.x0a.strapper`，构建工具 Gradle，**零第三方依赖**（JSON 解析和正则都用 JDK 自带能力）
- 需要 JDK 21

## 快速开始

```java
String format = """
    {
      "username":"*string:32//用户名",
      "age":"int[0,150]#18//年龄，不传按 18",
      "gender":"string{男,女}//性别可以不填"
    }""";

StrapResult result = new Strapper().strap(format, requestBody);
if (!result.ok()) {
    return fail(result.report());
}
Map<String, Object> params = result.asMap();
long age = (Long) params.get("age");
```

## 构建与运行

```bash
./gradlew build       # 编译 + 打 jar + 跑自检
./gradlew packageJar  # 把 jar 和 sources jar 复制到 lib/
./gradlew selfTest    # 只跑自检用例（零依赖，不是 JUnit）
./gradlew runDemo     # 跑 demo，十二个场景演示怎么用
./gradlew runBench    # JSON 解析性能对比（会拉第三方库，只进 bench 源集）
```

## JSON 解析的性能

内置的 `Json.java` 是个手写的递归下降解析器，存在的理由是"零依赖"，不是"快"。
实测（M 系列 Mac / JDK 21 / 每个库各占一个干净 JVM / 两次独立运行偏差 &lt;2%）：

| 输入 | 字节 | strapper | Jackson 2.22 | Gson 2.14 | fastjson2 2.0.65 |
|---|---:|---:|---:|---:|---:|
| 典型请求体 | 410 | 777K/s（基准） | 1.07M/s **1.38x** | 853K/s **1.10x** | 1.91M/s **2.46x** |
| 格式描述 | 579 | 495K/s（基准） | 946K/s **1.91x** | 782K/s **1.58x** | 1.61M/s **3.24x** |
| 中等列表 | 28K | 10.1K/s（基准） | 13.3K/s **1.31x** | 11.4K/s **1.13x** | 24.3K/s **2.40x** |
| 大文档 | 2.9M | 126/s（基准） | 136/s **1.08x** | 127/s **1.01x** | 253/s **2.02x** |

结论：比 Gson 慢一点（1.0~1.6x），比 Jackson 慢 1.1~1.9x，比 fastjson2 慢 2.0~3.2x。
吞吐在 270~350 MB/s 量级，一个 410 字节的典型请求体解析一次约 1.3 µs。

**但这个差距在本库的场景里基本不起作用。** 一次 `strap()` 要解析格式描述和数据两段 JSON，
再做格式自检和逐字段正则校验，实测 12 个字段的典型请求：

```
只解析这两段 JSON        460,869 次/秒    2.17 µs
完整 strap（解析+校验）    71,048 次/秒   14.07 µs
────────────────────────────────────────────────
JSON 解析占整体            15.4%
格式自检 + 正则校验占       84.6%
```

解析只占 15%。就算换成 fastjson2 把这部分再快 2.5 倍，整体也只快 9% 左右 ——
代价是引入一个第三方依赖。要真想提速，该优化的是那 85%（比如把格式描述的解析结果缓存起来，
免得每次请求都重新自检一遍）。

要是项目里本来就在用 Jackson / Gson / fastjson2，也可以直接换过去，见下一节。

自己复现：

```bash
./gradlew benchClasspath && tools/bench.sh          # 四个库各跑一个干净 JVM
java -cp "$(cat build/bench-cp.txt)" top.x0a.strapper.bench.StrapBench   # 解析占比
```

三个第三方库只出现在 `bench` 源集里，不进 `strapper.jar`，也不会传递给使用方
（`./gradlew dependencies --configuration runtimeClasspath` 输出 `No dependencies`）。
这不是 JMH：没有 fork、没有 GC 隔离、没有显著性检验，只测"同一台机器同一份输入下的大致量级"。

## top.x0a.json：可以单独拿去用的 JSON 工具

`Json`（解析）和 `JsonWriter`（写出）在 `top.x0a.json` 包里，不依赖 `top.x0a.strapper`
的任何东西，可以脱离本库单独使用。

### 解析

```java
Object tree = Json.parse(text);   // Map / List / String / Long / Double / Boolean / null
```

严格按 RFC 8259：不接受单引号、尾逗号、注释，也不允许文档结束后还有多余内容。
对参数校验这类场景，宽松解析是安全弱化，所以刻意不做。

### 写出

```java
JsonWriter.compact().write(map)                      // 紧凑，接口响应用这个
JsonWriter.pretty().write(map)                       // 缩进 2 空格，给人看
JsonWriter.pretty(4).write(map)                      // 缩进宽度可调
JsonWriter.compact().skipNulls(true).write(map)      // 值为 null 的 key 不写出来
JsonWriter.compact().escapeNonAscii(true).write(map) // 非 ASCII 写成 \uXXXX，输出全 ASCII
JsonWriter.compact().strict(true).write(map)         // 遇到不认识的类型直接报错，而不是 toString

JsonWriter.compact().writeTo(out, map);              // out 是任意 Appendable，不攒中间串
```

认得这些类型：`null`、`CharSequence`、`Character`、`Boolean`、`Number`、`Map`、
`Iterable`、Java 数组（含基本类型数组）、`Enum`（写成 `name()`）。
其余类型退化成 `toString()` 当字符串写，`strict(true)` 可以让这种情况直接报错。
`double` 一律写成不带指数的形式（`1.0E10` → `10000000000`），NaN 和 Infinity 写成 `null`。

配置方法返回新实例，实例本身不可变，可以做成常量在多线程间共享。

### 流式写出

响应体很大、或者要边查边写时，不必先攒一棵完整的树：

```java
// target 是这次输出专属的目标，任何 Appendable 都行
JsonWriter.compact().stream(target, out -> {
    out.beginObject()
       .field("code", "ok")
       .name("data").beginArray();
    for (Order order : orders) {           // 查一条写一条，内存里不会出现完整结果串
        out.beginObject()
           .field("id", order.id())
           .field("amount", order.amount())
           .endObject();
    }
    out.endArray().endObject();
});

// 连目标都不想操心时，用自带缓冲的重载
String json = JsonWriter.compact().stream(out -> out.beginObject().field("a", 1).endObject());

// 需要把写出过程拆到几个方法里时，还有 try-with-resources 的形式
try (JsonWriter.Stream out = JsonWriter.compact().stream(target)) { ... }
```

括号没闭合、对象里漏写 `name`、顶层写了两个值，这些都会当场抛 `IllegalStateException`，
不会悄悄产出一段坏 JSON。流式和整棵树写出可以混着用 —— 大结构流式拼，小对象直接
`value(someMap)` 丢进去。`Stream` 是线程封闭的，细节见下面的线程安全一节。

## 线程安全

三个类库都是独立的、可以放心在多线程下用的，但各自的契约不同。下表每一项都有并发自检用例
（8 线程 × 500 次）在守着：

| | 能不能多线程共用 | 为什么 |
|---|---|---|
| `Strapper`（配好之后） | ✅ | `strap()` 不改实例状态，每次调用的状态都在局部 |
| `Types`（配好之后） | ✅ | 只读 |
| `JsonReader` 内置实现 | ✅ | 无状态 |
| `Json.parse` | ✅ | 每次调用自己新建解析器，无静态状态 |
| `JsonWriter`（配置对象） | ✅ | 字段全 `final` 的 `final` 类，天然不可变 |
| `JsonWriter.Stream` | ❌ 线程封闭 | 有写到一半的状态，见下 |
| 写出的目标 `Appendable` | ❌ 各线程独占 | 见下 |

**不需要 `synchronized`，加了也救不了。** JSON 文档的原子单位是整篇文档，不是单次写入。
两个线程交替调 `beginObject()` / `field()`，就算每个方法都加锁，拿到的也是一份字段串门的文档 ——
锁只是把随机损坏变成确定性损坏。

### Stream 是线程封闭的，而且是强制的

不是靠文档约定：`Stream` 记住创建它的线程，别的线程一碰就抛 `IllegalStateException`。
优先用作用域式的写法，`Stream` 出不了 lambda，结构上就没机会泄漏：

```java
String json = JsonWriter.compact().stream(out -> out.beginObject().field("a", 1).endObject());
JsonWriter.compact().stream(target, out -> { ... });   // 写进自己指定的目标
```

确实要换线程接着写（比如异步接力），先由当前线程交接：

```java
stream.handOff();          // 必须由当前归属线程调用
// 之后任意线程都可以接手，并且一定看得到前面写进去的内容
```

交接是通过一次 volatile 写完成的，所以是真正的 happens-before 保证，不只是放行检查。

### 目标 `Appendable` 也必须是这个线程独占的

这条比共用 `Stream` 更容易漏。两个线程各拿各的 `Stream`、往同一个 `StringBuilder` 里写，
一样会写坏 —— `StringBuilder` 自己就不是线程安全的。实测 1000 个片段写进同一个
`StringBuilder`，最后只数出 735 个 `{`，内容交错且丢字符。

多个线程要往同一个目标汇聚时，各自在本地拼完整，再整篇一次性追加：

```java
String doc = JsonWriter.compact().write(payload);      // 本地拼完，互不干扰
synchronized (sharedOut) { sharedOut.append(doc); }    // 加锁粒度是一整篇文档
```

### 配置要在启动阶段一次性做完

`Strapper` 的配置字段是非 `final` 的（`types` / `jsonReader` / `conditionalResolver` /
`unknownTypePolicy`），`Types` 内部也是可变的 map。所以配置必须发生在"发布给其他线程之前"：

```java
// ✅ 安全：静态初始化有 happens-before 保证
static final Strapper STRAPPER = new Strapper()
        .addType("int.vehicleNo", "^20\d{2}\d{5}$")
        .disableType("text");

// ❌ 危险：已经在跑请求了才改配置，别的线程可能看到半配好的状态
STRAPPER.disableType("text");
```

`JsonWriter` 没有这个问题 —— 它字段全 `final`，JMM 保证即使通过数据竞争发布也能看到完整状态。

## 换掉内置的 JSON 解析

`Json` 在独立的 `top.x0a.json` 包里，不依赖 `top.x0a.strapper` 的任何东西，可以单独拿去用。
反过来，库只在两处需要解析 JSON（`strap()` 和 `checkFormat()` 各一次），所以插拔点只有一个方法：

```java
public interface JsonReader {
    Object read(String text);   // 返回 Map / List / String / Number / Boolean / null
}
```

项目里已经在用别的 JSON 库时，包一行就能换过去，不必在一个进程里驮两套解析实现：

```java
ObjectMapper mapper = new ObjectMapper();
strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));   // Jackson

Gson gson = new Gson();
strapper.jsonReader(JsonReader.wrapping(t -> gson.fromJson(t, Object.class)));      // Gson

strapper.jsonReader(JsonReader.wrapping(JSON::parse));                              // fastjson2
```

`wrapping` 会把第三方抛的各种异常统一转成 `JsonReader.JsonReadException`
（三家分别抛 `JsonParseException` / `JsonSyntaxException` / `JSONException`，互相没有共同基类）。

解析失败的错误码按用的是哪个 reader 区分：用内置实现时失败只可能是语法问题，报
`FORMAT_JSON_STRUCT_ERROR` / `DATA_JSON_STRUCT_ERROR`；换了 reader 之后失败原因本库无从知道
（语法错？还是适配器自己的策略把它挡了？），报 `FORMAT_JSON_READER_ERROR` /
`DATA_JSON_READER_ERROR`，真实原因在 `StrapIssue.detail()` 里。
适配器不一定是第三方库，也可以是自己的策略 —— demo 第 11 节演示了给请求体加大小上限。

可运行的适配器模板在 `src/bench/.../JsonReaderAdapters.java`，
实测四种后端在合法输入上产出的取值**完全一致**。

### 换之前要知道的三件事

这四家在"解析成通用树"这件事上一致性很高：容器都是 `java.util.Map` / `java.util.List`
的实现且都保 key 顺序，`null` 值都是"key 在、值为 null"，重复 key 都是后者覆盖。
但有三处不一样：

**① Gson 会丢精度。** 四家产出的数字类型不同：

| 输入 | 内置 | Jackson | Gson | fastjson2 |
|---|---|---|---|---|
| `1` | Long | Integer | **Double** | Integer |
| `1234567890123456789` | Long | Long | **Double** | Long |
| `1.5` | Double | Double | Double | **BigDecimal** |
| `3.14159265358979323846…` | Double | Double | Double | **BigDecimal**（不丢精度） |

Integer 和 BigDecimal 本库都能正确处理（按字符串形式校验）。但 Gson 一律转 `Double`，
超过 2^53 的整数会悄悄变样：`1234567890123456789` → `1234567890123456800`，
**校验照样通过，值已经错了**。ID 类字段用 Gson 后端时要留意。

**② 宽松度不同，换后端等于放松了这一层把关。**

| 畸形输入 | 内置 | Jackson | Gson | fastjson2 |
|---|---|---|---|---|
| `{"a":1} 多余内容` | 拒 | **接受** | 拒 | 拒 |
| `{'a':1}` 单引号 | 拒 | 拒 | **接受** | **接受** |
| `{"a":1,}` 尾逗号 | 拒 | 拒 | 拒 | **接受** |
| `""` 空串 | 拒 | 拒 | **返回 null** | **返回 null** |

内置实现严格按 RFC 8259，这些一律拒绝 —— 对参数校验这类场景，宽松解析是安全弱化。

**③ 只有读可换，写不换。** `StrapResult.toJson()` 始终用内置的 `JsonWriter`，
因为它写的是本库自己产出的 Map/List，不涉及第三方的对象模型。

## 在其他项目里引用

`./gradlew packageJar` 之后，`lib/` 下是可以直接拿走的成品：

```
lib/strapper-0.4.0.jar           # 库本体，内含 types.txt，零第三方依赖
lib/strapper-0.4.0-sources.jar   # 源码，给 IDE 看注释和跳转用
lib/API.md                       # 使用文档（手写）
```

Gradle：

```kotlin
dependencies {
    implementation(files("libs/strapper-0.4.0.jar"))
    // 或者整个目录：implementation(fileTree("libs") { include("*.jar") })
}
```

Maven（先 `mvn install:install-file` 装进本地仓库，或直接用 system scope 指路径）：

```xml
<dependency>
    <groupId>top.x0a</groupId>
    <artifactId>strapper</artifactId>
    <version>0.4.0</version>
</dependency>
```

命令行：

```bash
javac -cp lib/strapper-0.4.0.jar -d out Foo.java
java  -cp lib/strapper-0.4.0.jar:out Foo
```

## 目录

```
src/main/java/top/x0a/json/          独立的 JSON 工具，不依赖 strapper，可以单独拿去用
  Json.java                            解析，严格按 RFC 8259；写出的两个快捷入口
  JsonWriter.java                      写出：整棵树写出 + 流式写出，可配缩进/转义/跳过 null
src/main/java/top/x0a/strapper/      库本体
  Strapper.java                        入口：strap / checkFormat 和各项配置
  Types.java                           类型表：基础类型 + 明文的扩展类型
  JsonReader.java                      JSON 解析的插拔点，默认用 top.x0a.json.Json
  ValueFormat.java  KeyFormat.java     两条格式描述语法的解析
  Values.java                          取值归一化与范围比较
  StrapResult.java  StrapIssue.java  ErrorCode.java  Requirement.java  FormatException.java
src/main/resources/top/x0a/strapper/
  types.txt                            内置扩展类型表（明文，跨语言共用）
src/test/java/top/x0a/strapper/
  SelfTest.java                        259 条自检用例（含并发与线程封闭）
src/demo/java/top/x0a/strapper/demo/
  Demo.java                            用法演示（十二个场景）
src/doclet/java/top/x0a/tools/
  MarkdownDoclet.java                  把 javadoc 注释生成为单个 Markdown 文件的 doclet
src/bench/java/top/x0a/strapper/bench/
  JsonBench.java                       与 Jackson / Gson / fastjson2 的解析性能对比
  StrapBench.java                      解析在一次 strap() 里的占比
  JsonCompat.java                      四个解析器的行为差异探测
  JsonReaderAdapters.java              三个第三方适配器的可运行模板
src/top/x0a/strapper-v0.1/           上一版净室实现，已不在构建路径内
```

## 类型体系

类型分两层，这是本库的主要扩展点。

**基础类型**固定 9 个，决定一个值最终被解析成什么：

| 基础类型 | 取值 | 说明 |
|---|---|---|
| `int` | `Long` | |
| `float` / `number` | `Double` | `number` 是 `float` 的别名 |
| `bool` | `Boolean` | 接受 `true/false/1/0/yes/no` |
| `string` | `String` | 纯字符串，不允许制表符、换行符这类转义字符 |
| `text` | `String` | 任意文本，允许换行 |
| `date` | `String` | 归一化为 `yyyy-MM-dd` |
| `time` | `String` | 归一化为 `HH:mm:ss` |
| `datetime` | `String` | 归一化为 `yyyy-MM-dd HH:mm:ss` |

**扩展类型**就是一张「名 : 正则」的明文表，名字写成 `<基础类型>.<扩展名>`。
一个值要先过基础类型的正则，再过扩展类型的正则。spec 里列的 `mobile`、`idcard`、`retCode` 等
21 条内置扩展类型就写在 [`types.txt`](src/main/resources/top/x0a/strapper/types.txt) 里，
业务领域的类型走完全相同的入口：

```java
// 共享电动车领域：车辆编号 9 位数字，前 4 位是生产批次
Strapper strapper = new Strapper()
        .addType("int.vehicleNo", "^20\\d{2}\\d{5}$")
        .loadTypes(Path.of("conf/types.txt"));   // 或者整张表从明文读进来
```

格式描述里写全名 `"*int.vehicleNo"` 或短名 `"*vehicleNo"` 都行 —— 短名在全表内唯一时才能用，
重名时必须写全名。同名再写一行就是覆盖，不满意内置规则直接重写。

明文表的好处是跨语言共用：各语言实现读同一份表，正则方言有出入时只改对应那一行。
内置表刻意只用各家正则库都认的写法（锚定、字符组、`(?:)`、`|`、量词、`\d \s \x21`），
不用内联修饰组、命名组、反向引用和断言。

### string 与 text 的分工，以及禁用类型

`string` 和 `text` 的差别只在基础正则那一关：前者是纯字符串，不允许出现制表符、换行符；
后者允许。内置表里凡是单行短串一律挂在 `string` 下，只有确实可能带换行的才挂到 `text` 下
（目前只有 `inlineImage` 一条）。

这样分是为了让调用方能一次性收紧攻击面：

```java
Strapper strapper = new Strapper().disableType("text");
```

之后格式描述里再写 `text` 或任何 `text.*` 都会报 `FORMAT_TYPE_DISABLED`，
挂在 `string` 下的扩展类型不受影响。也可以只关单独一条：`disableType("string.email")`。
禁用是明确的项目决策，所以不受 `unknownTypePolicy(PASS_THROUGH)` 影响；
名字写错时直接抛异常而不是静默失败 —— 安全开关悄悄没生效比报错危险得多。

## 格式描述语法

### 值

```
"[*|!]<格式名>[数值范围][:长度][#默认值]//说明"
```

| 段 | 写法 | 含义 |
|---|---|---|
| 必填性 | `*` / `!` / 无 | 必填 / 条件必填 / 选填 |
| 格式名 | `int`、`mobile`、`int.vehicleNo` | 基础类型或扩展类型 |
| 数值范围 | `{男,女}` | 枚举 |
| | `[1,100]` `(1,100]` `[1,)` | 区间，方括号闭、圆括号开，端点留空表示该侧无界 |
| 长度 | `:32` | 原始字符串的最大长度 |
| 默认值 | `#18` | 取值失败或越界时的兜底值 |
| 说明 | `//年龄` | 给产品和业务同学看的 |

### KEY

```
"[*|!][[n]|[n,m]]<key名>"
```

- `"*carData"` —— 值为对象或数组时，必填性标在 key 上
- `"*[4,9]fruits"` —— 必填，且数组长度 4~9；`"[2]fruits"` 表示恰好 2 个
- `"..."` —— 本级接受未在格式中列出的 key；值为格式串时对这些 key 生效，值为 `{}` / `[]` 时原样收下
- `"!string"` —— key 名本身不确定，只约定 key 要符合某个类型

## 几处需要说明的取舍

规范里有些地方留了口子，PHP 版也标着 TODO，这份实现的选择是：

1. **`!` 条件必填** —— 规范说条件"需要在具体情境下具体描述"，所以判断权交给调用方：
   注册 `conditionalResolver` 即可，不注册时按选填处理。
2. **默认值** —— 值不合规时回落到默认值并记一条**告警**（不影响 `ok()`）；
   选填项整个缺失且写了默认值时，直接补上，不记告警。默认值自身不合规时报
   `FORMAT_DEFAULT_VALUE_INVALID`，这是格式描述写错了。
3. **未定义的 key** —— 没写 `"..."` 时丢弃并记告警（沿用 PHP 版的丢弃行为，但让它可见）。
4. **未定义的类型** —— 默认报错，让类型名的笔误在开发期暴露；
   需要 PHP 版那种放行行为时设 `unknownTypePolicy(PASS_THROUGH)`。
5. **JSON 里的 `null`** —— 等同于该 key 缺失，与 PHP 版的 `isset` 语义一致。
6. **格式描述有错时不再校验数据** —— 拿一份错的契约去校验数据，报出来的问题只会误导人。
7. **`datetime` 的时区** —— 带 `Z` 或 `+08:00` 后缀时取墙上时钟部分，不做时区换算；
   接口契约层不该替调用方决定时区。
8. **`float` 的 `0`、`bool` 的 `false`** —— 都是合法取值。PHP 版在这里用真假值判断，
   会把 `0` 和空串当成取值失败，这份实现没有沿用。

## 主要 API

```java
// 配置（启动阶段一次性配好，之后 strap 不改变实例状态，可多线程共享）
new Strapper()
    .addType(name, regex)                  // 加一条扩展类型
    .addTypes(Map<String,String>)          // 批量加
    .loadTypes(text)  .loadTypes(path)     // 从明文/文件读一张表
    .types(Types)     .types()             // 换用/取出整张类型表
    .disableType(name)   .enableType(name) // 禁用/启用类型，禁用基础类型会连带禁用其扩展
    .jsonReader(JsonReader)   .jsonReader()  // 换掉内置的 JSON 解析
    .conditionalResolver(resolver)         // ! 条件必填的裁决逻辑
    .unknownTypePolicy(ERROR | PASS_THROUGH);

// 使用
StrapResult r = strapper.strap(format, json);   // 校验数据
StrapResult c = strapper.checkFormat(format);   // 只检查格式描述，适合放进 CI

r.ok()  r.errors()  r.warnings()  r.report()
r.asMap()  r.asList()  r.find("data.list[0].driver")  r.toJson()  r.toPrettyJson()
```
