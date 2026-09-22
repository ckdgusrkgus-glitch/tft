package com.leechanghyun.autobattler.core.model

import kotlin.math.roundToInt

/**
 * 전장/벤치에 올라간 유닛 개체. 명세서 5장 데이터 모델 초안.
 *
 * @param position 보드 좌표. 벤치에 있으면 null.
 * @param starLevel 1..3 성. 합성은 [com.leechanghyun.autobattler.core.planning.StarUp] 이 한다(6단계).
 * @param items 장착한 아이템. 최대 [MAX_ITEM_SLOTS] 개. 장착/해제는
 *   [com.leechanghyun.autobattler.core.planning.PlanningSession] 이 한다(7단계).
 */
data class BoardUnit(
    val instanceId: String,
    val unitDef: UnitDef,
    val starLevel: Int = 1,
    val position: HexCoord? = null,
    val items: List<ItemDef> = emptyList(),
) {
    init {
        require(starLevel in 1..MAX_STAR) { "성 등급은 1..$MAX_STAR 범위여야 한다" }
        require(items.size <= MAX_ITEM_SLOTS) { "유닛당 아이템은 최대 ${MAX_ITEM_SLOTS}개다" }
    }

    /**
     * 성 등급 배율을 적용한 체력. 명세서 4-3: 2성 = 1.8배, 3성 = 3.24배(=1.8^2).
     *
     * 버림이 아니라 반올림이다. `1.8f` 는 이진 부동소수로 정확히 표현되지 않아
     * `1.8f * 1.8f` 가 3.2399998 이 되고, 버리면 3성 체력 3240 이 3239 로 어긋난다.
     */
    val hp: Int get() = (unitDef.baseHp * starMultiplier(starLevel)).roundToInt()

    /** 성 등급 배율을 적용한 공격력. 반올림 이유는 [hp] 와 같다. */
    val attack: Int get() = (unitDef.baseAttack * starMultiplier(starLevel)).roundToInt()

    val isOnBoard: Boolean get() = position != null

    /** 아이템을 더 낄 자리가 남았는지. 7단계 장착 경로가 확인한다. */
    val hasFreeItemSlot: Boolean get() = items.size < MAX_ITEM_SLOTS

    /**
     * 이 개체가 소모한 풀 카드 장수. 1성 1장, 2성 3장, 3성 9장이다.
     *
     * 판매할 때 이만큼을 공용 풀에 되돌려야 다른 플레이어가 다시 뽑을 수 있다.
     */
    val copiesConsumed: Int get() = COPIES_PER_STAR_UP.pow(starLevel - 1)

    private fun Int.pow(exponent: Int): Int {
        var result = 1
        repeat(exponent) { result *= this }
        return result
    }

    companion object {
        const val MAX_STAR = 3
        const val MAX_ITEM_SLOTS = 3

        /** 한 단계 성을 올리는 데 필요한 같은 유닛 수. 명세서 4-3: 3개. */
        const val COPIES_PER_STAR_UP = 3

        /** 명세서 4-3 의 등급별 스탯 배율. */
        const val STAR_UP_MULTIPLIER = 1.8f

        fun starMultiplier(starLevel: Int): Float =
            when (starLevel) {
                1 -> 1f
                2 -> STAR_UP_MULTIPLIER
                else -> STAR_UP_MULTIPLIER * STAR_UP_MULTIPLIER
            }
    }
}
