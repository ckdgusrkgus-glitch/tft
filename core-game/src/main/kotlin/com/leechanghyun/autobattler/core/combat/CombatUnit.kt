package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.SkillDef
import com.leechanghyun.autobattler.core.model.UnitDef
import kotlin.math.roundToInt

/**
 * 전투 중인 유닛 1기. 명세서 4-6.
 *
 * 전투는 100ms 틱마다 모든 유닛의 상태를 갱신하므로, 매 틱 새 객체를 만드는 대신
 * 가변 객체로 두고 시뮬레이터가 직접 고친다. 시뮬레이터 밖으로 새어 나가지 않도록
 * [CombatSimulator] 가 만들어 쓰고 결과만 [CombatOutcome] 으로 돌려준다.
 */
class CombatUnit(
    val id: String,
    val team: CombatTeam,
    val def: UnitDef,
    val skill: SkillDef,
    val starLevel: Int,
    var position: HexCoord,
) {
    /** 성 등급 배율이 적용된 최대 체력. 명세서 4-3. */
    val maxHp: Int = (def.baseHp * BoardUnit.starMultiplier(starLevel)).roundToInt()

    /** 성 등급 배율이 적용된 공격력. */
    val attackDamage: Int = (def.baseAttack * BoardUnit.starMultiplier(starLevel)).roundToInt()

    /** 스킬 1회 위력. 성 등급 배율을 함께 받는다. */
    val skillPower: Int = (skill.basePower * BoardUnit.starMultiplier(starLevel)).roundToInt()

    /**
     * 공격 한 번 사이의 틱 수.
     *
     * `attackSpeed` 는 초당 공격 횟수이고 1틱이 100ms 이므로 `10 / attackSpeed` 다.
     * 아무리 빨라도 1틱마다 한 번을 넘지 않는다.
     */
    val attackIntervalTicks: Int = (CombatRules.TICKS_PER_SECOND / def.attackSpeed).roundToInt().coerceAtLeast(1)

    var hp: Int = maxHp
        private set

    var mana: Int = skill.startingMana
        private set

    /** 다음 공격까지 남은 틱. */
    var attackCooldown: Int = 0
        internal set

    /** 다음 이동까지 남은 틱. */
    var moveCooldown: Int = 0
        internal set

    val isAlive: Boolean get() = hp > 0

    /** 마나가 가득 차 스킬을 쓸 수 있는 상태인지. */
    val canCastSkill: Boolean get() = isAlive && mana >= skill.manaCost

    /**
     * 피해를 입힌다.
     *
     * @return 실제로 깎인 체력. 남은 체력보다 큰 피해는 남은 만큼만 깎인다.
     */
    fun takeDamage(amount: Int): Int {
        val dealt = amount.coerceAtMost(hp)
        hp -= dealt
        return dealt
    }

    /** 마나를 얻는다. 최대치를 넘겨 쌓이지는 않는다. */
    fun gainMana(amount: Int) {
        if (!isAlive) return
        mana = (mana + amount).coerceAtMost(skill.manaCost)
    }

    /** 스킬을 쓰고 마나를 비운다. */
    fun spendMana() {
        mana = 0
    }

    override fun toString(): String = "$id(${def.name}, ${team}, hp=$hp/$maxHp, mana=$mana)"

    companion object {
        /**
         * 배치된 유닛을 전투 유닛으로 옮긴다.
         *
         * 보드에 올라가지 않은(벤치) 유닛은 전투에 참가하지 않으므로 null 을 돌려준다.
         */
        fun from(boardUnit: BoardUnit, team: CombatTeam): CombatUnit? {
            val placement = boardUnit.position ?: return null
            return CombatUnit(
                id = boardUnit.instanceId,
                team = team,
                def = boardUnit.unitDef,
                skill = MasterData.skill(boardUnit.unitDef.skillId),
                starLevel = boardUnit.starLevel,
                position = CombatField.toField(placement, team),
            )
        }
    }
}
