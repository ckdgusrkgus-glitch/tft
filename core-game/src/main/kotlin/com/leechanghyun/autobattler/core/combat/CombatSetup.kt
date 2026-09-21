package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.synergy.SynergyState

/**
 * 전투 시작 직전의 확정 상태. 양쪽 유닛과 양쪽 시너지를 한데 묶는다.
 *
 * 이 타입이 필요한 이유는 이음매 때문이다. 유닛을 조립하는 쪽은 이벤트 목록을 만들지 않고,
 * 시뮬레이션을 도는 쪽은 보드를 모른다. "시너지가 몇 단계였는가"라는 사실이 그 사이를 건너야
 * 10단계 전투 HUD 가 "수호자 2 발동"을 그릴 수 있다.
 *
 * 시너지는 전투 내내 바뀌지 않는다. 유일하게 바꿀 수 있는 것이 소환물인데, 그래서 소환물은
 * 시너지 인원에 세지 않는다. (그리고 5단계에는 소환 자체가 없다.)
 */
data class CombatSetup(
    val units: List<CombatUnit>,
    val synergyByTeam: Map<CombatTeam, SynergyState>,
) {
    fun synergyFor(team: CombatTeam): SynergyState = synergyByTeam[team] ?: SynergyState.NONE
}
