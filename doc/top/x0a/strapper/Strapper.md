# top.x0a.strapper.Strapper

**Package:** `top.x0a.strapper`

**Kind:** `class`

WEB API 参数契约校验器 —— libstrapper 的 Java 实现。

<p>用一段 JSON 形态的"格式描述"去校验另一段 JSON 形态的"参数数据"，产出归一化后的取值与问题清单。
格式描述的语法见 spec 的 valueFormat.md 与 dataStruct.md。

<h2>最简用法</h2>
<pre>`String format = """
    {
      "username":"*string:32//\u7528\u6237\u540d",
      "age":"int[0,150]#0//\u5e74\u9f84",
      "gender":"string{\u7537,\u5973`//\u6027\u522b\u53ef\u4ee5\u4e0d\u586b"
    }""";

StrapResult result = new Strapper().strap(format, requestBody);
if (!result.ok()) {
    return fail(result.report());
}
String username = (String) result.asMap().get("username");
}</pre>

<h2>扩展自己领域的类型</h2>
<p>扩展类型就是一张「名 : 正则」的明文表，内置的 mobile、idcard 等只是预先写好的几行，
业务领域的类型走完全相同的入口：
<pre>`Strapper strapper = new Strapper()
        // \u5171\u4eab\u7535\u52a8\u8f66\u9886\u57df\uff1a\u8f66\u8f86\u7f16\u53f7 9 \u4f4d\u6570\u5b57\uff0c\u524d 4 \u4f4d\u662f\u751f\u4ea7\u6279\u6b21
        .addType("int.vehicleNo", "^20\\d{2`\\d{5}$")
        // \u4e5f\u53ef\u4ee5\u6574\u5f20\u8868\u653e\u5728\u63a5\u53e3\u6587\u6863\u65c1\u8fb9\u4e00\u8d77\u7ef4\u62a4
        .loadTypes(Path.of("conf/types.txt"));
// \u683c\u5f0f\u63cf\u8ff0\u91cc\u5199 "*vehicleNo//\u8f66\u8f86\u7f16\u53f7" \u6216 "*int.vehicleNo//\u8f66\u8f86\u7f16\u53f7"
}</pre>

<h2>换掉内置的 JSON 解析</h2>
<p>项目里已经在用 Jackson / Gson / fastjson2 时，不必再驮一套解析实现：
<pre>`strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));`</pre>
具体约定和几个要当心的差异见 `JsonReader`。

<h2>线程安全</h2>
<p>配置完成后 `.strap` 不改变实例状态（每次调用的状态都在局部），可以在多线程间共享。

<p>但配置字段是非 `final` 的，`Types` 内部也是可变的 map，所以配置必须发生在
"发布给其他线程之前"：
<pre>`// \u5b89\u5168\uff1a\u9759\u6001\u521d\u59cb\u5316\u6709 happens-before \u4fdd\u8bc1
static final Strapper STRAPPER = new Strapper().addType(...).disableType("text");

// \u5371\u9669\uff1a\u5df2\u7ecf\u5728\u8dd1\u8bf7\u6c42\u4e86\u624d\u6539\u914d\u7f6e\uff0c\u522b\u7684\u7ebf\u7a0b\u53ef\u80fd\u770b\u5230\u534a\u914d\u597d\u7684\u72b6\u6001
STRAPPER.disableType("text");`</pre>

## Fields

### `ALWAYS_OPTIONAL` (`top.x0a.strapper.Strapper.ConditionalResolver`)

### `conditionalResolver` (`top.x0a.strapper.Strapper.ConditionalResolver`)

### `jsonReader` (`top.x0a.strapper.JsonReader`)

### `types` (`top.x0a.strapper.Types`)

### `unknownTypePolicy` (`top.x0a.strapper.Strapper.UnknownTypePolicy`)

## Constructors

### `public Strapper()`

## Methods

### `public top.x0a.strapper.Strapper addType(java.lang.String name, java.lang.String regex)`

加一条扩展类型。

#### Parameters

- `name` (`java.lang.String`) — 点分全名，形如 `int.vehicleNo`；点号前必须是基础类型名
- `regex` (`java.lang.String`) — 该类型的正则

### `public top.x0a.strapper.Strapper addTypes(java.util.Map<java.lang.String,java.lang.String> entries)`

批量加扩展类型，键为全名，值为正则。

#### Parameters

- `entries` (`java.util.Map<java.lang.String,java.lang.String>`)

### `private static java.util.Map<java.lang.String,java.lang.Object> asStringKeyMap(java.util.Map<?,?> map)`

#### Parameters

- `map` (`java.util.Map<?,?>`)

### `public top.x0a.strapper.StrapResult checkFormat(java.lang.String format)`

只检查格式描述本身写得对不对，不需要数据。适合在接口文档的 CI 里跑。

#### Parameters

- `format` (`java.lang.String`)

#### Returns

`StrapResult.value()` 为解析后的格式树

### `private void checkFormatArray(java.util.List<?> list, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `list` (`java.util.List<?>`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private void checkFormatNode(java.lang.Object node, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `node` (`java.lang.Object`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private void checkFormatObject(java.util.Map<?,?> map, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `map` (`java.util.Map<?,?>`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private void checkFormatString(java.lang.String source, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `source` (`java.lang.String`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private java.lang.Object checkValue(java.lang.String formatString, java.lang.Object data, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `formatString` (`java.lang.String`)
- `data` (`java.lang.Object`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private boolean checkWildcardKeyName(top.x0a.strapper.KeyFormat wildcard, java.lang.String name, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `wildcard` (`top.x0a.strapper.KeyFormat`)
- `name` (`java.lang.String`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private static java.lang.String child(java.lang.String path, java.lang.String name)`

#### Parameters

- `path` (`java.lang.String`)
- `name` (`java.lang.String`)

### `private static java.lang.Object coerceQuietly(top.x0a.strapper.Types.TypeDef def, java.lang.String raw)`

#### Parameters

- `def` (`top.x0a.strapper.Types.TypeDef`)
- `raw` (`java.lang.String`)

### `public top.x0a.strapper.Strapper conditionalResolver(top.x0a.strapper.Strapper.ConditionalResolver resolver)`

设置 `!` 条件必填的判定逻辑。

#### Parameters

- `resolver` (`top.x0a.strapper.Strapper.ConditionalResolver`)

### `private java.util.Optional<java.lang.Object> defaultValue(top.x0a.strapper.ValueFormat vf, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

默认值本身也要满足格式；不满足说明格式描述写错了，是开发期问题。

#### Parameters

- `vf` (`top.x0a.strapper.ValueFormat`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `public top.x0a.strapper.Strapper disableType(java.lang.String name)`

禁用一个类型，之后格式描述里写到它就会报错。

<p>禁用基础类型会连带禁用从它派生的扩展类型。典型用法是
`disableType("text")`：不接受任何可能带制表符、换行符的参数，收紧攻击面。

#### Parameters

- `name` (`java.lang.String`)

### `public top.x0a.strapper.Strapper enableType(java.lang.String name)`

解除禁用。

#### Parameters

- `name` (`java.lang.String`)

### `private java.lang.String evaluate(top.x0a.strapper.ValueFormat vf, top.x0a.strapper.Types.TypeDef def, java.lang.String raw, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

依次做长度、格式、范围三项检查。

#### Parameters

- `vf` (`top.x0a.strapper.ValueFormat`)
- `def` (`top.x0a.strapper.Types.TypeDef`)
- `raw` (`java.lang.String`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

#### Returns

通过返回 `null`，否则返回失败原因

### `private static top.x0a.strapper.ErrorCode failureCode(java.lang.String failure)`

#### Parameters

- `failure` (`java.lang.String`)

### `private void handleMissing(top.x0a.strapper.KeyFormat key, java.lang.Object fmtValue, java.lang.String name, java.lang.String childPath, java.util.Map<java.lang.String,java.lang.Object> siblings, java.util.Map<java.lang.String,java.lang.Object> result, top.x0a.strapper.Strapper.Run run)`

缺失的 key：按必填性报错，或用默认值补上。

#### Parameters

- `key` (`top.x0a.strapper.KeyFormat`)
- `fmtValue` (`java.lang.Object`)
- `name` (`java.lang.String`)
- `childPath` (`java.lang.String`)
- `siblings` (`java.util.Map<java.lang.String,java.lang.Object>`)
- `result` (`java.util.Map<java.lang.String,java.lang.Object>`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `public top.x0a.strapper.JsonReader jsonReader()`

当前使用的 JSON 解析器。

### `public top.x0a.strapper.Strapper jsonReader(top.x0a.strapper.JsonReader reader)`

换用另一个 JSON 解析器。默认是内置的零依赖实现。

<p>项目里已经在用 Jackson / Gson / fastjson2 时，包一层传进来就不必驮两套解析实现：
<pre>`strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));`</pre>
换之前请先读 `JsonReader` 上「换之前要知道的三件事」，尤其是 Gson 的数字精度问题。

#### Parameters

- `reader` (`top.x0a.strapper.JsonReader`)

### `public top.x0a.strapper.Strapper loadTypes(java.lang.String text)`

从明文读入一张扩展类型表，格式见 `Types.load(String)`。

#### Parameters

- `text` (`java.lang.String`)

### `public top.x0a.strapper.Strapper loadTypes(java.nio.file.Path file)`

从文件读入一张扩展类型表，UTF-8 编码。

#### Parameters

- `file` (`java.nio.file.Path`)

### `private static java.lang.String rangeCheck(top.x0a.strapper.ValueFormat.Range range, top.x0a.strapper.Types.TypeDef def, java.lang.String raw, java.lang.Object value)`

#### Parameters

- `range` (`top.x0a.strapper.ValueFormat.Range`)
- `def` (`top.x0a.strapper.Types.TypeDef`)
- `raw` (`java.lang.String`)
- `value` (`java.lang.Object`)

### `private java.lang.Object readFormat(java.lang.String format, top.x0a.strapper.Strapper.Run run)`

解析格式描述文本；失败时把问题记进 run 并返回 null。

#### Parameters

- `format` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private top.x0a.strapper.ErrorCode readerErrorCode(top.x0a.strapper.ErrorCode builtinCode, top.x0a.strapper.ErrorCode pluggedCode)`

内置解析器只会因 JSON 语法失败，所以报语法错误码；换了解析器之后失败原因本库无从知道
（语法错？适配器自己的策略把它挡了？），所以报另一个码，真实原因放在 detail 里。

#### Parameters

- `builtinCode` (`top.x0a.strapper.ErrorCode`)
- `pluggedCode` (`top.x0a.strapper.ErrorCode`)

### `public top.x0a.strapper.StrapResult strap(java.lang.String format, java.lang.String json)`

用格式描述校验一段 JSON 数据。

#### Parameters

- `format` (`java.lang.String`) — 格式描述；空串按 `"{`"} 处理（dataStruct 特殊情况一）
- `json` (`java.lang.String`) — 待校验的 JSON 文本

### `private static java.lang.String typeOf(java.lang.Object data)`

#### Parameters

- `data` (`java.lang.Object`)

### `public top.x0a.strapper.Types types()`

当前的类型表，可直接在上面继续 `add`。

### `public top.x0a.strapper.Strapper types(top.x0a.strapper.Types types)`

换用另一张类型表。

#### Parameters

- `types` (`top.x0a.strapper.Types`)

### `public top.x0a.strapper.Strapper unknownTypePolicy(top.x0a.strapper.Strapper.UnknownTypePolicy policy)`

设置未定义类型的处理策略。

#### Parameters

- `policy` (`top.x0a.strapper.Strapper.UnknownTypePolicy`)

### `private java.lang.Object walk(java.lang.Object formatNode, java.lang.Object data, java.lang.String path, top.x0a.strapper.KeyFormat key, top.x0a.strapper.Strapper.Run run)`

按格式节点的种类分发。`key` 仅在值为数组时用于取长度约定。

#### Parameters

- `formatNode` (`java.lang.Object`)
- `data` (`java.lang.Object`)
- `path` (`java.lang.String`)
- `key` (`top.x0a.strapper.KeyFormat`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private java.util.List<java.lang.Object> walkArray(java.util.List<?> format, java.util.List<?> data, java.lang.String path, top.x0a.strapper.KeyFormat key, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `format` (`java.util.List<?>`)
- `data` (`java.util.List<?>`)
- `path` (`java.lang.String`)
- `key` (`top.x0a.strapper.KeyFormat`)
- `run` (`top.x0a.strapper.Strapper.Run`)

### `private java.util.Map<java.lang.String,java.lang.Object> walkObject(java.util.Map<?,?> format, java.util.Map<?,?> data, java.lang.String path, top.x0a.strapper.Strapper.Run run)`

#### Parameters

- `format` (`java.util.Map<?,?>`)
- `data` (`java.util.Map<?,?>`)
- `path` (`java.lang.String`)
- `run` (`top.x0a.strapper.Strapper.Run`)
