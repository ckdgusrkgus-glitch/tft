package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit

/**
 * 보드 위 시너지 태그별 인원. 명세서 4-4.
 *
 * ### 세는 규칙
 * 보드(`position != null`)에 올라간 유닛의 **서로 다른 `unitDef.id`** 만 센다.
 * 강철수호병 3개는 기계공학자 1명, 수호자 1명이다. 성 등급과 아이템은 무관하다.
 *
 * 명세서 4-4 표는 열 제목을 "임계값(인원)"이라고만 적고 복제본 처리를 한 글자도 말하지 않는다.
 * 즉 이건 **명세서 확정값이 아니라 해석**이고, 근거는 넷이다.
 * 1. 보드 정원이 레벨과 같아([com.leechanghyun.autobattler.core.model.PlayerState.boardCapacity])
 *    복제본을 세면 1코스트 3장으로 계열 3을 사게 되어 2/4/6 사다리가 무너진다.
 * 2. 확장 로스터가 계열 6종·직업 6종을 맞추려고 만들어졌다. 개를 센다면 그 작업이 불필요했다.
 * 3. 명세서 4-7 의 AI 스코어링이 복제본("이미 보유한 동일 유닛 개수")과 태그 겹침을
 *    **서로 다른 항**으로 가격 매긴다. 복제본은 스타업 가치, 태그는 시너지 가치다.
 * 4. 6단계 자동 합성이 같은 성급 복제본을 없애므로, 개 기준 집계는 과도기에만 보이는 불안정한 규칙이다.
 *
 * 11단계 재검토 대상이다.
 *
 * 벤치를 빼는 술어는 [com.leechanghyun.autobattler.core.combat.CombatUnit.from] 과 같다.
 * 두 곳이 어긋나면 싸우지도 않는 유닛이 시너지를 올릴 수 있으므로 "보드에 있다"의 정의를 하나로 둔다.
 */
@JvmInline
value class TraitTally(val countsByTraitId: Map<String, Int>) {

    /** 해당 시너지의 인원. 한 명도 없으면 0. */
    fun count(traitId: String): Int = countsByTraitId[traitId] ?: 0

    companion object {
        val EMPTY = TraitTally(emptyMap())

        /**
         * 보드를 집계한다. 순수 함수다.
         *
         * 8단계 AI 스코어링이 "이 유닛을 사면 임계값을 새로 돌파하는가"(명세서 4-7)를 묻기 위해
         * `of(board + 후보유닛)` 으로 가상 보드에 그대로 부를 수 있어야 하므로,
         * PlanningSession 이나 PlayerState 가 아니라 보드 목록만 받는다.
         */
        fun of(board: List<BoardUnit>): TraitTally {
            val defs = board.filter { it.isOnBoard }.map { it.unitDef }.distinctBy { it.id }
            if (defs.isEmpty()) return EMPTY

            val counts = HashMap<String, Int>()
            defs.forEach { def ->
                counts.merge(def.origin.traitId, 1, Int::plus)
                counts.merge(def.unitClass.traitId, 1, Int::plus)
            }
            return TraitTally(counts)
        }
    }
}
