# top.x0a.strapper.JsonReader

**Package:** `top.x0a.strapper`

**Kind:** `interface`

JSON 解析的插拔点。

<p>本库只在两处需要 JSON 解析（`Strapper.strap` 和 `Strapper.checkFormat` 各一次），
所以这个接口只有一个方法。默认用内置的 `Json`，想换成项目里已经在用的第三方库就传一个适配器进来，
这样一个进程里不必同时驮着两套 JSON 解析实现。

<h2>约定</h2>
<p>`.read` 必须把 JSON 文本解析成这样一棵通用树：
<ul>
  <li>object &rarr; `Map`，<b>并且保持 key 的书写顺序</b>（校验结果按这个顺序产出）</li>
  <li>array &rarr; `List`</li>
  <li>string &rarr; `String`，true/false &rarr; `Boolean`，null &rarr; `null`</li>
  <li>number &rarr; 任意 `Number`（`Integer` / `Long` / `Double` /
      `BigDecimal` 都行，本库按它的字符串形式做校验）</li>
</ul>
<p>解析失败时抛 `JsonReadException`；用 `.wrapping` 包一层会自动完成转换。

<h2>写第三方适配器</h2>
<pre>`ObjectMapper mapper = new ObjectMapper();
strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));   // Jackson

Gson gson = new Gson();
strapper.jsonReader(JsonReader.wrapping(t -> gson.fromJson(t, Object.class)));      // Gson

strapper.jsonReader(JsonReader.wrapping(JSON::parse));                              // fastjson2`</pre>

<h2>换之前要知道的三件事</h2>
<ol>
  <li><b>Gson 把所有数字都解析成 `Double`</b>，超过 2^53 的整数会丢精度：
      `1234567890123456789` 会变成 `1234567890123456800`，
      校验照样能过，值已经错了。ID 类字段用 Gson 后端时要留意。</li>
  <li><b>各家的宽松度不同</b>：Jackson 默认接受文档尾部的多余内容，
      Gson 和 fastjson2 默认接受单引号，fastjson2 还接受尾逗号，
      Gson 和 fastjson2 对空串返回 `null` 而不是报错。
      内置的 `Json` 这些一律拒绝 —— 换后端等于放松了这一层把关。</li>
  <li><b>只有读是可换的，写不换</b>：`StrapResult.toJson()` 始终用内置的写出实现，
      因为它写的是本库自己产出的 Map/List，不涉及第三方的对象模型。</li>
</ol>

## Methods

### `public static top.x0a.strapper.JsonReader builtin()`

内置实现，零第三方依赖，严格按 RFC 8259 解析。这是默认值。

### `public abstract java.lang.Object read(java.lang.String text)`

把 JSON 文本解析成 Map / List / String / Number / Boolean / null 构成的树。

#### Parameters

- `text` (`java.lang.String`)

#### Throws

- `JsonReadException` — 文本不是合法 JSON

### `public static top.x0a.strapper.JsonReader wrapping(top.x0a.strapper.JsonReader.Parser parser)`

把任意第三方解析器包成 `JsonReader`：它自己抛什么异常都会被转成
`JsonReadException`，本库只认这一种。

#### Parameters

- `parser` (`top.x0a.strapper.JsonReader.Parser`)
