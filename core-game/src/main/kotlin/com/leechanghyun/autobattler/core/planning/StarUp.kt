package com.leechanghyun.autobattler.core.planning

import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.PlayerState

/**
 * 합성 1회의 기록. 10단계 연출과 단위테스트가 읽는다.
 *
 * @param consumedInstanceIds 사라진 세 개체. [resultInstanceId] 도 이 안에 들어 있다.
 *   살아남은 개체가 id 를 물려받기 때문이다.
 * @param returnedItems 자리가 모자라 가방으로 간 아이템. 7단계 전에는 항상 비어 있다.
 */
data class StarUpEvent(
    val unitDefId: String,
    val fromStar: Int,
    val toStar: Int,
    val resultInstanceId: String,
    val consumedInstanceIds: List<String>,
    val returnedItems: List<ItemDef> = emptyList(),
)

/** [StarUp.apply] 의 결과. */
data class StarUpResult(
    val player: PlayerState,
    val events: List<StarUpEvent>,
) {
    val merged: Boolean get() = events.isNotEmpty()

    /**
     * [instanceId] 개체가 합성을 거친 뒤 어떤 id 가 되었는지 따라간다.
     *
     * 방금 산 유닛이 즉시 합성돼 사라졌을 때 "그래서 내가 얻은 게 뭔가"를 답하는 용도다.
     * 연쇄 합성(1성 3개 → 2성, 그 2성이 다시 3성)도 이벤트 순서대로 따라가므로 끝까지 추적된다.
     */
    fun trace(instanceId: String): String {
        var current = instanceId
        events.forEach { event ->
            if (current in event.consumedInstanceIds) current = event.resultInstanceId
        }
        return current
    }
}

/**
 * 유닛 합성(스타업). 명세서 4-3, 로드맵 6단계.
 *
 * 완료 기준은 "동일 유닛 3개 자동 합성"이다. 명세서 4-3 이 정한 것은 두 줄뿐이다.
 * 같은 유닛 3개면 2성, 2성 3개(= 1성 9장)면 3성. 나머지는 아래처럼 정했고 11단계 재검토 대상이다.
 *
 * ### 벤치와 보드를 함께 본다
 * 한쪽만 보면 "벤치에 2개, 보드에 1개"가 영원히 합성되지 않는다. 명세서에 범위 제한이 없고
 * 원작도 함께 세므로 [PlayerState.bench] 와 [PlayerState.board] 를 한 묶음으로 본다.
 *
 * ### 어느 3개를 소모하고 누가 살아남는가
 * 같은 유닛이 4개 이상일 수 있으므로 **소모 순서**가 필요하고, 결정론(난수 없음)을 위해 고정한다.
 * 벤치를 먼저, 그 다음 보드를 각 목록 순서대로 소모한다. 보드 유닛을 먼저 먹으면 배치해 둔 진형이
 * 임의로 무너지기 때문이다.
 *
 * 살아남는 개체는 소모된 셋 중 **보드에 있던 첫 유닛**이고, 셋 다 벤치면 첫 벤치 유닛이다.
 * 살아남은 쪽이 `instanceId` 와 `position` 을 그대로 물려받으므로 화면의 선택 상태와 배치가 유지된다.
 * 보드 유닛 2개가 한 번에 먹히면 보드 인원이 하나 줄지만, 늘어나는 일은 없어 정원을 넘길 수 없다.
 *
 * ### 풀에 아무것도 돌려주지 않는다
 * [BoardUnit.copiesConsumed] 가 1 / 3 / 9 이므로 1성 3개(3장)가 2성 1개(3장)가 되고
 * 2성 3개(9장)가 3성 1개(9장)가 된다. 장수가 저절로 보존되어 풀을 건드릴 이유가 없다.
 * `카드 총량은 어떤 조작에도 변하지 않는다` 테스트가 이 성질을 지킨다.
 *
 * ### 아이템
 * 7단계 전에는 유닛에 아이템을 끼우는 경로가 아예 없어 항상 빈 목록이다. 그래도 소모된 유닛의
 * 아이템을 버리지 않고 살아남은 유닛에 [BoardUnit.MAX_ITEM_SLOTS] 까지 옮긴 뒤 넘치는 만큼
 * [PlayerState.itemInventory] 로 돌린다. 7단계에서 장착이 생길 때 조용히 사라지는 사고를 막는다.
 */
object StarUp {

    /**
     * 합성할 수 있는 만큼 전부 합성한 상태를 돌려준다. 더 합성할 것이 없으면 [player] 를 그대로 준다.
     *
     * 한 번 합성할 때마다 유닛 수가 2개씩 줄어들어 반드시 끝난다.
     */
    fun apply(player: PlayerState): StarUpResult {
        var current = player
        val events = mutableListOf<StarUpEvent>()

        while (true) {
            val event = mergeOnce(current) ?: break
            current = event.first
            events += event.second
        }
        return StarUpResult(player = current, events = events)
    }

    /** 합성 가능한 조합이 여러 개면 성 등급이 낮은 것부터, 같으면 유닛 id 순으로 하나만 처리한다. */
    private fun mergeOnce(player: PlayerState): Pair<PlayerState, StarUpEvent>? {
        // 소모 순서를 여기서 한 번만 정한다. 벤치 먼저, 그 다음 보드.
        val ordered = player.bench + player.board

        val group = ordered
            .filter { it.starLevel < BoardUnit.MAX_STAR }
            .groupBy { it.unitDef.id to it.starLevel }
            .filterValues { it.size >= BoardUnit.COPIES_PER_STAR_UP }
            // 낮은 성부터 처리해야 1성 셋이 2성이 되고 그 2성이 다시 3성으로 이어진다.
            // 같은 성이면 유닛 id 사전순. 난수도 해시도 쓰지 않아 실행마다 결과가 같다.
            .minWithOrNull(compareBy({ it.key.second }, { it.key.first }))
            ?: return null

        val (key, members) = group
        val consumed = members.take(BoardUnit.COPIES_PER_STAR_UP)
        val survivor = consumed.firstOrNull { it.isOnBoard } ?: consumed.first()

        val allItems = (listOf(survivor) + consumed.filter { it.instanceId != survivor.instanceId })
            .flatMap { it.items }
        val kept = allItems.take(BoardUnit.MAX_ITEM_SLOTS)
        val overflow = allItems.drop(BoardUnit.MAX_ITEM_SLOTS)

        val upgraded = survivor.copy(starLevel = survivor.starLevel + 1, items = kept)
        val consumedIds = consumed.map { it.instanceId }.toSet()

        val next = player.copy(
            bench = player.bench.filterNot { it.instanceId in consumedIds } +
                listOfNotNull(upgraded.takeUnless { it.isOnBoard }),
            board = player.board.filterNot { it.instanceId in consumedIds } +
                listOfNotNull(upgraded.takeIf { it.isOnBoard }),
            itemInventory = player.itemInventory + overflow,
        )

        return next to StarUpEvent(
            unitDefId = key.first,
            fromStar = key.second,
            toStar = key.second + 1,
            resultInstanceId = upgraded.instanceId,
            consumedInstanceIds = consumed.map { it.instanceId },
            returnedItems = overflow,
        )
    }
}
