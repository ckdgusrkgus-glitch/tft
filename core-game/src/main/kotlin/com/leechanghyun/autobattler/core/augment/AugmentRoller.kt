package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.masterdata.AUGMENT_CHOICES_PER_ROUND
import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.PlayerState
import kotlin.random.Random

/**
 * 증강 후보 3개를 뽑는다. 명세서 4-8: 매번 3개 중 1개, 영구 지속.
 *
 * 후보가 될 자격은 [AugmentRules.offerable] 이 정하고 여기서는 그 목록에서 겹치지 않게 뽑기만
 * 한다. 자격 판정이 두 곳에 있으면 화면에 떴는데 고를 수 없는 카드가 생긴다.
 *
 * ### id 로 정렬한 뒤에 뽑는다
 * 같은 난수 씨앗이 앱과 테스트에서 같은 후보를 내려면 입력 순서가 같아야 한다. 그런데 앱은
 * Room 에서 `ORDER BY id` 로 읽고([com.leechanghyun.autobattler.data.local.MasterDataDao]),
 * 테스트는 [MasterData.augments] 의 명세서 표 순서를 쓴다. 두 순서가 다르다. 뽑기 직전에 id 로
 * 정렬해 그 차이를 없앤다. 이 한 줄을 지워도 아무 테스트가 빨개지지 않으므로
 * `AugmentRollerTest.섞인 목록으로 만들어도 같은 씨앗이면 같은 후보가 나온다` 가 지킨다.
 *
 * ### 상점 난수와 섞지 않는다
 * [ShopRoller][com.leechanghyun.autobattler.core.economy.ShopRoller] 와 [Random] 을 공유하면
 * 증강을 뽑을 때마다 상점 스트림이 밀려 8단계 AI 측정값이 통째로 움직인다. 실제로 재어 봤고
 * 봇의 목표 태그 일치율이 0.50 → 0.52 로 흔들렸다. 별도 인스턴스를 받는다.
 */
class AugmentRoller(
    private val catalog: List<AugmentDef> = MasterData.augments,
    /**
     * 증강이 쓰는 **유일한** 난수원. 후보 뽑기와 아이템 지급(속성연마·대장장이의축복)이
     * 같은 스트림을 쓴다. 씨앗 하나로 증강 쪽 무작위를 전부 재현할 수 있다.
     */
    val random: Random = Random.Default,
) {

    /**
     * 이 플레이어에게 제시할 증강 후보.
     *
     * @throws IllegalArgumentException 남은 후보가 [count] 보다 적을 때. 10종에서 3라운드 동안
     *   3개를 고르므로 남은 후보는 최소 7개다. 이 예외가 나면 증강 표나 발동 라운드 수가 바뀐 것이다.
     */
    fun roll(state: PlayerState, count: Int = AUGMENT_CHOICES_PER_ROUND): List<AugmentDef> {
        val candidates = AugmentRules.offerable(state, catalog).sortedBy { it.id }
        require(candidates.size >= count) {
            "증강 후보가 ${candidates.size} 개뿐이라 $count 개를 제시할 수 없다"
        }
        return candidates.shuffled(random).take(count)
    }
}
