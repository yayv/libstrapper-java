# top.x0a.json.Json

**Package:** `top.x0a.json`

**Kind:** `class`

极简 JSON 解析与序列化，零第三方依赖。

<p>这个类不依赖 `top.x0a.strapper` 里的任何东西，可以单独拿去用。
它严格按 RFC 8259 解析，不接受单引号、尾逗号、注释，也不允许文档结束后还有多余内容 ——
宽松解析对参数校验这类场景是安全弱化，所以刻意不做。

<p>解析结果使用 JDK 原生类型表达，便于直接消费：
<ul>
  <li>object &rarr; `LinkedHashMap`（保持书写顺序，格式描述里 key 的先后有意义）</li>
  <li>array  &rarr; `ArrayList`</li>
  <li>string &rarr; `String`</li>
  <li>number &rarr; `Long`（整数）或 `Double`（含小数点/指数）</li>
  <li>true/false &rarr; `Boolean`，null &rarr; `null`</li>
</ul>

## Constructors

### `private Json()`

## Methods

### `public static java.lang.Object parse(java.lang.String text)`

解析 JSON 文本。

#### Parameters

- `text` (`java.lang.String`) — 完整的 JSON 文档

#### Returns

Map / List / String / Long / Double / Boolean / `null` 构成的树

#### Throws

- `JsonException` — 文本不是合法 JSON；消息里带出错位置

### `public static java.lang.String write(java.lang.Object value)`

紧凑输出。等价于 `JsonWriter.compact().write(value)`。

#### Parameters

- `value` (`java.lang.Object`)

### `public static java.lang.String writePretty(java.lang.Object value)`

缩进输出，便于人读。等价于 `JsonWriter.pretty().write(value)`。

#### Parameters

- `value` (`java.lang.Object`)
