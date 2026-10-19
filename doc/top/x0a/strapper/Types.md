# top.x0a.strapper.Types

**Package:** `top.x0a.strapper`

**Kind:** `class`

类型表 —— 本库的扩展点。

<p>类型分两层：
<ol>
  <li><b>基础类型</b>固定 9 个，决定一个值最终被解析成什么（`Kind`）：
      `int`、`float`、`number`、`bool`、
      `string`、`text`、`date`、`time`、`datetime`。</li>
  <li><b>扩展类型</b>就是一张「名 : 正则」的明文表，名字写成
      `<\u57fa\u7840\u7c7b\u578b>.<\u6269\u5c55\u540d>`，点号前面的部分说明这个值按什么语义取。
      一个值要先过基础类型的正则，再过扩展类型的正则。</li>
</ol>

<p>spec 里列的 mobile、idcard、retCode…… 本身就是这么写出来的，内置表见
`top/x0a/strapper/types.txt`。业务领域的类型走完全相同的入口：

<pre>`// \u5171\u4eab\u7535\u52a8\u8f66\u9886\u57df\uff1a\u8f66\u8f86\u7f16\u53f7 9 \u4f4d\u6570\u5b57\uff0c\u524d 4 \u4f4d\u662f\u751f\u4ea7\u6279\u6b21
types.add("int.vehicleNo", "^20\\d{2`\\d{5}$");

// \u6216\u8005\u6574\u5f20\u8868\u4ece\u660e\u6587\u91cc\u8bfb\u8fdb\u6765\uff0c\u8ddf\u63a5\u53e3\u6587\u6863\u653e\u5728\u4e00\u8d77\u7ef4\u62a4
types.load(Path.of("conf/types.txt"));
}</pre>

<p>格式描述里写全名 `"*int.vehicleNo"` 或短名 `"*vehicleNo"` 都可以；
短名在全表内唯一时才能用，重名时必须写全名，避免歧义静默通过。
同名再写一次即为覆盖，不满意内置规则就重写同名一行。

<h2>string 与 text 的分工</h2>
<p>`string` 是纯字符串，不允许出现制表符、换行符这类转义字符；`text` 允许。
内置表里凡是单行短串一律挂在 `string` 下，只有确实可能带换行的才挂到 `text` 下。
这样需要收紧攻击面时，一句 disable("text") 就能把全部可带换行的
类型一次性关掉：

<pre>`Types types = Types.standard().disable("text");   // \u4e4b\u540e\u683c\u5f0f\u63cf\u8ff0\u91cc\u5199 text \u6216 text.* \u4e00\u5f8b\u62a5\u9519`</pre>

## Fields

### `BASE` (`java.util.Map<java.lang.String,top.x0a.strapper.Types.TypeDef>`)

### `BUILTIN_RESOURCE` (`java.lang.String`)

内置扩展类型表所在的资源路径。

### `compiled` (`java.util.Map<java.lang.String,java.util.regex.Pattern>`)

### `disabled` (`java.util.Set<java.lang.String>`)

被禁用的类型：基础类型名或扩展类型全名。

### `extended` (`java.util.Map<java.lang.String,java.lang.String>`)

扩展类型：全名 -> 正则原文，保持写入顺序。

### `shortIndex` (`java.util.Map<java.lang.String,java.util.Set<java.lang.String>>`)

## Constructors

### `private Types()`

## Methods

### `public top.x0a.strapper.Types add(java.lang.String name, java.lang.String regex)`

加一条扩展类型。

#### Parameters

- `name` (`java.lang.String`) — 点分全名，形如 `string.email`；点号前必须是基础类型名
- `regex` (`java.lang.String`) — 该类型的正则；值要同时满足基础类型的正则和它

### `public top.x0a.strapper.Types addAll(java.util.Map<java.lang.String,java.lang.String> entries)`

批量加，键为全名，值为正则。

#### Parameters

- `entries` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public static top.x0a.strapper.Types base()`

只有基础类型，扩展类型从零开始自己写。

### `private static void base(java.lang.String name, top.x0a.strapper.Types.Kind kind, java.lang.String regex)`

#### Parameters

- `name` (`java.lang.String`)
- `kind` (`top.x0a.strapper.Types.Kind`)
- `regex` (`java.lang.String`)

### `private static java.lang.String baseNameOf(java.lang.String fullName)`

#### Parameters

- `fullName` (`java.lang.String`)

### `public static java.util.Set<java.lang.String> baseNames()`

全部基础类型名。

### `private java.lang.String canonical(java.lang.String name)`

把短名归一成可存进 `.disabled` 的规范名；名字不存在时抛异常。

#### Parameters

- `name` (`java.lang.String`)

### `public top.x0a.strapper.Types copy()`

复制一份，之后的改动互不影响。

### `public top.x0a.strapper.Types disable(java.lang.String name)`

禁用一个类型。之后格式描述里再写到它就会报
`ErrorCode.FORMAT_TYPE_DISABLED`。

<p>禁用基础类型会连带禁用从它派生的全部扩展类型 —— `disable("text")`
就是一次性关掉所有可能带换行、制表符的参数类型，需要收紧攻击面时很有用。

<p>名字写错会直接抛异常而不是静默失败：安全相关的开关，悄悄没生效比报错危险得多。

#### Parameters

- `name` (`java.lang.String`) — 基础类型名、扩展类型全名或唯一短名

### `public java.util.Set<java.lang.String> disabledTypes()`

当前被显式禁用的类型名。

### `public top.x0a.strapper.Types enable(java.lang.String name)`

解除禁用。

#### Parameters

- `name` (`java.lang.String`)

### `public java.util.Map<java.lang.String,java.lang.String> extendedTypes()`

全部扩展类型：全名 -> 正则原文。

### `private java.lang.String fullName(java.lang.String name)`

名字 -> 规范全名；基础类型返回自身，扩展类型返回全名，解析不出来返回 `null`。

#### Parameters

- `name` (`java.lang.String`)

### `public boolean has(java.lang.String name)`

是否可解析。

#### Parameters

- `name` (`java.lang.String`)

### `public boolean isDisabled(java.lang.String name)`

该类型是不是被禁用了（基础类型被禁用时，从它派生的扩展类型也算被禁用）。

#### Parameters

- `name` (`java.lang.String`)

### `public top.x0a.strapper.Types load(java.lang.String text)`

从明文读入一张类型表。每行一条 `\u540d : \u6b63\u5219`，
`#` 开头的行是注释，空行忽略；名字与正则之间用第一个 `:` 或 `=` 分隔。

#### Parameters

- `text` (`java.lang.String`)

### `public top.x0a.strapper.Types load(java.nio.file.Path file)`

从文件读入一张类型表，UTF-8 编码。

#### Parameters

- `file` (`java.nio.file.Path`)

### `public static java.lang.String match(top.x0a.strapper.Types.TypeDef def, java.lang.String raw)`

先过基础类型的正则，再过扩展类型的正则。

#### Parameters

- `def` (`top.x0a.strapper.Types.TypeDef`)
- `raw` (`java.lang.String`)

#### Returns

通过返回 `null`，否则返回失败原因

### `static java.lang.String matchBase(top.x0a.strapper.Types.TypeDef def, java.lang.String raw)`

只过基础类型的正则，用于在取值转换前先确认形态。

#### Parameters

- `def` (`top.x0a.strapper.Types.TypeDef`)
- `raw` (`java.lang.String`)

### `public top.x0a.strapper.Types.TypeDef resolve(java.lang.String name)`

按基础类型名、扩展类型全名或唯一短名查找。
找不到、短名有歧义、或该类型已被禁用时返回 `null`。

#### Parameters

- `name` (`java.lang.String`)

### `public java.lang.String resolveProblem(java.lang.String name)`

`.resolve` 失败的原因，用于把「未定义」和「短名有歧义」区分开。

#### Parameters

- `name` (`java.lang.String`)

### `public static top.x0a.strapper.Types standard()`

基础类型 + 内置扩展类型表（spec 中列出的全部扩展类型）。

### `public java.lang.String text()`

把当前扩展类型表导出成明文，可直接落盘或打印。
