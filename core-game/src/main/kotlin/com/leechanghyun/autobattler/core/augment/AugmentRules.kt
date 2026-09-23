package com.leechanghyun.autobattler.core.augment

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitClass
import kotlin.random.Random

/**
 * 보유한 증강 목록을 읽어 게임 규칙이 필요로 하는 값을 돌려준다. 명세서 4-8, 로드맵 9단계.
 *
 * 수치는 전부 [AugmentTables] 에 있고 여기에는 **해석만** 있다.
 *
 * ### 의존 방향
 * 이 패키지가 보는 것은 `model` 과 `masterdata` 뿐이다. `synergy` 도 `economy` 도 보지 않는다.
 * 그래서 [UnitBuffs][com.leechanghyun.autobattler.core.synergy.UnitBuffs] 를 만들어 주지 않고
 * 원시 값만 내보낸다. 버프 객체를 조립하는 일은 시너지 엔진 한 곳이 계속 맡는다.
 * 반대로 뒀다면 `synergy → augment → synergy` 순환이 생긴다.
 *
 * 같은 이유로 경험치 지급도 여기서 하지 않는다. 레벨업 계산의 주인은
 * [Economy.grantExp][com.leechanghyun.autobattler.core.economy.Economy.grantExp] 이고,
 * 증강이 그 규칙을 다시 쓰면 두 벌이 된다. 여기서는 [expGrantedBy] 로 "몇 점인지"만 말한다.
 */
object AugmentRules {

    /** 매 라운드 수입에 더해지는 골드. 황금손길 +2, 심연의계약 +1. */
    fun goldPerRound(augments: List<AugmentDef>): Int =
        augments.sumOf { AugmentTables.GOLD_PER_ROUND[it.effect] ?: 0 }

    /** 이 증강을 고르는 순간 한 번 내는 체력. 대가가 없으면 0. */
    fun hpCostOf(augment: AugmentDef): Int = AugmentTables.HP_COST[augment.effect] ?: 0

    /**
     * 체력을 대가로 치르고 남는 체력. **최소 1 이다.**
     *
     * 0 이하로 내려가면 [PlayerState.isEliminated] 가 참이 되어 증강을 고른 순간 게임이 끝난다.
     * 고르면 지는 선택지는 선택지가 아니다. 왜 막아야 하는지는 [AugmentTables.HP_COST] 에 있다.
     */
    fun hpAfterCost(hp: Int, augment: AugmentDef): Int = (hp - hpCostOf(augment)).coerceAtLeast(1)

    /** 이 증강을 고르는 순간 한 번 받는 경험치. 없으면 0. */
    fun expGrantedBy(augment: AugmentDef): Int = AugmentTables.GRANT_EXP[augment.effect] ?: 0

    /** 시너지 id 별 요구 인원 감소량. 전열강화가 수호자를 1 깎는다. */
    fun thresholdDiscounts(augments: List<AugmentDef>): Map<String, Int> {
        val discounts = HashMap<String, Int>()
        augments.forEach { augment ->
            val (traitId, amount) = AugmentTables.TRAIT_DISCOUNT[augment.effect] ?: return@forEach
            discounts[traitId] = (discounts[traitId] ?: 0) + amount
        }
        return discounts
    }

    /** 아군 전체가 받는 방어력 가산. 강철의의지 +10. */
    fun teamArmorFlat(augments: List<AugmentDef>): Int =
        augments.sumOf { AugmentTables.TEAM_ARMOR[it.effect] ?: 0 }

    /** 계열 한정 공격속도 보정. 폭풍의가호가 폭풍의 부족에게 +15%. */
    fun attackSpeedByOrigin(augments: List<AugmentDef>): Map<Origin, Float> {
        val byOrigin = HashMap<Origin, Float>()
        augments.forEach { augment ->
            val (origin, percent) = AugmentTables.ORIGIN_ATTACK_SPEED[augment.effect] ?: return@forEach
            byOrigin[origin] = (byOrigin[origin] ?: 0f) + percent
        }
        return byOrigin
    }

    /**
     * 직업 한정 치명타 충전량. 사수의감각이 사수에게 20.
     *
     * "사수 **시너지** 유닛" 은 사수 태그를 단 유닛이라는 뜻이고, 사수 시너지가 발동했는지와는
     * 무관하다. 사수 1명짜리 보드에서도 그 1명은 치명타를 띄운다. 명세서가 발동 조건을 걸지
     * 않았고, [TeamBuffs][com.leechanghyun.autobattler.core.synergy.TeamBuffs] 가 애초에
     * 이 구분을 위해 태그별 버프를 따로 들고 있다.
     */
    fun critChargeByClass(augments: List<AugmentDef>): Map<UnitClass, Int> {
        val byClass = HashMap<UnitClass, Int>()
        augments.forEach { augment ->
            val (unitClass, charge) = AugmentTables.CLASS_CRIT_CHARGE[augment.effect] ?: return@forEach
            byClass[unitClass] = (byClass[unitClass] ?: 0) + charge
        }
        return byClass
    }

    /** 매 라운드 공짜로 쓸 수 있는 리롤 횟수. 재고정리가 1회. */
    fun freeRerollsPerRound(augments: List<AugmentDef>): Int =
        augments.count { it.effect == AugmentEffect.FREE_REROLL } * AugmentTables.FREE_REROLLS_PER_ROUND

    /**
     * 아직 고르지 않은 증강. 증강 후보는 여기서만 정한다.
     *
     * 후보 판정이 두 군데로 갈라지면 "화면에는 떴는데 고르면 거절당하는 카드" 가 생긴다.
     * [AugmentRoller] 는 이 목록을 받아 섞기만 한다.
     *
     * 체력이 모자라서 제외하는 규칙은 두지 않는다. 심연의계약의 대가는 [hpAfterCost] 가 최소 1로
     * 막으므로 언제나 고를 수 있고, 고를 수 없는 후보를 만들지 않는 편이 규칙이 하나 더 적다.
     */
    fun offerable(
        state: PlayerState,
        catalog: List<AugmentDef> = MasterData.augments,
    ): List<AugmentDef> {
        val taken = state.augments.map { it.id }.toSet()
        return catalog.filterNot { it.id in taken }
    }

    /**
     * 증강을 고른 직후의 상태. 명세서 5장 초안의 `applyEffect: (PlayerState) -> PlayerState` 자리다.
     *
     * 여기서 하는 일은 **고르는 순간 한 번만 일어나는 것** 셋뿐이다. 보유 목록에 넣고, 체력 대가를
     * 치르고, 아이템을 지급한다. 매 라운드 읽히는 효과(골드·리롤·시너지·전투)는 상태에 새기지 않고
     * 보유 목록에서 그때그때 읽는다. 새겨 두면 같은 값이 두 곳에 생겨 어긋난다.
     *
     * 경험치는 [expGrantedBy] 로 양만 내보내고 지급은 호출부가 한다. 이유는 클래스 KDoc 참고.
     *
     * @param random 아이템을 지급하는 증강만 쓴다. 나머지는 이 값을 건드리지 않는다.
     */
    fun applyOnPick(state: PlayerState, augment: AugmentDef, random: Random): PlayerState {
        val granted = grantedItemOf(augment, random)
        return state.copy(
            augments = state.augments + augment,
            hp = hpAfterCost(state.hp, augment),
            itemInventory = state.itemInventory + listOfNotNull(granted),
        )
    }

    /**
     * 이 증강이 지급하는 아이템 1개. 지급하지 않는 증강이면 null.
     *
     * 명세서의 "**보유** 아이템 컴포넌트 1개 무료 지급" 에서 `보유` 는 "이미 가진 것 중에서" 가
     * 아니라 "보유하게 해 준다" 로 읽는다. 앞 읽기를 택하면 가방이 빈 플레이어는 아무것도 못 받아
     * 증강이 무효가 되고, 같은 표의 대장장이의축복은 `보유` 없이 "완성 아이템 1개 무료 지급" 이라
     * 두 줄이 같은 문장 구조라는 점도 뒤 읽기를 가리킨다.
     */
    private fun grantedItemOf(augment: AugmentDef, random: Random): ItemDef? {
        val pool = when (augment.effect) {
            AugmentEffect.GRANT_COMPONENT -> MasterData.itemComponents
            AugmentEffect.GRANT_COMPLETED_ITEM -> MasterData.completedItems
            else -> return null
        }
        return pool[random.nextInt(pool.size)]
    }
}
