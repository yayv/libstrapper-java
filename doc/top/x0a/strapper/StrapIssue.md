# top.x0a.strapper.StrapIssue

**Package:** `top.x0a.strapper`

**Kind:** `record`

一条校验问题。

## Fields

### `code` (`top.x0a.strapper.ErrorCode`)

### `detail` (`java.lang.String`)

### `path` (`java.lang.String`)

## Record Components

### `code` (`top.x0a.strapper.ErrorCode`)

问题码

### `detail` (`java.lang.String`)

针对本次问题的补充说明（可能为空串）

### `path` (`java.lang.String`)

出问题的位置，形如 `$.data.list[0].modelId`；根为 `$`

## Constructors

### `public StrapIssue(java.lang.String path, top.x0a.strapper.ErrorCode code, java.lang.String detail)`

#### Parameters

- `path` (`java.lang.String`)
- `code` (`top.x0a.strapper.ErrorCode`)
- `detail` (`java.lang.String`)

## Methods

### `public top.x0a.strapper.ErrorCode code()`

### `public java.lang.String detail()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `static top.x0a.strapper.StrapIssue of(java.lang.String path, top.x0a.strapper.ErrorCode code)`

#### Parameters

- `path` (`java.lang.String`)
- `code` (`top.x0a.strapper.ErrorCode`)

### `static top.x0a.strapper.StrapIssue of(java.lang.String path, top.x0a.strapper.ErrorCode code, java.lang.String detail)`

#### Parameters

- `path` (`java.lang.String`)
- `code` (`top.x0a.strapper.ErrorCode`)
- `detail` (`java.lang.String`)

### `public java.lang.String path()`

### `public java.lang.String toString()`
