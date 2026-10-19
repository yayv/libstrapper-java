# top.x0a.strapper.ErrorCode

**Package:** `top.x0a.strapper`

**Kind:** `enum`

校验过程中可能产生的问题码。

<p>前缀含义：
<ul>
  <li>`FORMAT_*` —— 格式描述本身写错了（开发期问题，应当在接口文档评审时就消灭）</li>
  <li>`DATA_*`   —— 格式描述没问题，是传入的数据不符合约定（运行期问题）</li>
  <li>`TYPE_*`   —— 类型表里找不到可用的类型或校验方法</li>
</ul>

<p>`*_JSON_STRUCT_ERROR` 与 `*_JSON_READER_ERROR` 的区别：
<ul>
  <li>用内置解析器时，失败只可能是 JSON 语法问题，报 `*_JSON_STRUCT_ERROR`</li>
  <li>换了 `JsonReader` 之后，失败原因本库无从知道 —— 可能是语法错，也可能是
      适配器自己的策略把它挡了（比如请求体超限）—— 所以报 `*_JSON_READER_ERROR`，
      真实原因在 `StrapIssue.detail()` 里</li>
</ul>

## Fields

### `message` (`java.lang.String`)

## Enum Constants

### `DATA_ARRAY_LENGTH_ERROR` (`top.x0a.strapper.ErrorCode`)

### `DATA_JSON_READER_ERROR` (`top.x0a.strapper.ErrorCode`)

### `DATA_JSON_STRUCT_ERROR` (`top.x0a.strapper.ErrorCode`)

### `DATA_KEY_NEED_EXIST` (`top.x0a.strapper.ErrorCode`)

### `DATA_NOT_EXIST` (`top.x0a.strapper.ErrorCode`)

### `DATA_NOT_IN_SET_RANGE` (`top.x0a.strapper.ErrorCode`)

### `DATA_NOT_IN_VALID_RANGE` (`top.x0a.strapper.ErrorCode`)

### `DATA_NOT_MATCHED` (`top.x0a.strapper.ErrorCode`)

### `DATA_OVER_LENGTH` (`top.x0a.strapper.ErrorCode`)

### `DATA_UNKNOWN_KEY_ERROR` (`top.x0a.strapper.ErrorCode`)

### `DATA_USE_DEFAULT_VALUE` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_DEFAULT_VALUE_INVALID` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_JSON_READER_ERROR` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_JSON_STRUCT_ERROR` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_KEY_SYNTAX_ERROR` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_NOT_SUPPORT_MULTIFORMAT_ARRAY` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_NOT_SUPPORT_NOFORMAT_ARRAY` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_RANGE_SYNTAX_ERROR` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_SYNTAX_ERROR` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_TYPE_DISABLED` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_UNKNOWN_KEYTYPE_ERROR` (`top.x0a.strapper.ErrorCode`)

### `FORMAT_VALUEFORMAT_SYNTAX_ERROR` (`top.x0a.strapper.ErrorCode`)

### `TYPE_NO_MATCHED` (`top.x0a.strapper.ErrorCode`)

### `TYPE_WITHOUT_METHOD` (`top.x0a.strapper.ErrorCode`)

## Constructors

### `private ErrorCode(java.lang.String message)`

#### Parameters

- `message` (`java.lang.String`)

## Methods

### `public java.lang.String message()`

这个问题码的中文说明。

### `public static top.x0a.strapper.ErrorCode valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static top.x0a.strapper.ErrorCode[] values()`
