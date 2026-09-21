package com.leechanghyun.autobattler.core.economy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.POOL_SIZE_BY_COST
import com.leechanghyun.autobattler.core.model.UnitDef
import kotlin.random.Random

/**
 * 8인이 함께 쓰는 공용 유닛 풀. 명세서 4-1 "코스트별 풀 크기".
 *
 * 풀(pool)이란 게임 전체에 존재하는 유닛 카드의 재고다. 1코스트 유닛은 종류마다 22장이 있고,
 * 누가 사 가면 그만큼 다른 사람 상점에 덜 나온다. 이 재고 공유가 오토배틀러 경제의 핵심이라
 * 상점 로직을 만들기 전에 먼저 구현한다.
 *
 * 이 클래스는 UI 와 무관한 순수 Kotlin 이고 스레드 안전하지 않다.
 * 라운드 진행은 단일 코루틴에서 순차 처리한다는 전제다.
 */
class UnitPool(
    units: List<UnitDef> = MasterData.units,
    poolSizeByCost: Map<Int, Int> = POOL_SIZE_BY_COST,
) {
    private val unitsById: Map<String, UnitDef> = units.associateBy { it.id }

    private val remaining: MutableMap<String, Int> = units.associate { unit ->
        val size = requireNotNull(poolSizeByCost[unit.cost]) {
            "코스트 ${unit.cost} 의 풀 크기가 정의되지 않았다 (유닛 ${unit.id})"
        }
        unit.id to size
    }.toMutableMap()

    private val unitIdsByCost: Map<Int, List<String>> =
        units.groupBy { it.cost }.mapValues { (_, list) -> list.map { it.id } }

    /** 풀에 남은 [unitId] 카드 수. */
    fun remaining(unitId: String): Int = remaining[unitId] ?: 0

    /** 코스트 [cost] 등급에 남은 카드 총합. */
    fun remainingOfCost(cost: Int): Int =
        unitIdsByCost[cost]?.sumOf { remaining(it) } ?: 0

    /** 풀에 남은 전체 카드 수. */
    fun totalRemaining(): Int = remaining.values.sum()

    /**
     * [unitId] 카드 1장을 풀에서 꺼낸다.
     *
     * @return 꺼낸 유닛. 재고가 없으면 null.
     */
    fun take(unitId: String): UnitDef? {
        val left = remaining[unitId] ?: return null
        if (left <= 0) return null
        remaining[unitId] = left - 1
        return unitsById[unitId]
    }

    /**
     * 코스트 [cost] 등급에서 카드 1장을 무작위로 꺼낸다.
     *
     * 유닛 종류별로 균등하게 뽑는 것이 아니라 **남은 재고 수에 비례**해 뽑는다.
     * 많이 팔린 유닛일수록 덜 나오는 원작 동작을 그대로 따른다.
     *
     * @return 꺼낸 유닛. 해당 등급 재고가 모두 소진됐으면 null.
     */
    fun takeRandomOfCost(cost: Int, random: Random): UnitDef? {
        val ids = unitIdsByCost[cost] ?: return null
        val total = ids.sumOf { remaining(it) }
        if (total <= 0) return null

        var ticket = random.nextInt(total)
        for (id in ids) {
            ticket -= remaining(id)
            if (ticket < 0) return take(id)
        }
        // 위 루프는 total 계산과 일관되므로 도달하지 않는다.
        return null
    }

    /** 카드 1장을 풀에 되돌린다. 유닛 판매, 상점 리롤로 노출이 사라질 때 쓴다. */
    fun giveBack(unitId: String) {
        val current = remaining[unitId] ?: return
        remaining[unitId] = current + 1
    }

    /** 여러 장을 한 번에 되돌린다. */
    fun giveBackAll(unitIds: Iterable<String>) = unitIds.forEach(::giveBack)
}
