package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.OffsetCoord
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.UnitClass
import com.leechanghyun.autobattler.core.model.UnitDef

/**
 * 시너지 테스트용 보드 생성기.
 *
 * 수치가 아니라 **구성**을 다루므로 여기서는 실제 마스터 데이터의 유닛을 쓴다.
 * 시너지 단계는 어떤 유닛이 올라갔는지로만 정해지고 유닛의 스탯과는 무관하기 때문이다.
 */
internal object SynergyFixtures {

    /** 해당 계열의 서로 다른 유닛 [count] 종. */
    fun defsOf(origin: Origin, count: Int): List<UnitDef> =
        MasterData.units.filter { it.origin == origin }.take(count)
            .also { require(it.size == count) { "$origin 에 $count 종이 없다" } }

    /** 해당 직업의 서로 다른 유닛 [count] 종. */
    fun defsOf(unitClass: UnitClass, count: Int): List<UnitDef> =
        MasterData.units.filter { it.unitClass == unitClass }.take(count)
            .also { require(it.size == count) { "$unitClass 에 $count 종이 없다" } }

    /** 해당 시너지 id 의 구성원 [count] 종. 계열이든 직업이든 받는다. */
    fun defsOfTrait(traitId: String, count: Int): List<UnitDef> {
        val origin = Origin.entries.firstOrNull { it.name.lowercase() == traitId }
        if (origin != null) return defsOf(origin, count)
        val unitClass = requireNotNull(UnitClass.entries.firstOrNull { it.name.lowercase() == traitId }) {
            "알 수 없는 시너지 id: $traitId"
        }
        return defsOf(unitClass, count)
    }

    /** 유닛들을 보드 맨 앞줄부터 채운다. 좌표는 서로 겹치지 않는다. */
    fun board(defs: List<UnitDef>, idPrefix: String = "b", starLevel: Int = 1): List<BoardUnit> =
        defs.mapIndexed { index, def ->
            BoardUnit(
                instanceId = "$idPrefix$index",
                unitDef = def,
                starLevel = starLevel,
                position = HexBoard.fromOffset(OffsetCoord(row = 3 - index / 7, col = index % 7)),
            )
        }

    /** 같은 유닛들을 벤치에 둔다. 시너지에 세어지면 안 되는 쪽이다. */
    fun bench(defs: List<UnitDef>, idPrefix: String = "x"): List<BoardUnit> =
        defs.mapIndexed { index, def -> BoardUnit(instanceId = "$idPrefix$index", unitDef = def) }
}
