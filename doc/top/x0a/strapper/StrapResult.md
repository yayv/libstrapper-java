# top.x0a.strapper.StrapResult

**Package:** `top.x0a.strapper`

**Kind:** `record`

一次校验的结果。

## Fields

### `errors` (`java.util.List<top.x0a.strapper.StrapIssue>`)

### `ok` (`boolean`)

### `value` (`java.lang.Object`)

### `warnings` (`java.util.List<top.x0a.strapper.StrapIssue>`)

## Record Components

### `errors` (`java.util.List<top.x0a.strapper.StrapIssue>`)

导致校验失败的问题

### `ok` (`boolean`)

是否没有 error 级问题（warning 不影响 ok）

### `value` (`java.lang.Object`)

按格式描述取出并归一化后的数据；顶层是 `Map` 或 `List`，失败时可能为 `null`

### `warnings` (`java.util.List<top.x0a.strapper.StrapIssue>`)

不影响结果可用性、但值得知会的问题（用了默认值、丢弃了未定义的 key 等）

## Constructors

### `public StrapResult(boolean ok, java.lang.Object value, java.util.List<top.x0a.strapper.StrapIssue> errors, java.util.List<top.x0a.strapper.StrapIssue> warnings)`

#### Parameters

- `ok` (`boolean`)
- `value` (`java.lang.Object`)
- `errors` (`java.util.List<top.x0a.strapper.StrapIssue>`)
- `warnings` (`java.util.List<top.x0a.strapper.StrapIssue>`)

## Methods

### `public java.util.List<java.lang.Object> asList()`

顶层为数组时的取值；不是数组则抛 `IllegalStateException`。

### `public java.util.Map<java.lang.String,java.lang.Object> asMap()`

顶层为对象时的取值；不是对象则抛 `IllegalStateException`。

### `private static java.lang.String describe(java.lang.Object v)`

#### Parameters

- `v` (`java.lang.Object`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<top.x0a.strapper.StrapIssue> errors()`

### `static top.x0a.strapper.StrapResult failure(top.x0a.strapper.StrapIssue issue)`

#### Parameters

- `issue` (`top.x0a.strapper.StrapIssue`)

### `public java.util.Optional<java.lang.Object> find(java.lang.String path)`

按路径取值，路径写法同错误信息里的 path，但不带开头的 `$.`：
`"data.list[0].modelId"`。取不到时返回空。

#### Parameters

- `path` (`java.lang.String`)

### `private static void flush(java.util.List<java.lang.String> tokens, java.lang.StringBuilder current)`

#### Parameters

- `tokens` (`java.util.List<java.lang.String>`)
- `current` (`java.lang.StringBuilder`)

### `public final int hashCode()`

### `public boolean ok()`

### `public java.lang.String report()`

人读的问题清单，直接打到日志里就能定位。

### `public java.lang.String toJson()`

取值的紧凑 JSON 形式。这里始终用内置的写出实现，与 `JsonReader` 换不换无关。

### `public java.lang.String toPrettyJson()`

取值的缩进 JSON 形式。

### `public final java.lang.String toString()`

### `private static java.util.List<java.lang.String> tokenize(java.lang.String path)`

#### Parameters

- `path` (`java.lang.String`)

### `public java.lang.Object value()`

### `public java.util.List<top.x0a.strapper.StrapIssue> warnings()`
