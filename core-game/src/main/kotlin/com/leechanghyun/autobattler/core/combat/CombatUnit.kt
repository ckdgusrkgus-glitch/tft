package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.items.ItemStats
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.SkillDef
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import kotlin.math.roundToInt

/**
 * 피해 1회의 결과.
 *
 * 쉴드가 생기면서 "얼마나 들어갔는가"가 한 숫자로 답할 수 없는 질문이 됐다. 80 피해 중 쉴드가
 * 50 을 먹었다면 30 인가 80 인가. 둘 다 필요하므로 나눠서 돌려준다.
 * [hpLost] 는 5단계 이전 `takeDamage` 반환값의 의미를 그대로 유지하고,
 * [absorbed] 가 10단계 애니메이션의 쉴드 연출과 11단계 튜닝에 필요한 나머지 절반을 준다.
 */
data class DamageResult(val absorbed: Int, val hpLost: Int) {
    /** 방어력 감산 뒤 실제로 유닛에 닿은 총량. */
    val total: Int get() = absorbed + hpLost

    companion object {
        val NONE = DamageResult(absorbed = 0, hpLost = 0)
    }
}

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
    /**
     * 시너지(5단계)·아이템(7단계)·증강(9단계)이 합쳐진 보정. 무보정이 기본값이다.
     *
     * 사후 setter 가 아니라 **생성자 인자**인 것이 중요하다. 최대 체력이 생성 시점에 확정돼야
     * [hp] 초기값이 따라 올라가고, "최대 1200인데 시작 1000" 같은 상태가 원천적으로 불가능해진다.
     */
    val buffs: UnitBuffs = UnitBuffs.NONE,
) {
    /** 성 등급 배율과 보정이 적용된 최대 체력. 명세서 4-3, 4-4(수호자). */
    val maxHp: Int = UnitBuffs.scale(
        (def.baseHp * BoardUnit.starMultiplier(starLevel)).roundToInt(),
        buffs.hpFlat,
        buffs.hpPercent,
    )

    /** 성 등급 배율과 보정이 적용된 공격력. 명세서 4-4(검사). */
    val attackDamage: Int = UnitBuffs.scale(
        (def.baseAttack * BoardUnit.starMultiplier(starLevel)).roundToInt(),
        buffs.attackFlat,
        buffs.attackPercent,
    )

    /**
     * 스킬 1회 위력. 성 등급 배율과 보정을 함께 받는다. 명세서 4-4(마법사).
     *
     * 마법사 시너지의 "주문력"은 `skill.basePower` 를 가리킨다. AP 유닛의 `baseAttack` 이 아니다.
     * 금빛사제처럼 AP 이면서 직업이 수호자인 유닛이 있어, 주력 스탯으로 고르면 태그 한정 규칙과
     * 어긋나기 때문이다.
     *
     * 피해 지점이 아니라 여기서 올리는 덕분에 `CombatEvent.SkillCast.damagePerTarget` 이
     * 자동으로 실제 피해와 일치한다.
     */
    val skillPower: Int = UnitBuffs.scale(
        (skill.basePower * BoardUnit.starMultiplier(starLevel)).roundToInt(),
        buffs.skillPowerFlat,
        buffs.skillPowerPercent,
    )

    /**
     * 공격 한 번 사이의 틱 수.
     *
     * `attackSpeed` 는 초당 공격 횟수이고 1틱이 100ms 이므로 `10 / attackSpeed` 다.
     * 아무리 빨라도 1틱마다 한 번을 넘지 않는다.
     *
     * 사수 시너지의 공격속도 비율은 반드시 **나눗셈 전에** `attackSpeed` 에 곱한다.
     * 결과 틱 수에 곱하면 정수 반올림이 작은 버프를 비대칭으로 먹는다.
     */
    val attackIntervalTicks: Int =
        (CombatRules.TICKS_PER_SECOND / (def.attackSpeed * (1f + buffs.attackSpeedPercent)))
            .roundToInt().coerceAtLeast(1)

    /**
     * 방어력/마법저항력 통합 점수. 명세서 4-4(기계공학자).
     *
     * 유닛 마스터 데이터에는 이 열이 없다. 명세서 4-4 로스터 표에 없기 때문이며, 로스터는 건드리지
     * 않는다. 모든 유닛이 0 에서 출발하고 시너지·아이템·증강만이 이 값을 올린다.
     */
    val armor: Int get() = buffs.armorFlat

    /** 주기마다 채워지는 쉴드량. 명세서 4-4(심연의 아이들). */
    val shieldPerRefresh: Int = (maxHp * buffs.shieldPercentOfMaxHp).roundToInt()

    /**
     * 사거리.
     *
     * 시뮬레이터가 `def.attackRange` 를 직접 읽어 이 클래스를 우회하고 있었다. 지금 한곳으로
     * 모아 두지 않으면 7·9단계가 사거리를 건드릴 때 조용히 무효가 된다.
     */
    val attackRange: Int = def.attackRange

    var hp: Int = maxHp
        private set

    /**
     * 현재 마나. 시작값은 스킬 기본 시작 마나에 [UnitBuffs.startingManaFlat] 을 더한 값이고,
     * 스킬 소모 마나를 넘지 않는다. 마나의흔장 계열이 여기로 들어온다.
     *
     * 상한을 여기서 자르는 이유는 [gainMana] 와 규칙을 하나로 맞추기 위해서다. 자르지 않으면
     * 마나 아이템을 잔뜩 낀 유닛이 `mana > manaCost` 로 시작해 `spendMana` 가 그 초과분을 버린다.
     */
    var mana: Int = (skill.startingMana + buffs.startingManaFlat).coerceAtMost(skill.manaCost)
        private set

    /** 다음 공격까지 남은 틱. */
    var attackCooldown: Int = 0
        internal set

    /** 다음 이동까지 남은 틱. */
    var moveCooldown: Int = 0
        internal set

    /**
     * 남아 있는 쉴드. 심연의 아이들.
     *
     * 체력과 **분리된** 값이다. `hp` 에 더하면 시간 초과 시 체력 합으로 승자를 정하는 규칙과
     * `CombatOutcome.survivors` 의 의미가 조용히 바뀌어 4단계 전투 테스트가 흔들린다.
     */
    var shield: Int = 0
        private set

    /** 연쇄 번개 충전량. 폭풍의 부족. 명세서의 "확률"을 난수 없이 표현한다. */
    var chainCharge: Int = 0
        private set

    val isAlive: Boolean get() = hp > 0

    /** 마나가 가득 차 스킬을 쓸 수 있는 상태인지. */
    val canCastSkill: Boolean get() = isAlive && mana >= skill.manaCost

    /**
     * 피해를 입힌다. 전투의 모든 피해가 이 함수 하나를 지난다.
     *
     * 순서는 **방어력 감산 → 쉴드 흡수 → 체력**이다. 보정이 전부 0 이면 감산이 항등이고 쉴드가
     * 0 이라 5단계 이전과 산술적으로 완전히 같다.
     *
     * @return 쉴드가 먹은 양과 실제로 깎인 체력. 남은 체력보다 큰 피해는 남은 만큼만 깎인다.
     */
    fun takeDamage(amount: Int): DamageResult {
        if (amount <= 0 || !isAlive) return DamageResult.NONE

        val reduced = CombatRules.damageAfterArmor(amount, armor)
        val absorbed = reduced.coerceAtMost(shield)
        shield -= absorbed

        val hpLost = (reduced - absorbed).coerceAtMost(hp)
        hp -= hpLost
        return DamageResult(absorbed = absorbed, hpLost = hpLost)
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

    /**
     * 쉴드를 채운다. **덮어쓰기이고 누적이 아니다.**
     *
     * 누적하면 흡수량이 피해량을 넘어 시간 초과 무승부가 쏟아진다.
     */
    internal fun refreshShield() {
        if (!isAlive) return
        shield = maxOf(shield, shieldPerRefresh)
    }

    /**
     * 기본 공격 1회분을 충전하고, 가득 찼으면 상한만큼 빼고 true 를 돌려준다.
     *
     * 난수를 쓰지 않으므로 "몇 번째 공격에서 터지는가"를 정수로 단언할 수 있다.
     * 충전량이 상한보다 작으므로 한 번의 공격으로 두 번 터지는 일은 없다.
     */
    internal fun chargeChain(): Boolean {
        if (buffs.chainChargePerAttack <= 0) return false
        chainCharge += buffs.chainChargePerAttack
        if (chainCharge < CombatRules.CHAIN_CHARGE_FULL) return false
        chainCharge -= CombatRules.CHAIN_CHARGE_FULL
        return true
    }

    override fun toString(): String = "$id(${def.name}, ${team}, hp=$hp/$maxHp, shield=$shield, mana=$mana)"

    companion object {
        /**
         * 배치된 유닛을 전투 유닛으로 옮긴다.
         *
         * 보드에 올라가지 않은(벤치) 유닛은 전투에 참가하지 않으므로 null 을 돌려준다.
         *
         * **장착 아이템은 여기서 자동으로 합류한다.** 7단계에서 아이템 환산을 이 한 곳에 두지 않으면
         * 호출부마다 [ItemStats.buffsOf] 를 부르는 것을 잊을 수 있고, 잊어도 전투는 정상으로 보인다.
         *
         * @param externalBuffs 시너지(5단계)·증강(9단계)처럼 유닛 바깥에서 오는 보정.
         *   아이템 보정은 여기 넣지 않는다. [BoardUnit.items] 에서 직접 읽는다.
         * @param idPrefix 개체 id 앞에 붙일 진영 접두어. 양 팀의 개체 id 가 겹칠 수 있어서 필요하다.
         *   비어 있으면 [BoardUnit.instanceId] 를 그대로 쓴다.
         */
        fun from(
            boardUnit: BoardUnit,
            team: CombatTeam,
            externalBuffs: UnitBuffs = UnitBuffs.NONE,
            idPrefix: String = "",
        ): CombatUnit? {
            val placement = boardUnit.position ?: return null
            return CombatUnit(
                id = if (idPrefix.isEmpty()) boardUnit.instanceId else "${idPrefix}_${boardUnit.instanceId}",
                team = team,
                def = boardUnit.unitDef,
                skill = MasterData.skill(boardUnit.unitDef.skillId),
                starLevel = boardUnit.starLevel,
                position = CombatField.toField(placement, team),
                buffs = externalBuffs + ItemStats.buffsOf(boardUnit.items),
            )
        }
    }
}
