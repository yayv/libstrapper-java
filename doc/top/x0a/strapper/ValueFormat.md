# top.x0a.strapper.ValueFormat

**Package:** `top.x0a.strapper`

**Kind:** `record`

一条值的格式描述，对应 valueFormat.md 的语法：

<pre>
  "[*|!]&lt;格式名&gt;[数值范围][:长度][#默认值]//说明"
</pre>

例如 `"*string{\u7537,\u5973`:4#\u7537//\u6027\u522b"}、`"*int[1990,2030]:4//\u5e74\u4efd"`、
`"mobile//\u624b\u673a\u53f7"`。

## Fields

### `PATTERN` (`java.util.regex.Pattern`)

### `comment` (`java.lang.String`)

### `defaultValue` (`java.lang.String`)

### `length` (`java.lang.Integer`)

### `range` (`top.x0a.strapper.ValueFormat.Range`)

### `requirement` (`top.x0a.strapper.Requirement`)

### `source` (`java.lang.String`)

### `typeName` (`java.lang.String`)

## Record Components

### `comment` (`java.lang.String`)

给产品、业务同学看的说明

### `defaultValue` (`java.lang.String`)

取值失败时兜底用的默认值，没写时为 `null`

### `length` (`java.lang.Integer`)

原始值允许的最大字符长度，没写时为 `null`

### `range` (`top.x0a.strapper.ValueFormat.Range`)

取值范围，没写时为 `null`

### `requirement` (`top.x0a.strapper.Requirement`)

必填性

### `source` (`java.lang.String`)

原始格式串

### `typeName` (`java.lang.String`)

格式名，可以是短名（`mobile`）或全名（`string.mobile`）

## Constructors

### `public ValueFormat(java.lang.String source, top.x0a.strapper.Requirement requirement, java.lang.String typeName, top.x0a.strapper.ValueFormat.Range range, java.lang.Integer length, java.lang.String defaultValue, java.lang.String comment)`

#### Parameters

- `source` (`java.lang.String`)
- `requirement` (`top.x0a.strapper.Requirement`)
- `typeName` (`java.lang.String`)
- `range` (`top.x0a.strapper.ValueFormat.Range`)
- `length` (`java.lang.Integer`)
- `defaultValue` (`java.lang.String`)
- `comment` (`java.lang.String`)

## Methods

### `public java.lang.String comment()`

### `public java.lang.String defaultValue()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public boolean hasDefault()`

是否写了默认值（`#` 后面即使是空串也算写了）。

### `public final int hashCode()`

### `public java.lang.Integer length()`

### `public static top.x0a.strapper.ValueFormat parse(java.lang.String source)`

解析一条值格式描述；语法错误时抛 `FormatException`。

#### Parameters

- `source` (`java.lang.String`)

### `private static top.x0a.strapper.ValueFormat.Range parseRange(java.lang.String left, java.lang.String body, java.lang.String right, java.lang.String source)`

#### Parameters

- `left` (`java.lang.String`)
- `body` (`java.lang.String`)
- `right` (`java.lang.String`)
- `source` (`java.lang.String`)

### `public top.x0a.strapper.ValueFormat.Range range()`

### `public top.x0a.strapper.Requirement requirement()`

### `public java.lang.String source()`

### `public final java.lang.String toString()`

### `public java.lang.String typeName()`
