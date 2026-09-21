package com.leechanghyun.autobattler.core.model

/**
 * 전장/벤치에 올라간 유닛 개체. 명세서 5장 데이터 모델 초안.
 *
 * @param position 보드 좌표. 벤치에 있으면 null.
 * @param starLevel 1..3 성. 합성 로직은 로드맵 6단계에서 붙인다.
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

    /** 성 등급 배율을 적용한 체력. 명세서 4-3: 2성 = 1.8배, 3성 = 3.24배(=1.8^2). */
    val hp: Int get() = (unitDef.baseHp * starMultiplier(starLevel)).toInt()

    /** 성 등급 배율을 적용한 공격력. */
    val attack: Int get() = (unitDef.baseAttack * starMultiplier(starLevel)).toInt()

    val isOnBoard: Boolean get() = position != null

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
