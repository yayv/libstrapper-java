# top.x0a.strapper.Strapper.ConditionalResolver

**Package:** `top.x0a.strapper`

**Kind:** `interface`

判定 `!` 标记的参数在当前上下文下到底必不必填。

<p>spec 说这类条件"需要在具体情境下具体描述"，所以判断权在调用方。

## Methods

### `public abstract boolean isRequired(java.lang.String path, java.lang.String keyName, java.util.Map<java.lang.String,java.lang.Object> siblings)`

#### Parameters

- `path` (`java.lang.String`) — 该参数的完整路径，如 `$.data.invoiceTitle`
- `keyName` (`java.lang.String`) — 该参数的 key 名
- `siblings` (`java.util.Map<java.lang.String,java.lang.Object>`) — 同一层已知的原始数据（只读），可据此判断
