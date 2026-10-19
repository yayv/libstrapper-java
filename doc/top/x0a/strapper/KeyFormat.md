# top.x0a.strapper.KeyFormat

**Package:** `top.x0a.strapper`

**Kind:** `record`

一个 key 的格式描述，对应 valueFormat.md / dataStruct.md 里 key 上可以带的标记：

<pre>
  "[*|!][[n]|[n,m]]&lt;key名&gt;"
</pre>

<ul>
  <li>`"*carData"` —— 值为对象或数组时，必填性标在 key 上（dataStruct 特殊情况二）</li>
  <li>`"*[4,9]fruits"` —— 必填，且数组长度在 4~9 之间；`"[n]"` 表示长度恰为 n</li>
  <li>`"..."` —— 本级接受未在格式中列出的 key（`Kind.MORE_KEYS`）</li>
  <li>`"!string"` —— key 名本身不确定，只约定 key 要符合某个类型（`Kind.WILDCARD`）</li>
</ul>

## Fields

### `MORE_KEYS_TOKEN` (`java.lang.String`)

允许出现未定义 key 的特殊 key。

### `PATTERN` (`java.util.regex.Pattern`)

### `keyType` (`java.lang.String`)

### `kind` (`top.x0a.strapper.KeyFormat.Kind`)

### `maxItems` (`java.lang.Integer`)

### `minItems` (`java.lang.Integer`)

### `name` (`java.lang.String`)

### `requirement` (`top.x0a.strapper.Requirement`)

### `source` (`java.lang.String`)

## Record Components

### `keyType` (`java.lang.String`)

`WILDCARD` 时，key 名自身要满足的类型名

### `kind` (`top.x0a.strapper.KeyFormat.Kind`)

这个 key 的种类

### `maxItems` (`java.lang.Integer`)

值为数组时的最大长度，未约定为 `null`

### `minItems` (`java.lang.Integer`)

值为数组时的最小长度，未约定为 `null`

### `name` (`java.lang.String`)

普通 key 的名字；`MORE_KEYS`/`WILDCARD` 时无意义

### `requirement` (`top.x0a.strapper.Requirement`)

必填性

### `source` (`java.lang.String`)

原始 key 串

## Constructors

### `public KeyFormat(java.lang.String source, top.x0a.strapper.KeyFormat.Kind kind, top.x0a.strapper.Requirement requirement, java.lang.String name, java.lang.Integer minItems, java.lang.Integer maxItems, java.lang.String keyType)`

#### Parameters

- `source` (`java.lang.String`)
- `kind` (`top.x0a.strapper.KeyFormat.Kind`)
- `requirement` (`top.x0a.strapper.Requirement`)
- `name` (`java.lang.String`)
- `minItems` (`java.lang.Integer`)
- `maxItems` (`java.lang.Integer`)
- `keyType` (`java.lang.String`)

## Methods

### `public boolean acceptsSize(int size)`

数组长度是否落在约定内。

#### Parameters

- `size` (`int`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public boolean hasItemsRange()`

是否对数组长度做了约定。

### `public final int hashCode()`

### `public java.lang.String itemsRangeText()`

人读的长度约定描述，用于错误信息。

### `public java.lang.String keyType()`

### `public top.x0a.strapper.KeyFormat.Kind kind()`

### `public java.lang.Integer maxItems()`

### `public java.lang.Integer minItems()`

### `public java.lang.String name()`

### `public static top.x0a.strapper.KeyFormat parse(java.lang.String source, top.x0a.strapper.Types types)`

解析一个 key。

<p>`!` 在 key 上有两种读法：`!string` 是"key 名不确定"（dataStruct），
而 `!mobileVerified` 是"条件必填"（valueFormat）。消歧规则：
<b>标记为 `!`、没有数组长度约束、且剩余部分恰好是一个已注册的类型名</b> 时按通配 key 处理，
否则按条件必填的普通 key 处理。

#### Parameters

- `source` (`java.lang.String`)
- `types` (`top.x0a.strapper.Types`) — 用于判断 `!xxx` 里的 xxx 是不是类型名；传 `null` 则一律按普通 key 处理

### `public top.x0a.strapper.Requirement requirement()`

### `public java.lang.String source()`

### `public final java.lang.String toString()`
