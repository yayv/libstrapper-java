# top.x0a.strapper.Types.TypeDef

**Package:** `top.x0a.strapper`

**Kind:** `record`

一个可用的类型：基础类型本身，或一条扩展类型。

## Fields

### `base` (`java.lang.String`)

### `basePattern` (`java.util.regex.Pattern`)

### `kind` (`top.x0a.strapper.Types.Kind`)

### `name` (`java.lang.String`)

### `pattern` (`java.util.regex.Pattern`)

## Record Components

### `base` (`java.lang.String`)

### `basePattern` (`java.util.regex.Pattern`)

### `kind` (`top.x0a.strapper.Types.Kind`)

### `name` (`java.lang.String`)

### `pattern` (`java.util.regex.Pattern`)

## Constructors

### `public TypeDef(java.lang.String name, java.lang.String base, top.x0a.strapper.Types.Kind kind, java.util.regex.Pattern basePattern, java.util.regex.Pattern pattern)`

#### Parameters

- `name` (`java.lang.String`)
- `base` (`java.lang.String`)
- `kind` (`top.x0a.strapper.Types.Kind`)
- `basePattern` (`java.util.regex.Pattern`)
- `pattern` (`java.util.regex.Pattern`)

## Methods

### `public java.lang.String base()`

### `public java.util.regex.Pattern basePattern()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean isBase()`

是否为基础类型（没有额外的扩展正则）。

### `public top.x0a.strapper.Types.Kind kind()`

### `public java.lang.String name()`

### `public java.util.regex.Pattern pattern()`

### `public java.lang.String toString()`
