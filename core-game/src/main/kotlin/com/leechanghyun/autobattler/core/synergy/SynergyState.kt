package com.leechanghyun.autobattler.core.synergy

/**
 * 보드 하나를 해석한 결과 전체. 전투가 시작되면 끝날 때까지 바뀌지 않는다.
 *
 * 소비자가 셋이고 서로를 모른다.
 * - 전투: [combat] 을 전투 유닛 생성자에 넣는다
 * - 경제: [goldPerRound] 를 라운드 수입이 더한다
 * - 화면: [activeTraits] 를 그대로 그린다. 세지도, 임계값을 비교하지도 않는다
 *
 * [goldPerRound] 가 [TeamBuffs] 안이 아니라 여기 있는 이유는 황금가문이 8종 중 유일하게
 * 전투를 거치지 않기 때문이다. 전투 타입에 억지로 끼우면 economy 가 combat 을 의존하게 되고,
 * 명세서 6장이 `synergy/` 를 `combat/` 의 **형제**로 둔 이유가 사라진다.
 *
 * @param activeTraits 보드에 구성원이 1명이라도 있는 시너지 전부. 미발동(tier 0)도 들어간다.
 *   화면이 회색 "수호자 1/2"를 그리려면 인원과 다음 임계값이 필요하기 때문이다.
 */
data class SynergyState(
    val activeTraits: List<ActiveTrait>,
    val combat: TeamBuffs,
    val goldPerRound: Int,
) {
    /** 실제로 발동한 것만. 화면이 굵게 그리는 목록이다. */
    val active: List<ActiveTrait> get() = activeTraits.filter { it.isActive }

    fun traitOf(traitId: String): ActiveTrait? = activeTraits.firstOrNull { it.traitId == traitId }

    fun tierOf(traitId: String): Int = traitOf(traitId)?.tier ?: 0

    fun memberCountOf(traitId: String): Int = traitOf(traitId)?.memberCount ?: 0

    companion object {
        val NONE = SynergyState(activeTraits = emptyList(), combat = TeamBuffs.NONE, goldPerRound = 0)
    }
}
