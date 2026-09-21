package com.leechanghyun.autobattler.core.combat

/**
 * 전투 진행에 쓰이는 상수. 명세서 4-6.
 *
 * 틱 길이 100ms 만 명세서 확정값이고, **나머지는 전부 임의 초기값**이다.
 * 명세서가 마나 획득량이나 이동 속도를 정하지 않았기 때문이며, 11단계 밸런스 튜닝에서 조정한다.
 */
object CombatRules {

    /** 한 틱의 길이(ms). 명세서 4-6 확정값. */
    const val TICK_MS = 100

    /** 1초에 해당하는 틱 수. */
    const val TICKS_PER_SECOND = 10f

    /** 기본 공격 한 번에 얻는 마나. */
    const val MANA_PER_ATTACK = 10

    /** 피해를 입었을 때 얻는 마나. */
    const val MANA_PER_HIT_TAKEN = 5

    /** 한 칸 이동에 걸리는 틱. 300ms 다. */
    const val MOVE_INTERVAL_TICKS = 3

    /**
     * 전투가 끝나지 않을 때 강제로 끊는 틱 수. 3000틱 = 300초.
     *
     * 서로 닿지 못하거나 회복량이 피해량을 넘는 구성에서 무한히 도는 것을 막는다.
     */
    const val MAX_TICKS = 3000

    /**
     * 방어력 점수를 피해 감소로 바꾸는 상수. 방어력이 이 값과 같으면 피해가 절반이 된다.
     *
     * 명세서에 공식이 없어 임의 초기값이며 11단계에서 조정한다.
     */
    const val ARMOR_K = 100

    /** 방어력이 아무리 높아도 0 이 아닌 피해는 최소 이만큼 들어간다. 무한 무승부를 막는다. */
    const val MIN_DAMAGE = 1

    /** 연쇄 번개가 터지는 충전 상한. 단계별 충전량이 명세서의 "확률" 값에 대응하도록 100 이다. */
    const val CHAIN_CHARGE_FULL = 100

    /**
     * 방어력 감산. **정수 연산이다.** 피해 경로에 부동소수를 들이지 않아 결정성이 가장 견고하고
     * 테스트도 정수로 단언할 수 있다.
     *
     * 뺄셈식(`amount - armor`)이 아니라 나눗셈식인 이유는 겹침 때문이다. 기계공학자 6단계(60)에
     * 7단계 수호의흔장과 9단계 강철의의지(+10)가 겹치면 뺄셈은 공격력 낮은 유닛을 완전히 무력화해
     * 시간 초과 무승부를 양산한다. 나눗셈은 수익 체감이라 그런 절벽이 없다.
     *
     * 시너지·아이템·증강이 **전부 이 함수 하나**를 거친다. 방어력의 정의가 셋으로 갈라지지 않는다.
     * 7단계에서 방어력과 마법저항력을 나눠야 하면 피해 종류 인자를 여기에 더한다.
     */
    fun damageAfterArmor(amount: Int, armor: Int): Int =
        if (amount <= 0 || armor <= 0) amount
        else (amount * ARMOR_K / (ARMOR_K + armor)).coerceAtLeast(MIN_DAMAGE)
}
