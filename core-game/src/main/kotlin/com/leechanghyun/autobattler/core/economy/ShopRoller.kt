package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.UnitDef
import kotlin.random.Random

/**
 * 상점 슬롯 1칸. 유닛이 비어 있을 수 있어 [unit] 이 nullable 이다.
 *
 * @param purchased 이번 상점에서 이미 산 칸인지. 산 칸은 리롤 전까지 빈 칸으로 남는다.
 */
data class ShopSlot(
    val unit: UnitDef?,
    val purchased: Boolean = false,
) {
    val isEmpty: Boolean get() = unit == null
}

/** 상점 5칸 한 세트. 명세서 4-1. */
data class ShopOffer(val slots: List<ShopSlot>) {
    val units: List<UnitDef> get() = slots.mapNotNull { it.unit }

    companion object {
        val EMPTY = ShopOffer(List(EconomyRules.SHOP_SLOT_COUNT) { ShopSlot(null) })
    }
}

/**
 * 상점 롤 로직. 명세서 4-1.
 *
 * 슬롯마다 독립적으로 [ShopOdds] 로 등급을 뽑고, 그 등급의 [UnitPool] 재고에서 유닛 1종을 꺼낸다.
 * 상점에 노출된 유닛은 풀에서 빠진 상태이며, 리롤하거나 상점을 떠나면 [giveBackOffer] 로 되돌린다.
 *
 * UI 와 무관한 순수 Kotlin 이라 단위테스트로 검증한다. [random] 을 시드로 고정하면 결과가 재현된다.
 */
class ShopRoller(
    private val pool: UnitPool,
    private val random: Random = Random.Default,
) {
    /**
     * 레벨 [level] 기준으로 상점 [slotCount] 칸을 새로 뽑는다.
     *
     * 직전 상점 [previous] 을 넘기면 그 유닛들을 먼저 풀에 되돌린 뒤 뽑는다. (리롤 동작)
     */
    fun roll(
        level: Int,
        previous: ShopOffer? = null,
        slotCount: Int = EconomyRules.SHOP_SLOT_COUNT,
    ): ShopOffer {
        previous?.let(::giveBackOffer)
        return ShopOffer(List(slotCount) { ShopSlot(rollSlot(level)) })
    }

    /** 상점에 남아 있는 유닛을 모두 풀에 되돌린다. */
    fun giveBackOffer(offer: ShopOffer) {
        pool.giveBackAll(offer.units.map { it.id })
    }

    /**
     * 슬롯 1칸을 뽑는다.
     *
     * 추첨된 등급의 재고가 바닥났으면 코스트가 가장 가까운 다른 등급에서 뽑는다.
     * (같은 거리면 낮은 코스트 우선) 모든 등급이 비면 null 을 돌려 빈 칸으로 둔다.
     */
    private fun rollSlot(level: Int): UnitDef? {
        val wantedCost = ShopOdds.rollCost(level, random)
        pool.takeRandomOfCost(wantedCost, random)?.let { return it }

        val fallbackOrder = (UnitDef.MIN_COST..UnitDef.MAX_COST)
            .filter { it != wantedCost }
            .sortedWith(compareBy({ kotlin.math.abs(it - wantedCost) }, { it }))

        for (cost in fallbackOrder) {
            pool.takeRandomOfCost(cost, random)?.let { return it }
        }
        return null
    }
}
