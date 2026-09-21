package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.Origin
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitClass

/**
 * 시너지 계산 엔진. 명세서 4-4, 로드맵 5단계.
 *
 * 명세서 6장이 `synergy/` 를 `combat/` · `economy/` 의 **형제**로 지정한 자리다.
 * 전투 패키지 안에 두면 황금가문(경제 효과)과 8단계 AI 스코어링이 전투를 의존하게 되어 레이어가 꼬인다.
 *
 * 의존 방향은 synergy → model + masterdata 뿐이고, combat 과 economy 가 이쪽을 본다. 순환이 없다.
 *
 * 전부 순수 함수다. 같은 보드를 넣으면 언제나 같은 결과가 나오므로 보드 구성만으로 단위테스트할 수 있다.
 */
object SynergyEngine {

    /** 태그별 인원. 세는 규칙은 [TraitTally] 참고. */
    fun tally(board: List<BoardUnit>): TraitTally = TraitTally.of(board)

    /**
     * 보드 위 시너지 상태. 구성원이 1명 이상인 시너지만 돌려준다. 미발동도 포함한다.
     *
     * 순서는 마스터 데이터 순서(계열 4종 → 직업 4종)라 화면 출력이 라운드마다 흔들리지 않는다.
     *
     * @param thresholdDiscounts 시너지 id 별 요구 인원 감소량. **9단계 전까지는 항상 비어 있다.**
     *   지금 넣어 두는 이유는 명세서 4-8 의 전열강화가 9단계에 왔을 때 소비자 셋(전투 버프,
     *   황금가문 골드, AI 임계값 돌파 보너스)을 동시에 고치는 일을 없애기 위해서다.
     *   기본값이 빈 맵이라 5단계 동작은 조금도 바뀌지 않는다.
     */
    fun activeTraits(
        board: List<BoardUnit>,
        thresholdDiscounts: Map<String, Int> = emptyMap(),
    ): List<ActiveTrait> {
        val tally = TraitTally.of(board)
        return MasterData.traits.mapNotNull { trait ->
            val count = tally.count(trait.id)
            if (count <= 0) null else ActiveTrait.of(trait, count, thresholdDiscounts[trait.id] ?: 0)
        }
    }

    /**
     * 5단계의 핵심. 보드만으로 결정되는 순수 함수다.
     *
     * 8단계 AI 스코어링이 `resolve(board + 후보유닛)` 으로 가상 보드를 물어볼 수 있도록
     * PlanningSession 이 아니라 보드 목록을 받는다.
     */
    fun resolve(
        board: List<BoardUnit>,
        thresholdDiscounts: Map<String, Int> = emptyMap(),
    ): SynergyState {
        val traits = activeTraits(board, thresholdDiscounts)
        val accumulator = Accumulator()
        traits.filter { it.isActive }.forEach { accumulator.contribute(it) }
        return SynergyState(
            activeTraits = traits,
            combat = accumulator.toTeamBuffs(),
            goldPerRound = accumulator.goldPerRound,
        )
    }

    /** 증강까지 반영한 해석. 호출부가 증강 목록을 따로 실어 나르지 않게 하는 어댑터다. */
    fun resolve(state: PlayerState): SynergyState =
        resolve(state.board, thresholdDiscountsFrom(state.augments))

    /**
     * 임계값을 깎는 증강 → 시너지 id 별 감소량.
     *
     * **9단계 전까지는 항상 빈 맵이다.** [AugmentDef] 에 파라미터 슬롯이 없어(id/name/description/
     * effectId 뿐) 어느 시너지를 얼마나 깎는지 적을 자리가 없기 때문이다. 그 슬롯을 만드는 것이
     * 9단계의 일이고, 5단계는 그것을 불가능하게 만들지만 않으면 된다.
     */
    internal fun thresholdDiscountsFrom(@Suppress("UNUSED_PARAMETER") augments: List<AugmentDef>): Map<String, Int> =
        emptyMap()

    /**
     * 발동한 시너지 하나의 효과를 모으는 누적기.
     *
     * [contribute] 의 `when` 이 8종을 전부 분기하고 `else` 에서 터진다. 시너지가 늘었는데 여기
     * 케이스를 안 만들면 "모든 시너지 id 가 엔진에서 처리된다" 테스트가 즉시 잡아낸다.
     */
    private class Accumulator {
        var teamWide: UnitBuffs = UnitBuffs.NONE
        val byOrigin = HashMap<Origin, UnitBuffs>()
        val byClass = HashMap<UnitClass, UnitBuffs>()
        val summons = mutableListOf<SummonSpec>()
        var goldPerRound: Int = 0

        fun contribute(active: ActiveTrait) {
            val tierIndex = active.tier - 1
            when (active.traitId) {
                Origin.MECHA.traitId -> {
                    addTeam(UnitBuffs(armorFlat = SynergyTables.MECHA_ARMOR[tierIndex]))
                    // 6인 효과의 거대 골렘은 명세만 내보내고 실제 소환은 10단계다. [SummonSpec] 참고.
                    if (active.tier >= active.effectiveThresholds.size) {
                        summons += SummonSpec(SynergyTables.GOLEM_UNIT_ID, count = 1, sourceTraitId = active.traitId)
                    }
                }

                Origin.ABYSSAL.traitId -> addTeam(
                    UnitBuffs(
                        shieldPercentOfMaxHp = SynergyTables.ABYSSAL_SHIELD_PERCENT[tierIndex],
                        shieldPeriodTicks = SynergyTables.ABYSSAL_SHIELD_PERIOD_TICKS,
                    ),
                )

                Origin.GOLDEN_HOUSE.traitId -> goldPerRound += SynergyTables.GOLDEN_HOUSE_GOLD[tierIndex]

                Origin.STORM_TRIBE.traitId -> addOrigin(
                    Origin.STORM_TRIBE,
                    UnitBuffs(
                        chainChargePerAttack = SynergyTables.STORM_CHARGE[tierIndex],
                        chainDamage = SynergyTables.STORM_DAMAGE[tierIndex],
                        chainTargets = SynergyTables.STORM_TARGETS[tierIndex],
                    ),
                )

                UnitClass.BLADE.traitId -> addClass(
                    UnitClass.BLADE,
                    UnitBuffs(attackPercent = SynergyTables.BLADE_ATTACK_PERCENT[tierIndex]),
                )

                UnitClass.ARCANIST.traitId -> addClass(
                    UnitClass.ARCANIST,
                    UnitBuffs(skillPowerPercent = SynergyTables.ARCANIST_SKILL_POWER_PERCENT[tierIndex]),
                )

                UnitClass.MARKSMAN.traitId -> addClass(
                    UnitClass.MARKSMAN,
                    UnitBuffs(attackSpeedPercent = SynergyTables.MARKSMAN_ATTACK_SPEED_PERCENT[tierIndex]),
                )

                UnitClass.WARDEN.traitId -> addClass(
                    UnitClass.WARDEN,
                    UnitBuffs(hpPercent = SynergyTables.WARDEN_HP_PERCENT[tierIndex]),
                )

                else -> error("시너지 ${active.traitId} 의 효과가 엔진에 구현되지 않았다")
            }
        }

        private fun addTeam(buffs: UnitBuffs) {
            teamWide += buffs
        }

        private fun addOrigin(origin: Origin, buffs: UnitBuffs) {
            byOrigin[origin] = (byOrigin[origin] ?: UnitBuffs.NONE) + buffs
        }

        private fun addClass(unitClass: UnitClass, buffs: UnitBuffs) {
            byClass[unitClass] = (byClass[unitClass] ?: UnitBuffs.NONE) + buffs
        }

        fun toTeamBuffs() = TeamBuffs(
            teamWide = teamWide,
            byOrigin = byOrigin.toMap(),
            byClass = byClass.toMap(),
            summons = summons.toList(),
        )
    }
}
