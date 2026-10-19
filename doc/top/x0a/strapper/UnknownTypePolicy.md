# top.x0a.strapper.Strapper.UnknownTypePolicy

**Package:** `top.x0a.strapper`

**Kind:** `enum`

格式描述里出现未定义类型时的处理策略。

## Enum Constants

### `ERROR` (`top.x0a.strapper.Strapper.UnknownTypePolicy`)

报 `ErrorCode.FORMAT_UNKNOWN_KEYTYPE_ERROR`，让类型名的笔误在开发期就暴露。默认值。

### `PASS_THROUGH` (`top.x0a.strapper.Strapper.UnknownTypePolicy`)

按 `text` 放行并记一条告警。PHP 版是这个行为，迁移期可用。

## Constructors

### `private UnknownTypePolicy()`

## Methods

### `public static top.x0a.strapper.Strapper.UnknownTypePolicy valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static top.x0a.strapper.Strapper.UnknownTypePolicy[] values()`
