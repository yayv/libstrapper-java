# top.x0a.json.JsonWriter.Stream

**Package:** `top.x0a.json`

**Kind:** `class`

流式写出器。不必先攒一棵 Map / List 树，适合结果集很大或者边查边写的场景。

<p><b>线程封闭</b>：一个实例只能由创建它的线程使用。别的线程一碰就抛
`IllegalStateException`，不会让你拿到一段悄悄写坏的 JSON。
确实需要换线程接着写时，先由当前线程调 `.handOff()`。

<p>注意目标 `Appendable` 也必须是这个线程独占的，原因见 `JsonWriter` 的线程安全一节。

## Fields

### `closed` (`boolean`)

### `empty` (`java.util.List<java.lang.Boolean>`)

当前层是不是还一个元素都没写。

### `expectingValue` (`boolean`)

object 里是不是刚写完 name、正等着 value。

### `out` (`java.lang.Appendable`)

### `owner` (`java.util.concurrent.atomic.AtomicReference<java.lang.Thread>`)

当前归属线程；`null` 表示已交接、等待下一个线程认领。

### `rootWritten` (`boolean`)

### `stack` (`java.util.List<java.lang.Boolean>`)

每一层是不是 object；true 为 object，false 为 array。

### `writer` (`top.x0a.json.JsonWriter`)

## Constructors

### `private Stream(top.x0a.json.JsonWriter writer, java.lang.Appendable out)`

#### Parameters

- `writer` (`top.x0a.json.JsonWriter`)
- `out` (`java.lang.Appendable`)

## Methods

### `private void afterValue()`

### `private top.x0a.json.JsonWriter.Stream begin(char bracket, boolean object)`

#### Parameters

- `bracket` (`char`)
- `object` (`boolean`)

### `public top.x0a.json.JsonWriter.Stream beginArray()`

开始一个数组。

### `public top.x0a.json.JsonWriter.Stream beginObject()`

开始一个对象。

### `private void checkOwner()`

### `public void close()`

收尾。检查括号是否都闭合了；`out` 如果是 `java.io.Closeable` 本身，
这里不会去关它 —— 谁开的谁负责关。

### `private top.x0a.json.JsonWriter.Stream end(char bracket, boolean object)`

#### Parameters

- `bracket` (`char`)
- `object` (`boolean`)

### `public top.x0a.json.JsonWriter.Stream endArray()`

结束当前数组。

### `public top.x0a.json.JsonWriter.Stream endObject()`

结束当前对象。

### `public top.x0a.json.JsonWriter.Stream field(java.lang.String name, java.lang.Object value)`

`.name(String)` + `.value(Object)`，对象里最常用的写法。

#### Parameters

- `name` (`java.lang.String`)
- `value` (`java.lang.Object`)

### `public top.x0a.json.JsonWriter.Stream handOff()`

交出归属，让另一个线程接着写。

<p>必须由当前归属线程调用。交接是通过一次 volatile 写完成的，所以接手的线程
一定能看到前面写进去的全部内容 —— 这是真正的 happens-before 保证，不只是放行检查。

<p>目标 `Appendable` 的可见性得你自己保证：它也要跟着一起交接，
不能两个线程同时拿着它。

### `private void io(top.x0a.json.JsonWriter.Stream.IoAction action)`

#### Parameters

- `action` (`top.x0a.json.JsonWriter.Stream.IoAction`)

### `public top.x0a.json.JsonWriter.Stream name(java.lang.String name)`

写一个 key，之后必须紧跟一个值。

#### Parameters

- `name` (`java.lang.String`)

### `public top.x0a.json.JsonWriter.Stream nullValue()`

写一个 JSON null。

### `public java.lang.Thread owner()`

当前归属线程；已交接、还没人认领时返回 `null`。

### `private void separator()`

写下一个元素之前：补逗号、补换行缩进。

### `public top.x0a.json.JsonWriter.Stream value(java.lang.Object value)`

写一个值。对象、数组、纯量都行，会按 `JsonWriter` 的规则整棵写出去。

#### Parameters

- `value` (`java.lang.Object`)
