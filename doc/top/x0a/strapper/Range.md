# top.x0a.strapper.ValueFormat.Range

**Package:** `top.x0a.strapper`

**Kind:** `record`

取值范围。两种形态：
<ul>
  <li>枚举 `{\u7537,\u5973`} —— `.enumeration()` 为 true，成员在 `.members()`</li>
  <li>区间 `(1,100]` —— 端点在 `.min()`/`.max()`，
      开闭由 `.minInclusive()`/`.maxInclusive()` 决定；端点写空表示该侧无界</li>
</ul>

## Fields

### `enumeration` (`boolean`)

### `max` (`java.lang.String`)

### `maxInclusive` (`boolean`)

### `members` (`java.util.List<java.lang.String>`)

### `min` (`java.lang.String`)

### `minInclusive` (`boolean`)

### `source` (`java.lang.String`)

## Record Components

### `enumeration` (`boolean`)

### `max` (`java.lang.String`)

### `maxInclusive` (`boolean`)

### `members` (`java.util.List<java.lang.String>`)

### `min` (`java.lang.String`)

### `minInclusive` (`boolean`)

### `source` (`java.lang.String`)

## Constructors

### `public Range(boolean enumeration, java.util.List<java.lang.String> members, java.lang.String min, java.lang.String max, boolean minInclusive, boolean maxInclusive, java.lang.String source)`

#### Parameters

- `enumeration` (`boolean`)
- `members` (`java.util.List<java.lang.String>`)
- `min` (`java.lang.String`)
- `max` (`java.lang.String`)
- `minInclusive` (`boolean`)
- `maxInclusive` (`boolean`)
- `source` (`java.lang.String`)

## Methods

### `public boolean enumeration()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean lowerUnbounded()`

该侧是否无界。

### `public java.lang.String max()`

### `public boolean maxInclusive()`

### `public java.util.List<java.lang.String> members()`

### `public java.lang.String min()`

### `public boolean minInclusive()`

### `public java.lang.String source()`

### `public java.lang.String toString()`

### `public boolean upperUnbounded()`
