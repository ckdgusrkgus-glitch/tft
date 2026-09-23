package com.leechanghyun.autobattler.core.synergy

import com.leechanghyun.autobattler.core.augment.AugmentRules
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
 * 의존 방향은 synergy → model + masterdata + augment 뿐이고, combat 과 economy 가 이쪽을 본다.
 * 9단계에 붙은 `augment/` 도 model + masterdata 만 보므로 순환이 없다.
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
     * @param thresholdDiscounts 시너지 id 별 요구 인원 감소량. 9단계 전열강화가 수호자를 1 깎는다.
     *   5단계에 미리 자리를 뚫어 둔 덕에 9단계는 소비자 셋(전투 버프, 황금가문 골드,
     *   AI 임계값 돌파 보너스)을 한 줄도 고치지 않았다. 값은
     *   [com.leechanghyun.autobattler.core.augment.AugmentRules.thresholdDiscounts] 가 만든다.
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
    ): SynergyState = resolve(board, thresholdDiscounts, augments = emptyList())

    /**
     * 증강까지 반영한 해석. 로드맵 9단계.
     *
     * **전투 버프를 읽는 경로는 반드시 이쪽이어야 한다.** 보드만 받는 [resolve] 는 증강을 볼 수
     * 없으므로 강철의의지·폭풍의가호·사수의감각이 통째로 빠진 결과를 돌려준다. 10단계가 실제
     * 전투를 붙일 때 `resolve(board)` 를 집어 들면 증강이 조용히 사라진다.
     * `SynergyAugmentTest.증강 전투 버프는 보드만 보는 해석에는 없다` 가 그 차이를 지킨다.
     */
    fun resolve(state: PlayerState): SynergyState = resolve(
        board = state.board,
        thresholdDiscounts = AugmentRules.thresholdDiscounts(state.augments),
        augments = state.augments,
    )

    /**
     * 시너지와 증강을 같은 누적기에 넣어 한 번에 해석한다.
     *
     * 두 출처가 같은 [UnitBuffs] 항목에 들어가야 합산이 한 곳에서 일어난다. 따로 계산해 나중에
     * 더하면 [UnitBuffs.shieldPeriodTicks] 처럼 더하면 안 되는 항목이 잘못 합쳐진다.
     *
     * **증강 골드는 여기 들어오지 않는다.** [SynergyState.goldPerRound] 는 황금가문 시너지의
     * 몫이고, 증강 골드는 [com.leechanghyun.autobattler.core.economy.Economy.roundIncome] 이
     * 별도 항으로 더한다. 섞으면 "시너지 골드" 라는 말의 뜻이 두 가지가 되고, 5단계가 세운
     * `황금가문 골드는 이자와 섞이지 않는다` 의 검증 대상도 흐려진다.
     */
    private fun resolve(
        board: List<BoardUnit>,
        thresholdDiscounts: Map<String, Int>,
        augments: List<AugmentDef>,
    ): SynergyState {
        val traits = activeTraits(board, thresholdDiscounts)
        val accumulator = Accumulator()
        traits.filter { it.isActive }.forEach { accumulator.contribute(it) }
        accumulator.contributeAugments(augments)
        return SynergyState(
            activeTraits = traits,
            combat = accumulator.toTeamBuffs(),
            goldPerRound = accumulator.goldPerRound,
        )
    }

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

        /**
         * 증강 효과를 시너지와 같은 자리에 더한다. 명세서 4-8.
         *
         * [contribute] 의 `when` 에 끼우지 않는다. 그 분기는 `traitId` 로 갈라지고 `else` 에서
         * 터지는데, 증강에는 traitId 가 없다.
         *
         * 여기 있는 것은 **매 전투 읽히는 효과**뿐이다. 고르는 순간 한 번 일어나는 것(경험치,
         * 아이템, 체력 대가)은 [com.leechanghyun.autobattler.core.augment.AugmentRules.applyOnPick]
         * 이 이미 상태에 새겼으므로 여기서 다시 보면 두 번 적용된다.
         */
        fun contributeAugments(augments: List<AugmentDef>) {
            if (augments.isEmpty()) return

            val armor = AugmentRules.teamArmorFlat(augments)
            if (armor != 0) addTeam(UnitBuffs(armorFlat = armor))

            AugmentRules.attackSpeedByOrigin(augments).forEach { (origin, percent) ->
                addOrigin(origin, UnitBuffs(attackSpeedPercent = percent))
            }
            AugmentRules.critChargeByClass(augments).forEach { (unitClass, charge) ->
                addClass(unitClass, UnitBuffs(critChargePerAttack = charge))
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
