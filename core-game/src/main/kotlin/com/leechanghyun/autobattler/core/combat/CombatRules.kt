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
}
