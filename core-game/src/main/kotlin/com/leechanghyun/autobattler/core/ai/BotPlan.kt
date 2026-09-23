package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.synergy.TraitTally

/**
 * 봇이 이번 라운드에 밀 방향. 명세서 4-7, 로드맵 8단계.
 *
 * 명세서 4-7:
 * > 매 라운드 **시작 시** AI는 자신의 보드를 스캔해 "가장 많이 보유한 태그 2개"를 우선 시너지로
 * > **고정**하고, 그 태그에 해당하는 유닛의 가중치를 높인다.
 *
 * ### "고정"이 이 클래스의 존재 이유다
 * 한 장 살 때마다 우선 태그를 다시 계산하면 목표가 매번 흔들려 "방향성"이라는 개념 자체가 사라진다.
 * 그래서 라운드 시작에 한 번 만들어 구매 루프가 끝날 때까지 **다시 만들지 않는 값 객체**로 뒀다.
 * `BotPlanTest.우선 태그는 라운드 중간에 바뀌지 않는다` 가 이 규칙을 지킨다.
 *
 * ### 동점 처리
 * 인원이 같은 태그가 여럿이면 [MasterData.traits] 순서(계열 4종 → 직업 4종)로 끊는다.
 * [TraitTally.countsByTraitId] 맵을 직접 순회하면 해시 순서가 결과에 새어 들어가 결정론이 깨진다.
 * 그래서 **맵을 순회하지 않고 마스터 데이터 목록을 순회한다.**
 *
 * ### 계획은 방향을 이끌지 않고 따라간다 — 11단계 재검토 항목
 * 이 계획은 **보드에서 파생된다.** 보드가 바뀌면 계획도 따라 바뀌므로, 봇이 한 방향을 붙잡고
 * 버티는 일은 일어나지 않는다. 8인 로비 20라운드에서 마지막 5라운드의 1순위 태그를 세어 보니
 * 스코어링 봇 35명 중 18명만 한 태그를 유지했고, 오히려 대조군(왼쪽부터 사는 봇)이 26명으로
 * **더 안정적**이었다. 스코어링이 임계값을 깨러 더 자주 갈아타기 때문이다.
 *
 * 그렇다고 "한 번 잡은 태그는 유지" 같은 규칙을 지금 넣지는 않았다. 갈아타기가 이득인지 손해인지는
 * 전투를 붙여야 잴 수 있는데 8단계 로비는 전투를 돌리지 않는다. 잴 수 없는 규칙은 넣지 않는다는
 * 이 저장소의 관행대로, 11단계 밸런스 튜닝으로 넘긴다.
 */
data class BotPlan(
    /** 우선 시너지 태그. 최대 [PRIORITY_TRAIT_COUNT] 개. 보드가 비어 있으면 빈 목록이다. */
    val priorityTraitIds: List<String>,
    /**
     * 초반인지. 명세서 4-7 "보드가 비어있는 초반(1~3라운드)은 태그 무관하게
     * 코스트 대비 스탯이 좋은 유닛을 픽하도록 예외 처리".
     */
    val isEarlyGame: Boolean,
) {
    /** [def] 가 우선 태그를 몇 개 가지고 있는지. 0, 1, 2 중 하나다. */
    fun priorityMatchCount(def: UnitDef): Int =
        priorityTraitIds.count { it == def.origin.traitId || it == def.unitClass.traitId }

    companion object {
        /** 명세서 4-7: "가장 많이 보유한 태그 2개". */
        const val PRIORITY_TRAIT_COUNT = 2

        /** 명세서 4-7: "초반(1~3라운드)". */
        const val EARLY_GAME_LAST_ROUND = 3

        /**
         * 라운드 시작 시점의 보드를 스캔해 계획을 세운다.
         *
         * 명세서의 초반 예외는 "보드가 비어있는 초반(1~3라운드)"이라 두 조건을 **모두** 요구한다.
         * 라운드가 3 이하여도 보드에 유닛이 있으면 이미 방향이 생긴 것이므로 태그를 본다.
         */
        fun of(player: PlayerState, round: Int): BotPlan {
            val tally = TraitTally.of(player.board)

            // 맵이 아니라 마스터 데이터 목록을 순회한다. 해시 순서가 결과에 섞이지 않게 하기 위해서다.
            val priority = MasterData.traits
                .map { it.id to tally.count(it.id) }
                .filter { (_, count) -> count > 0 }
                .sortedByDescending { (_, count) -> count }
                .take(PRIORITY_TRAIT_COUNT)
                .map { (id, _) -> id }

            return BotPlan(
                priorityTraitIds = priority,
                isEarlyGame = round <= EARLY_GAME_LAST_ROUND && player.board.isEmpty(),
            )
        }
    }
}
