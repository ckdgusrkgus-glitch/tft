package com.leechanghyun.autobattler.core.ai

import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.economy.ShopOdds
import com.leechanghyun.autobattler.core.items.ItemStats
import com.leechanghyun.autobattler.core.masterdata.traitId
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.core.model.UnitDef
import com.leechanghyun.autobattler.core.synergy.SynergyEngine
import com.leechanghyun.autobattler.core.synergy.TraitTally
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import kotlin.math.abs

/**
 * 상점 유닛 1기에 매긴 점수. 명세서 4-7 의 다섯 항을 **따로 들고 다닌다.**
 *
 * 합계만 돌려주면 "왜 이걸 샀는지"를 테스트도 11단계 튜닝도 알 수 없고, 어느 항이 고장 났는지
 * 지목할 수도 없다. 항별로 단언할 수 있어야 스코어링이 죽은 코드가 되는 것을 막는다.
 */
data class BotScore(
    val unitId: String,
    /** 코스트 대비 현재 레벨 적정성. 적정치에서 멀수록 음수다. */
    val levelFitness: Double,
    /** 이미 보유한 동일 유닛 1성 개수 x 스타업 임박 가중치. */
    val starUp: Double,
    /** 보드의 계열/직업 태그와 겹치는 정도 x 시너지 가중치. */
    val synergyOverlap: Double,
    /** 임계값(2/4/6)을 새로 돌파시키면 붙는 보너스. */
    val thresholdBreak: Double,
    /** 코스트 대비 스탯. **초반 예외에서만** 쓰인다. */
    val costEfficiency: Double,
    /** 남은 골드로 감당 가능한가. 불가능하면 합계가 음의 무한대다. */
    val affordable: Boolean,
    /** 명세서 4-7 의 초반 예외가 적용된 점수인가. */
    val earlyGame: Boolean,
) {
    /**
     * 명세서 4-7 의 최종 점수.
     *
     * 감당 불가는 **음의 무한대**다. 큰 음수가 아니라 무한대인 이유는, 다른 항의 합이 아무리 커도
     * 살 수 없다는 사실을 덮을 수 없어야 하기 때문이다. 명세서도 `-무한대`라고 적었다.
     */
    val total: Double
        get() = when {
            !affordable -> Double.NEGATIVE_INFINITY
            earlyGame -> costEfficiency
            else -> levelFitness + starUp + synergyOverlap + thresholdBreak
        }

    /** 실제로 살 만한가. 감당 가능하고 [BotWeights.MIN_BUY_SCORE] 이상이어야 한다. */
    val worthBuying: Boolean get() = affordable && total >= BotWeights.MIN_BUY_SCORE
}

/**
 * AI 봇 구매 스코어링. 명세서 4-7, 로드맵 8단계.
 *
 * ```
 * score(유닛) =
 *     (유닛_코스트 대비 현재_레벨_적정성 가중치)
 *   + (이미_보유한_동일_유닛_개수 * 스타업_임박_가중치)
 *   + (현재_보드의_계열/직업_태그와_겹치는_정도 * 시너지_가중치)
 *   + (해당_유닛이_임계값(2/4/6)을_새로_돌파시키는가 ? 보너스 : 0)
 *   - (남은_골드로_감당_불가능하면 -무한대)
 * ```
 *
 * ### 전부 순수 함수이고 [com.leechanghyun.autobattler.core.planning.PlanningSession] 을 모른다
 * 받는 것은 [UnitDef] + [PlayerState] + [BotPlan] 뿐이다. 세션 없이 항 하나씩 단위테스트할 수 있고,
 * 세션을 만지는 일은 [BotBrain] 혼자 한다. 의존 방향은 ai → {planning, synergy, economy, items} 뿐이고
 * **combat 은 보지 않는다.** 8단계 봇은 전투를 몰라도 되고, 알면 레이어가 꼬인다.
 *
 * ### 난수가 한 줄도 없다
 * 동점은 호출부가 슬롯 번호로 끊는다([BotBrain]). 이 저장소의 결정론 규칙을 그대로 따른다.
 */
object BotScoring {

    /**
     * 레벨 [level] 상점이 내놓는 **기대 코스트**. 명세서 4-1 확률표에서 유도한다.
     *
     * 명세서 4-7 의 "코스트 대비 현재 레벨 적정성"을 읽는 방법이다. 새 표를 만들지 않고
     * 이미 확정된 상점 확률표를 쓴다. 레벨 2 는 1.0, 레벨 5 는 1.79, 레벨 9 는 3.0 이 된다.
     * 즉 레벨이 오를수록 높은 코스트가 적정해지고, 레벨 9 에서 1코스트를 집는 것은 -2점이다.
     */
    fun expectedCost(level: Int): Double =
        ShopOdds.oddsFor(level)
            .withIndex()
            .sumOf { (index, probability) -> (index + UnitDef.MIN_COST) * probability }

    /** 1항. 코스트가 [expectedCost] 에서 멀수록 깎인다. 적정이면 0, 음수만 나온다. */
    fun levelFitnessTerm(def: UnitDef, level: Int): Double =
        -abs(def.cost - expectedCost(level)) * BotWeights.LEVEL_FITNESS_PER_COST

    /**
     * 2항. 이미 가진 같은 유닛 **1성** 개수 x 스타업 임박 가중치.
     *
     * 1성만 세는 것이 중요하다. 상점에서 사는 카드는 항상 1성이고 1성끼리만 합쳐지므로,
     * 2성을 들고 있는 것은 이번 구매의 합성을 앞당기지 않는다. 6단계가 구매 즉시 합성하므로
     * 이 값은 0, 1, 2 중 하나이고 2일 때가 명세서의 "2성 완성 임박"이다.
     */
    fun starUpTerm(def: UnitDef, player: PlayerState): Double {
        val copies = (player.bench + player.board)
            .count { it.unitDef.id == def.id && it.starLevel == 1 }
        return copies * BotWeights.STAR_UP_PER_COPY
    }

    /**
     * 3항. 보드의 계열/직업 태그와 겹치는 정도 x 시너지 가중치.
     *
     * 계열과 직업을 **따로** 센다. 한 유닛이 두 태그를 가지므로 양쪽 모두에서 점수를 받을 수 있다.
     * [BotPlan.priorityTraitIds] 에 든 태그는 [BotWeights.PRIORITY_TRAIT_MULTIPLIER] 배를 받는다.
     * 명세서 4-7 의 "그 태그에 해당하는 유닛의 가중치를 높인다"가 이것이다.
     */
    fun synergyOverlapTerm(def: UnitDef, player: PlayerState, plan: BotPlan): Double =
        synergyOverlapOn(def, player.board, plan)

    /**
     * 3항을 임의의 보드에 대해 계산한다.
     *
     * [BotBrain] 의 배치 단계가 "아직 절반만 채운 가상 보드"에 대고 물어보기 때문에 따로 낸다.
     * 두 곳이 서로 다른 식을 쓰면 봇이 살 때와 올릴 때 다른 것을 좋아하게 된다.
     */
    fun synergyOverlapOn(def: UnitDef, board: List<BoardUnit>, plan: BotPlan): Double {
        val tally = TraitTally.of(board)

        fun contribution(traitId: String): Double {
            val members = tally.count(traitId)
            if (members <= 0) return 0.0
            val multiplier =
                if (traitId in plan.priorityTraitIds) BotWeights.PRIORITY_TRAIT_MULTIPLIER else 1.0
            return members * BotWeights.SYNERGY_PER_MEMBER * multiplier
        }

        return contribution(def.origin.traitId) + contribution(def.unitClass.traitId)
    }

    /**
     * 4항. 이 유닛을 보드에 올리면 임계값을 새로 넘기는가. 넘기는 단계 수만큼 보너스다.
     *
     * 5단계가 [SynergyEngine.activeTraits] 를 "보드 목록만 받는 순수 함수"로 만들어 둔 것이
     * 여기서 쓰인다. 가상 보드를 그대로 물어본다.
     *
     * 좌표는 아무 칸이나 쓴다. [TraitTally] 는 "보드에 있는가"만 보고 좌표가 겹치는지는 세지 않으므로
     * 집계 결과가 달라지지 않는다. 이미 보드에 같은 종이 있으면 종 수가 늘지 않아 0이 되는데,
     * 그것이 옳다. 복제본의 가치는 2항이 이미 값을 매겼다.
     */
    fun thresholdBreakTerm(def: UnitDef, player: PlayerState): Double =
        thresholdBreakOn(def, player.board)

    /** 4항을 임의의 보드에 대해 계산한다. 이유는 [synergyOverlapOn] 과 같다. */
    fun thresholdBreakOn(def: UnitDef, board: List<BoardUnit>): Double =
        tiersGainedBy(def, board) * BotWeights.THRESHOLD_BREAK_BONUS

    /** [def] 를 [board] 에 올렸을 때 새로 올라가는 시너지 단계의 총합. */
    fun tiersGainedBy(def: UnitDef, board: List<BoardUnit>): Int {
        val hypothetical = board + BoardUnit(
            instanceId = HYPOTHETICAL_INSTANCE_ID,
            unitDef = def,
            position = HexBoard.coords.first(),
        )

        val before = SynergyEngine.activeTraits(board).associate { it.traitId to it.tier }
        return SynergyEngine.activeTraits(hypothetical).sumOf { trait ->
            (trait.tier - (before[trait.traitId] ?: 0)).coerceAtLeast(0)
        }
    }

    /** 5항. 사고 나서도 [BotWeights.GOLD_RESERVE] 가 남는가. 명세서 4-7 의 이자 확보 규칙. */
    fun canAfford(def: UnitDef, player: PlayerState): Boolean =
        player.gold - def.cost >= BotWeights.GOLD_RESERVE

    /**
     * 유닛 1종의 전투력 근사치. 체력과 초당 피해를 한 숫자로 합친다.
     *
     * 명세서 4-7 이 두 번 쓰는 모호한 문구("코스트 대비 스탯이 좋은", "가장 보유 스탯이 좋은 유닛")를
     * 읽는 하나의 척도다. 두 곳이 서로 다른 기준을 쓰면 봇의 판단이 스스로 어긋난다.
     */
    fun power(def: UnitDef): Double =
        def.baseHp * BotWeights.HP_PER_POWER +
            def.baseAttack * def.attackSpeed * BotWeights.DPS_PER_POWER

    /**
     * 명세서 4-7 의 초반 예외가 쓰는 "코스트 대비 스탯".
     *
     * 골드 1당 얼마나 강한가다. 초반 1~3라운드는 보드가 비어 시너지를 말할 수 없으므로
     * 태그를 보지 않고 이것만 본다.
     */
    fun costEfficiency(def: UnitDef): Double = power(def) / def.cost

    /**
     * 개체 1기의 **현재** 전투력. 성 등급 배율과 장착 아이템이 반영된다.
     *
     * 명세서 4-7 "완성 아이템은 가장 보유 스탯이 좋은 유닛에게 우선 장착"의 "보유 스탯"이다.
     * 전투가 쓰는 [UnitBuffs.scale] 을 그대로 써서, 봇이 보는 강함과 실제 전투 수치가 갈라지지 않게 한다.
     */
    fun instancePower(unit: BoardUnit): Double {
        val buffs = ItemStats.buffsOf(unit.items)
        val hp = UnitBuffs.scale(unit.hp, buffs.hpFlat, buffs.hpPercent)
        val attack = UnitBuffs.scale(unit.attack, buffs.attackFlat, buffs.attackPercent)
        val attackSpeed = unit.unitDef.attackSpeed * (1f + buffs.attackSpeedPercent)
        return hp * BotWeights.HP_PER_POWER + attack * attackSpeed * BotWeights.DPS_PER_POWER
    }

    /** 다섯 항을 전부 계산한 점수. */
    fun score(def: UnitDef, player: PlayerState, plan: BotPlan): BotScore = BotScore(
        unitId = def.id,
        levelFitness = levelFitnessTerm(def, player.level),
        starUp = starUpTerm(def, player),
        synergyOverlap = synergyOverlapTerm(def, player, plan),
        thresholdBreak = thresholdBreakTerm(def, player),
        costEfficiency = costEfficiency(def),
        affordable = canAfford(def, player),
        earlyGame = plan.isEarlyGame,
    )

    /** 가상 보드에만 쓰는 개체 id. 실제 개체와 겹치지 않도록 실제 경로가 만들지 않는 모양으로 둔다. */
    internal const val HYPOTHETICAL_INSTANCE_ID = "__bot_candidate__"
}
