package com.leechanghyun.autobattler.core.synergy

import kotlin.math.roundToInt

/**
 * 유닛 1기에게 적용되는 보정 묶음. 전부 0 이면 무보정이다.
 *
 * 4단계 전투 시뮬레이터가 "시너지 버프는 5단계, 아이템 효과는 7단계에서 이 계산에 끼워 넣는다"고
 * 예약해 둔 자리다. 그래서 5단계가 쓰지 않는 가산(flat) 항도 처음부터 둔다.
 * 명세서 4-5 의 컴포넌트 9종(힘의흔장 AD+, 지혜의흔장 AP+, 활력의흔장 HP+, 신속의흔장 공속+,
 * 수호의흔장 방어+, 저항의흔장 마저+)이 7단계에 그대로 이 필드들로 합류하고, 9단계 증강도 같다.
 * 시너지 전용 구조로 만들면 7단계에서 똑같은 것을 한 번 더 만들게 된다.
 *
 * 기본값이 전부 0 이라 기존 전투 코드와 테스트는 한 글자도 고치지 않고 그대로 통과한다.
 *
 * ### 방어력은 "퍼센트"가 아니라 "점수"다
 * 퍼센트는 더해지지 않는다. 50% 감소 둘을 더하면 100% 가 되어 무적이 된다. 점수는 더해진다.
 * 기계공학자(5단계)·수호의흔장(7단계)·강철의의지(9단계, "아군 전체 방어력 +10")가 전부
 * [armorFlat] 하나에 쏟아져 들어가고, 점수를 감소율로 바꾸는 변환은
 * [com.leechanghyun.autobattler.core.combat.CombatRules.damageAfterArmor] 한 곳에서만 한다.
 *
 * 방어력과 마법저항력은 5단계에서 **한 풀로 합친다.** 기계공학자가 둘을 항상 함께 올리고
 * (명세서 4-4), 피해에 종류 구분이 없어 어떤 테스트도 둘을 구별할 수 없기 때문이다.
 * 7단계에서 수호의흔장과 저항의흔장이 실제로 달라야 할 때 피해 종류를 도입해 쪼갠다.
 */
data class UnitBuffs(
    val hpFlat: Int = 0,
    val hpPercent: Float = 0f,
    val attackFlat: Int = 0,
    val attackPercent: Float = 0f,
    val skillPowerFlat: Int = 0,
    val skillPowerPercent: Float = 0f,
    val attackSpeedPercent: Float = 0f,
    /** 방어력/마법저항력 통합 점수. 5단계에서는 나누지 않는다. */
    val armorFlat: Int = 0,
    /** 주기마다 채워지는 쉴드량(최대 체력 비율). 심연의 아이들. */
    val shieldPercentOfMaxHp: Float = 0f,
    /** 쉴드 갱신 주기(틱). 0 이면 쉴드가 없다. */
    val shieldPeriodTicks: Int = 0,
    /** 기본 공격 1회당 쌓이는 연쇄 번개 충전량. 폭풍의 부족. */
    val chainChargePerAttack: Int = 0,
    /** 연쇄 번개 1회가 대상 하나에게 주는 피해. */
    val chainDamage: Int = 0,
    /** 연쇄 번개가 튀는 대상 수. 주 대상은 제외한다. */
    val chainTargets: Int = 0,
) {
    /**
     * 항목별 합산.
     *
     * [shieldPeriodTicks] 만 예외로 **0 이 아닌 값 중 짧은 쪽**을 쓴다. 주기는 더할 수 있는 양이 아니다.
     */
    operator fun plus(other: UnitBuffs): UnitBuffs = UnitBuffs(
        hpFlat = hpFlat + other.hpFlat,
        hpPercent = hpPercent + other.hpPercent,
        attackFlat = attackFlat + other.attackFlat,
        attackPercent = attackPercent + other.attackPercent,
        skillPowerFlat = skillPowerFlat + other.skillPowerFlat,
        skillPowerPercent = skillPowerPercent + other.skillPowerPercent,
        attackSpeedPercent = attackSpeedPercent + other.attackSpeedPercent,
        armorFlat = armorFlat + other.armorFlat,
        shieldPercentOfMaxHp = shieldPercentOfMaxHp + other.shieldPercentOfMaxHp,
        shieldPeriodTicks = shorterPeriod(shieldPeriodTicks, other.shieldPeriodTicks),
        chainChargePerAttack = chainChargePerAttack + other.chainChargePerAttack,
        chainDamage = chainDamage + other.chainDamage,
        chainTargets = chainTargets + other.chainTargets,
    )

    val hasShield: Boolean get() = shieldPeriodTicks > 0 && shieldPercentOfMaxHp > 0f

    val hasChain: Boolean get() = chainChargePerAttack > 0 && chainTargets > 0 && chainDamage > 0

    companion object {
        val NONE = UnitBuffs()

        private fun shorterPeriod(a: Int, b: Int): Int = when {
            a <= 0 -> b
            b <= 0 -> a
            else -> minOf(a, b)
        }

        /**
         * 성 배율까지 끝난 스탯에 보정을 얹는다. **가산 먼저, 비율 나중**이다.
         *
         * 즉 7단계 아이템의 "+20 공격력"은 검사 시너지 비율의 혜택을 함께 받는다. 원작 관행이다.
         * 보정이 없으면 `((v + 0) * 1f).roundToInt() == v` 이므로 5단계 이전 값과 정확히 같다.
         */
        fun scale(starScaled: Int, flat: Int, percent: Float): Int =
            ((starScaled + flat) * (1f + percent)).roundToInt()
    }
}
