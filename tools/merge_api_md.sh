#!/bin/sh
SRC=./doc
OUT=lib/libstrapper-api.md

walk() {
  dir=$1
  for f in "$dir"/*.md; do
    [ -e "$f" ] || continue
    rel=${f#"$SRC"/}          # 去掉 SRC 前缀，得相对路径
    fqn=${rel%.md}            # 去掉 .md 后缀
    fqn=$(printf '%s' "$fqn" | tr '/' '.')   # / 换成 .
    #echo "## $fqn"; echo
    cat "$f"; echo; echo '---'; echo
  done
  for sub in "$dir"/*/; do
    [ -d "$sub" ] || continue
    walk "${sub%/}"
  done
}

{
  echo "# tinyweb API"; echo
  walk "$SRC"
} > "$OUT"
