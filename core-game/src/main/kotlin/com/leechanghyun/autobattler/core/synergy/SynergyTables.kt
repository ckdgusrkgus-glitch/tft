package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.UnitClass

/**
 * 시너지 수치표. **아래 숫자는 전부 임의 초기값이고 11단계 밸런스 튜닝에서 조정한다.**
 *
 * 명세서 4-4 는 "공격력 증가"처럼 방향만 적고 수치를 적지 않았다. 명세서가 그런 값은 11단계에서
 * 조정한다고 정해 두었고, 골드 연승 보너스표가 이미 같은 관행을 쓴다.
 *
 * ### 왜 [com.leechanghyun.autobattler.core.model.TraitDef] 에 넣지 않는가
 * TraitDef 는 Room 에 저장되는 행이다. 엔티티·매퍼·인코딩 왕복 테스트가 전부 딸려 있고
 * 그 코드는 `app` 모듈에 있어 이 환경에서 컴파일조차 되지 않는다. 문구는 마스터 데이터에,
 * 숫자는 여기에 둔다. 그래야 11단계가 숫자를 만질 때 DB 스키마를 건드리지 않는다.
 *
 * 모든 리스트의 색인은 `ActiveTrait.tier - 1` 이다.
 */
object SynergyTables {

    /** 시너지별 적용 범위. 범위가 명세서에 없는 6종은 해석이다. [TraitScope] 참고. */
    val SCOPE: Map<String, TraitScope> = mapOf(
        Origin.MECHA.traitId to TraitScope.TEAM,
        Origin.ABYSSAL.traitId to TraitScope.TEAM,
        Origin.GOLDEN_HOUSE.traitId to TraitScope.ECONOMY,
        Origin.STORM_TRIBE.traitId to TraitScope.TAGGED,
        UnitClass.BLADE.traitId to TraitScope.TAGGED,
        UnitClass.ARCANIST.traitId to TraitScope.TAGGED,
        UnitClass.MARKSMAN.traitId to TraitScope.TAGGED,
        UnitClass.WARDEN.traitId to TraitScope.TAGGED,
    )

    // --- 계열 4종 (임계값 2 / 4 / 6) ---

    /**
     * 기계공학자: 아군 전체 방어력/마법저항력 점수.
     *
     * `100 / (100 + n)` 공식이므로 피해가 각각 13% / 26% / 37.5% 줄어든다.
     */
    val MECHA_ARMOR: List<Int> = listOf(15, 35, 60)

    /** 기계공학자 6단계의 거대 골렘. 5단계는 명세만 내보내고 실제 소환은 10단계다. [SummonSpec] 참고. */
    const val GOLEM_UNIT_ID = "summon_giant_golem"

    /** 심연의 아이들: 주기마다 채워지는 쉴드량(최대 체력 비율). */
    val ABYSSAL_SHIELD_PERCENT: List<Float> = listOf(0.08f, 0.15f, 0.25f)

    /** 쉴드 갱신 주기. 30틱 = 3초. 단계와 무관하게 고정이다. */
    const val ABYSSAL_SHIELD_PERIOD_TICKS = 30

    /** 황금가문: 라운드마다 받는 추가 골드. */
    val GOLDEN_HOUSE_GOLD: List<Int> = listOf(2, 4, 7)

    /**
     * 폭풍의 부족: 기본 공격 1회당 충전량. 100 을 채우면 연쇄 번개가 터진다.
     *
     * 명세서의 "일정 확률로"를 그대로 옮긴 숫자다. 20 은 20% 와 기대 발동 빈도가 같고 분산만 0 이다.
     * 11단계는 확률을 튜닝하듯 이 값을 튜닝하면 된다.
     */
    val STORM_CHARGE: List<Int> = listOf(20, 35, 50)
    val STORM_DAMAGE: List<Int> = listOf(60, 110, 180)
    val STORM_TARGETS: List<Int> = listOf(1, 2, 3)

    // --- 직업 4종 (임계값 2 / 4) ---

    val BLADE_ATTACK_PERCENT: List<Float> = listOf(0.15f, 0.35f)

    val ARCANIST_SKILL_POWER_PERCENT: List<Float> = listOf(0.20f, 0.45f)

    /**
     * 사수: 공격속도 비율.
     *
     * **양자화에 주의해야 하는 유일한 수치다.** 공격 간격이 정수 틱이라 작은 비율은 통째로 사라진다.
     * 로스터의 사수 6종은 공격속도가 0.58 ~ 0.75 이고, +5% 를 걸면 대부분의 간격이 **전혀 변하지
     * 않는다.** (0.70 은 10/0.735 = 13.6 → 14틱으로 버프 전과 같다.)
     * 아래 15% / 35% 는 사수 6종 전부의 간격이 실제로 줄어드는 값으로 골랐다.
     * 11단계가 이 값을 낮추면 `SynergyCombatTest` 의 로스터 전수 테스트가 터진다.
     * 더 고운 해상도가 필요해지면 그때 1/100틱 정수 스케줄러로 바꾼다. 틱 루프의 심장을 고치는
     * 일이라 5단계에 끼워 넣지 않는다.
     */
    val MARKSMAN_ATTACK_SPEED_PERCENT: List<Float> = listOf(0.15f, 0.35f)

    val WARDEN_HP_PERCENT: List<Float> = listOf(0.20f, 0.45f)

    init {
        // 11단계 가드레일. 표가 시너지 단계 수와 어긋나거나 단계가 거꾸로 가면 여기서 바로 터진다.
        requireTiers(Origin.MECHA.traitId, MECHA_ARMOR.map { it.toFloat() })
        requireTiers(Origin.ABYSSAL.traitId, ABYSSAL_SHIELD_PERCENT)
        requireTiers(Origin.GOLDEN_HOUSE.traitId, GOLDEN_HOUSE_GOLD.map { it.toFloat() })
        requireTiers(Origin.STORM_TRIBE.traitId, STORM_CHARGE.map { it.toFloat() })
        requireTiers(Origin.STORM_TRIBE.traitId, STORM_DAMAGE.map { it.toFloat() })
        requireTiers(Origin.STORM_TRIBE.traitId, STORM_TARGETS.map { it.toFloat() })
        requireTiers(UnitClass.BLADE.traitId, BLADE_ATTACK_PERCENT)
        requireTiers(UnitClass.ARCANIST.traitId, ARCANIST_SKILL_POWER_PERCENT)
        requireTiers(UnitClass.MARKSMAN.traitId, MARKSMAN_ATTACK_SPEED_PERCENT)
        requireTiers(UnitClass.WARDEN.traitId, WARDEN_HP_PERCENT)

        require(SCOPE.keys == MasterData.traits.map { it.id }.toSet()) {
            "시너지 범위표가 마스터 데이터의 시너지 8종을 빠짐없이 덮지 않는다"
        }
        require(MasterData.units.none { it.id == GOLEM_UNIT_ID }) {
            "소환물 $GOLEM_UNIT_ID 가 로스터에 들어가면 상점과 공용 풀이 오염된다"
        }
    }

    /** 수치표 한 줄이 해당 시너지의 단계 수와 길이가 같고, 값이 전부 양수이며 단계마다 커지는지 본다. */
    private fun requireTiers(traitId: String, values: List<Float>) {
        val tiers = MasterData.trait(traitId).thresholds.size
        require(values.size == tiers) { "시너지 $traitId 의 수치표 길이 ${values.size} 가 단계 수 $tiers 와 다르다" }
        require(values.all { it > 0f }) { "시너지 $traitId 의 수치에 0 이하가 있다" }
        require(values.zipWithNext().all { (a, b) -> b > a }) { "시너지 $traitId 의 수치가 단계마다 커지지 않는다" }
    }
}
