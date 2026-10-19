# top.x0a.json.JsonWriter

**Package:** `top.x0a.json`

**Kind:** `class`

把 key-value 结构写成 JSON 串。零第三方依赖，不依赖 `top.x0a.strapper`。

<p>两种用法：

<h2>一、已经有一棵 Map / List 树</h2>
<pre>`Map<String, Object> body = new LinkedHashMap<>();
body.put("code", "ok");
body.put("data", List.of(1, 2, 3));

String json = JsonWriter.compact().write(body);
JsonWriter.pretty().writeTo(out, body);   // out \u662f\u4efb\u610f Appendable\uff1aWriter\u3001StringBuilder\u2026\u2026`</pre>

<h2>二、流式拼，不必先攒一棵树</h2>
<pre>`// out \u662f\u8fd9\u6b21\u8f93\u51fa\u4e13\u5c5e\u7684 Writer / StringBuilder\uff0c\u4e0d\u4e0e\u522b\u7684\u7ebf\u7a0b\u5171\u4eab
JsonWriter.compact().stream(out, json -> {
    json.beginObject()
        .field("code", "ok")
        .name("data").beginArray();
    for (Order order : orders) {
        json.beginObject()
            .field("id", order.id())
            .field("amount", order.amount())
            .endObject();`
    json.endArray().endObject();
});
}</pre>
适合结果集很大或者边查边写的场景 —— 整个过程不会在内存里出现完整的结果串。

<h2>能写哪些类型</h2>
<ul>
  <li>`null`、`CharSequence`、`Character`、`Boolean`、`Number`</li>
  <li>`Map` &rarr; object（key 一律按 `String.valueOf` 取字符串）</li>
  <li>`Iterable`、Java 数组（含基本类型数组）&rarr; array</li>
  <li>`Enum` &rarr; 它的 `name()`</li>
  <li>其他类型 &rarr; 退化成它的 `toString()`，当字符串写。
      想让这种情况直接报错，用 strict(true)</li>
</ul>

<h2>线程安全</h2>
<p>`JsonWriter` 本身不可变（字段全 `final` 的 `final` 类），
配置方法返回新实例，可以做成常量在多线程间共享，不需要任何同步。

<p>`Stream` 则是<b>线程封闭</b>的：它有写到一半的状态，只能由创建它的线程使用。
这不是靠约定 —— 别的线程一碰就会抛 `IllegalStateException`
（确实需要跨线程接力时用 `Stream.handOff()`）。

<p>还有一点容易漏：<b>目标 `Appendable` 也必须是这个线程独占的</b>。
两个线程各拿各的 `Stream` 往同一个 `StringBuilder` 里写，一样会写坏 ——
`StringBuilder` 自己就不是线程安全的，内容会交错甚至丢字符。
多个线程要往同一个目标汇聚时，正确做法是各自在本地拼完整，再整篇一次性追加：
<pre>`String doc = JsonWriter.compact().write(payload);      // \u672c\u5730\u62fc\u5b8c\uff0c\u4e92\u4e0d\u5e72\u6270
synchronized (sharedOut) { sharedOut.append(doc);`    // \u52a0\u9501\u7684\u7c92\u5ea6\u662f\u4e00\u6574\u7bc7\u6587\u6863
}</pre>
加锁粒度必须是整篇文档，不能是单个方法 —— JSON 的原子单位是文档，不是一次写入。

## Fields

### `COMPACT` (`top.x0a.json.JsonWriter`)

### `escapeNonAscii` (`boolean`)

### `indent` (`int`)

缩进宽度，0 表示不换行不缩进。

### `skipNulls` (`boolean`)

### `strict` (`boolean`)

## Constructors

### `private JsonWriter(int indent, boolean escapeNonAscii, boolean skipNulls, boolean strict)`

#### Parameters

- `indent` (`int`)
- `escapeNonAscii` (`boolean`)
- `skipNulls` (`boolean`)
- `strict` (`boolean`)

## Methods

### `private static java.util.List<java.lang.Object> arrayToList(java.lang.Object array)`

#### Parameters

- `array` (`java.lang.Object`)

### `public static top.x0a.json.JsonWriter compact()`

紧凑输出，不换行。接口响应用这个。

### `public top.x0a.json.JsonWriter escapeNonAscii(boolean on)`

把非 ASCII 字符写成 `\\uXXXX`。

<p>输出全是 ASCII，就不会再因为响应头里的 charset 写错而乱码；代价是中文内容体积翻几倍。

#### Parameters

- `on` (`boolean`)

### `private void newline(java.lang.Appendable out, int depth)`

#### Parameters

- `out` (`java.lang.Appendable`)
- `depth` (`int`)

### `public static java.lang.String number(double d)`

`double` 的 JSON 形式，不带指数。

#### Parameters

- `d` (`double`)

### `public static java.lang.String number(java.lang.Number n)`

数字的 JSON 形式。

<p>`double` / `float` 写成不带指数的形式，避免 `1.0E10` 这种
对前端不友好的输出；NaN 和 Infinity 不是合法 JSON，写成 `null`。

#### Parameters

- `n` (`java.lang.Number`)

### `public static top.x0a.json.JsonWriter pretty()`

缩进 2 空格的输出，给人看的。

### `public static top.x0a.json.JsonWriter pretty(int spaces)`

指定缩进宽度；传 0 等同于 `.compact()`。

#### Parameters

- `spaces` (`int`)

### `public top.x0a.json.JsonWriter skipNulls(boolean on)`

对象里值为 `null` 的 key 直接不写出来。数组里的 null 不受影响。

#### Parameters

- `on` (`boolean`)

### `public top.x0a.json.JsonWriter.Stream stream(java.lang.Appendable out)`

开一个流式写出器，边拼边往 `out` 里写。

<p>用完要 `Stream.close()`（配合 try-with-resources），close 时会检查括号是否都闭合了。
需要把写出过程拆散到几个方法里时用它，否则优先用
java.util.function.Consumer) —— 那个形式不会让 `Stream` 泄漏出去。

#### Parameters

- `out` (`java.lang.Appendable`) — 这次输出专属的目标，不能与别的线程共享

### `public void stream(java.lang.Appendable out, java.util.function.Consumer<top.x0a.json.JsonWriter.Stream> body)`

边拼边往 `out` 里写，写完自动收尾。

<p>推荐用这个而不是 `.stream(Appendable)`：`Stream` 出不了这个 lambda，
也就没机会被别的线程拿到，线程封闭是结构上保证的。

#### Parameters

- `out` (`java.lang.Appendable`) — 这次输出专属的目标，不能与别的线程共享
- `body` (`java.util.function.Consumer<top.x0a.json.JsonWriter.Stream>`) — 往 `Stream` 里拼内容；返回时括号必须都闭合了

### `public java.lang.String stream(java.util.function.Consumer<top.x0a.json.JsonWriter.Stream> body)`

拼进一个自己管的缓冲区，返回结果串。连目标 `Appendable` 都不用操心。

#### Parameters

- `body` (`java.util.function.Consumer<top.x0a.json.JsonWriter.Stream>`) — 往 `Stream` 里拼内容；返回时括号必须都闭合了

### `public top.x0a.json.JsonWriter strict(boolean on)`

遇到不认识的类型时直接抛 `IllegalArgumentException`，而不是退化成 `toString()`。

<p>输出的是接口响应时建议打开：一个本该是数字的字段悄悄变成了对象的 toString，
比当场报错难查得多。

#### Parameters

- `on` (`boolean`)

### `private static java.lang.String unicodeEscape(char c)`

#### Parameters

- `c` (`char`)

### `public java.lang.String write(java.lang.Object value)`

写成字符串。

#### Parameters

- `value` (`java.lang.Object`)

### `private void writeArray(java.lang.Appendable out, java.lang.Iterable<?> items, int depth)`

#### Parameters

- `out` (`java.lang.Appendable`)
- `items` (`java.lang.Iterable<?>`)
- `depth` (`int`)

### `private void writeObject(java.lang.Appendable out, java.util.Map<?,?> map, int depth)`

#### Parameters

- `out` (`java.lang.Appendable`)
- `map` (`java.util.Map<?,?>`)
- `depth` (`int`)

### `private void writeString(java.lang.Appendable out, java.lang.CharSequence s)`

#### Parameters

- `out` (`java.lang.Appendable`)
- `s` (`java.lang.CharSequence`)

### `public void writeTo(java.lang.Appendable out, java.lang.Object value)`

直接写进 `Appendable`（`Writer`、`StringBuilder` 都可以），不攒中间串。

#### Parameters

- `out` (`java.lang.Appendable`)
- `value` (`java.lang.Object`)

### `private void writeValue(java.lang.Appendable out, java.lang.Object value, int depth)`

#### Parameters

- `out` (`java.lang.Appendable`)
- `value` (`java.lang.Object`)
- `depth` (`int`)
