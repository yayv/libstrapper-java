# top.x0a.strapper.Types.Kind

**Package:** `top.x0a.strapper`

**Kind:** `enum`

基础类型决定一个值最终被解析成什么。

## Enum Constants

### `BOOL` (`top.x0a.strapper.Types.Kind`)

布尔，结果为 `Boolean`。

### `DATE` (`top.x0a.strapper.Types.Kind`)

日期，结果为归一化的 `yyyy-MM-dd`。

### `DATETIME` (`top.x0a.strapper.Types.Kind`)

日期时间，结果为归一化的 `yyyy-MM-dd HH:mm:ss`。

### `DECIMAL` (`top.x0a.strapper.Types.Kind`)

小数，结果为 `Double`。

### `INT` (`top.x0a.strapper.Types.Kind`)

整数，结果为 `Long`。

### `TEXT` (`top.x0a.strapper.Types.Kind`)

文本，结果为 `String`。

### `TIME` (`top.x0a.strapper.Types.Kind`)

时间，结果为归一化的 `HH:mm:ss`。

## Constructors

### `private Kind()`

## Methods

### `public static top.x0a.strapper.Types.Kind valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static top.x0a.strapper.Types.Kind[] values()`
