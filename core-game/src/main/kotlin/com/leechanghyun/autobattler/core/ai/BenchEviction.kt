package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef

/**
 * 벤치가 꽉 찼을 때 **무엇을 내보내고, 내보낼 만한가**를 정한다. 로드맵 8단계.
 *
 * ### 왜 따로 떼어 놓았나
 * 명세서 4-7 은 판매를 한 글자도 말하지 않는다. 그래서 여기 있는 것은 전부 해석이고, 해석은
 * 테스트가 직접 찌를 수 있어야 한다. [BotBrain] 안의 private 함수로 두었을 때는 상점 뽑기 결과에
 * 의존하지 않고는 이 규칙 하나만 검사할 방법이 없었고, 실제로 규칙 하나가 **아무 테스트에도
 * 걸리지 않은 채** 통과했다.
 *
 * ### 하나의 순서를 양쪽에 같이 쓴다
 * 내보낼 카드를 고르는 순서와 "들어올 카드가 나은가"를 재는 순서가 같다.
 * **우선 태그 일치 수 → 같은 종 보유 수 → 전투력** 이다. 두 곳이 다른 기준을 쓰면
 * "이 카드가 저 카드보다 낫다"가 봇 안에서 어긋난다.
 *
 * 같은 종 보유 수가 두 번째 키인 것이 중요하다. 약하지만 2장 모아 둔 카드(6단계 2성 직전)를
 * 강한 1장짜리보다 먼저 팔아 버리면, 스타업 항이 아무리 큰 점수를 줘도 2성이 완성되지 않는다.
 */
internal object BenchEviction {

    /** 벤치에서 가장 먼저 내보낼 카드. 벤치가 비었으면 null. 보드는 건드리지 않는다. */
    fun victim(player: PlayerState, plan: BotPlan): BoardUnit? {
        val copies = copiesByDefId(player)
        return player.bench.minWithOrNull(
            // instanceId 는 고유하므로 마지막 키가 동점을 반드시 끊는다. 해시 순서가 낄 자리가 없다.
            compareBy<BoardUnit> { plan.priorityMatchCount(it.unitDef) }
                .thenBy { copies[it.unitDef.id] ?: 0 }
                .thenBy { BotScoring.instancePower(it) }
                .thenBy { it.instanceId },
        )
    }

    /**
     * [incoming] 이 [victim] 을 밀어낼 만한가.
     *
     * [incoming] 은 아직 개체가 없으므로 **살 경우 갖게 될 수**(지금 보유 수 + 1)로 센다.
     * 그래서 2성이 걸린 3장째는 벤치를 비워서라도 들어오고, 이미 여러 장 가진 종은 밀려나지 않는다.
     *
     * 동점이면 **바꾸지 않는다.** 같은 값을 교체하는 데 판매 손실을 낼 이유가 없고,
     * 그렇게 두지 않으면 벤치가 꽉 찬 뒤로 "사면 팔고 팔면 산다"가 된다.
     */
    fun isUpgrade(incoming: UnitDef, victim: BoardUnit, player: PlayerState, plan: BotPlan): Boolean {
        val copies = copiesByDefId(player)

        val incomingMatch = plan.priorityMatchCount(incoming)
        val victimMatch = plan.priorityMatchCount(victim.unitDef)
        if (incomingMatch != victimMatch) return incomingMatch > victimMatch

        val incomingCopies = (copies[incoming.id] ?: 0) + 1
        val victimCopies = copies[victim.unitDef.id] ?: 0
        if (incomingCopies != victimCopies) return incomingCopies > victimCopies

        return BotScoring.power(incoming) > BotScoring.instancePower(victim)
    }

    private fun copiesByDefId(player: PlayerState): Map<String, Int> =
        (player.bench + player.board).groupingBy { it.unitDef.id }.eachCount()
}
