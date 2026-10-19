# top.x0a.strapper.Requirement

**Package:** `top.x0a.strapper`

**Kind:** `enum`

参数项的必填性，对应格式描述开头的 `*` / `!` / 无标记。

## Enum Constants

### `CONDITIONAL` (`top.x0a.strapper.Requirement`)

`!`：根据前后文条件确定是否必填。

<p>spec 明确这类条件"需要在具体情境下具体描述"，格式本身给不出答案，
因此本库把判断权交给调用方：注册 `Strapper.ConditionalResolver` 即可；
不注册时按选填处理。

### `OPTIONAL` (`top.x0a.strapper.Requirement`)

无标记：选填。

### `REQUIRED` (`top.x0a.strapper.Requirement`)

`*`：必填。

## Constructors

### `private Requirement()`

## Methods

### `static top.x0a.strapper.Requirement of(java.lang.String marker)`

#### Parameters

- `marker` (`java.lang.String`)

### `static top.x0a.strapper.Requirement strongest(top.x0a.strapper.Requirement a, top.x0a.strapper.Requirement b)`

key 上的标记与 value 上的标记同时存在时，取更强的一个。

#### Parameters

- `a` (`top.x0a.strapper.Requirement`)
- `b` (`top.x0a.strapper.Requirement`)

### `public static top.x0a.strapper.Requirement valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static top.x0a.strapper.Requirement[] values()`
