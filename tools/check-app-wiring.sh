#!/usr/bin/env bash
# app 모듈을 컴파일하지 않고도 잡을 수 있는 연결 실수를 기계적으로 확인한다.
#
# 이 저장소를 만든 환경에서는 dl.google.com 이 막혀 있어 안드로이드 SDK 와 AndroidX 를
# 받을 수 없다. 그래서 :app 은 한 번도 컴파일된 적이 없고, core-game 의 공개 API 를 바꾸면
# app 쪽 호출부가 어긋나도 아무도 모른다. 여기서 보는 셋은 그중 실제로 겪은 것들이다.
#
# 사용법: bash tools/check-app-wiring.sh
set -u
cd "$(dirname "$0")/.."
fail=0

say() { printf '%s\n' "$*"; }
bad() { fail=1; say "FAIL  $*"; }
ok()  { say "ok    $*"; }

PLANNING=core-game/src/main/kotlin/com/leechanghyun/autobattler/core/planning/PlanningSession.kt
VIEWMODEL=app/src/main/kotlin/com/leechanghyun/autobattler/ui/shop/ShopViewModel.kt
SCREEN=app/src/main/kotlin/com/leechanghyun/autobattler/ui/shop/ShopScreen.kt

# 1. PlanningError 의 값과 toMessage 의 분기가 1:1 인가.
#    toMessage 는 else 없는 when 이라 값이 하나 늘면 :app 이 컴파일되지 않는다.
enum_vals=$(sed -n '/^enum class PlanningError/,/^}/p' "$PLANNING" | grep -oE '^    [A-Z_]+,' | tr -d ' ,' | sort)
branches=$(grep -oE 'PlanningError\.[A-Z_]+ ->' "$VIEWMODEL" | sed 's/PlanningError\.//; s/ ->//' | sort)
if [ "$enum_vals" = "$branches" ]; then
  ok "PlanningError $(printf '%s\n' "$enum_vals" | wc -l | tr -d ' ') 종이 전부 toMessage 에 있다"
else
  bad "PlanningError 와 toMessage 가 어긋났다"
  diff <(printf '%s\n' "$enum_vals") <(printf '%s\n' "$branches") | sed 's/^/      /'
fi

# 2. ShopScreen 의 파라미터 수와 호출부(ShopRoute, Preview)의 인자 수가 같은가.
params=$(sed -n '/^fun ShopScreen(/,/^) {/p' "$SCREEN" | grep -cE '^    [a-zA-Z]+:')
calls=$(grep -cE '^        [a-zA-Z]+ = ' "$SCREEN")   # ShopRoute 의 인자들
previews=$(grep -cE '^            on[A-Za-z]+ = ' "$SCREEN")
route_args=$(sed -n '/^fun ShopRoute(/,/^}/p' "$SCREEN" | grep -cE '^        [a-zA-Z]+ = ')
if [ "$params" -eq "$route_args" ]; then
  ok "ShopScreen 파라미터 $params 개 = ShopRoute 인자 $route_args 개"
else
  bad "ShopScreen 파라미터 $params 개 != ShopRoute 인자 $route_args 개"
fi
preview_args=$(sed -n '/private fun ShopScreenPreview/,/^}/p' "$SCREEN" | grep -cE '^            [a-zA-Z]+ = ')
if [ "$preview_args" -ge "$params" ]; then
  ok "Preview 인자 $preview_args 개가 파라미터 $params 개를 덮는다"
else
  bad "Preview 인자 $preview_args 개 < 파라미터 $params 개"
fi

# 3. app 이 읽는 core-game 상수가 internal 이 아닌가. internal 은 :app 에서 보이지 않는다.
for ref in $(grep -ohE '\bEconomyRules\.[A-Z_]+' -r app/src | sed 's/EconomyRules\.//' | sort -u); do
  decl=$(grep -nE "(const )?val $ref\b" core-game/src/main/kotlin/com/leechanghyun/autobattler/core/masterdata/EconomyTables.kt)
  if [ -z "$decl" ]; then
    bad "EconomyRules.$ref 선언을 찾지 못했다"
  elif printf '%s' "$decl" | grep -q 'internal'; then
    bad "EconomyRules.$ref 가 internal 이라 :app 에서 보이지 않는다"
  else
    ok "EconomyRules.$ref 가 :app 에 공개돼 있다"
  fi
done

# 4. app 이 부르는 PlanningSession 멤버가 실제로 있는가.
for member in $(grep -ohE 'session\.[a-zA-Z]+' "$VIEWMODEL" | sed 's/session\.//' | sort -u); do
  if grep -qE "(fun|val|var) $member\b" "$PLANNING"; then
    ok "PlanningSession.$member 가 있다"
  else
    bad "PlanningSession.$member 가 없다"
  fi
done

[ "$fail" -eq 0 ] && say "" && say "모두 통과" || { say ""; say "실패가 있다"; }
exit $fail
