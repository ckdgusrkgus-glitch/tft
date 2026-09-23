package com.leechanghyun.autobattler.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leechanghyun.autobattler.core.augment.AugmentRoller
import com.leechanghyun.autobattler.core.board.DropTarget
import com.leechanghyun.autobattler.core.economy.Economy
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.planning.PlanningError
import com.leechanghyun.autobattler.core.planning.PlanningResult
import com.leechanghyun.autobattler.core.planning.PlanningSession
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.AugmentDef
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.data.repository.GameDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 상점 1칸의 표시용 데이터. */
data class ShopSlotUi(
    val index: Int,
    val unitId: String?,
    val name: String,
    val cost: Int,
    val origin: String,
    val unitClass: String,
    val skillName: String,
    val purchased: Boolean,
    /** 지금 살 수 있는지. 골드 부족, 벤치 만석, 이미 구매면 false 다. */
    val buyable: Boolean,
)

/** 보드에 올라간 유닛의 표시용 데이터. */
data class PlacedUnitUi(
    val instanceId: String,
    val coord: HexCoord,
    val name: String,
    val cost: Int,
    val starLevel: Int,
    val sellPrice: Int,
)

/**
 * 시너지 패널 한 줄의 표시용 데이터.
 *
 * 값은 전부 `core-game` 의 시너지 엔진에서 이미 해석돼 온다. 이 화면은 인원을 세지도,
 * 임계값을 비교하지도 않는다.
 *
 * @param tier 0 이면 미발동이다. 회색으로 "수호자 1/2" 를 그린다.
 */
data class SynergyUi(
    val name: String,
    val memberCount: Int,
    val tier: Int,
    val activeThreshold: Int?,
    val nextThreshold: Int?,
    val effect: String?,
)

/**
 * 증강 1개의 표시용 데이터. 로드맵 9단계, 명세서 4-8.
 *
 * 효과 해석은 전부 `core-game` 의 증강 규칙이 이미 했다. 이 화면은 이름과 설명만 그린다.
 */
data class AugmentUi(
    val id: String,
    val name: String,
    val description: String,
)

/** 벤치 1칸의 표시용 데이터. */
data class BenchUnitUi(
    val instanceId: String,
    val name: String,
    val cost: Int,
    val starLevel: Int,
    val origin: String,
    val unitClass: String,
    /** 판매 시 돌려받는 골드. */
    val sellPrice: Int,
)

/**
 * 상점 화면 상태.
 *
 * @param expToNext 다음 레벨까지 남은 경험치. 최대 레벨이면 null.
 * @param message 조작 실패를 알리는 한 줄 메시지. 표시한 뒤 [ShopViewModel.consumeMessage] 로 지운다.
 * @param roundLabel "2-1" 같은 스테이지 표기. 시작 전에는 "-" 다. 화면이 라운드를 세지 않는다.
 * @param pendingAugments 지금 골라야 하는 증강 후보. 비어 있지 않으면 모달을 띄운다.
 * @param rerollCost 지금 리롤에 드는 골드. 증강 재고정리가 0 으로 만들 수 있어 상수를 쓰지 않는다.
 */
data class ShopUiState(
    val isLoading: Boolean = true,
    val roundLabel: String = "-",
    val gold: Int = 0,
    val hp: Int = PlayerState.STARTING_HP,
    val level: Int = 1,
    val exp: Int = 0,
    val expToNext: Int? = null,
    val slots: List<ShopSlotUi> = emptyList(),
    val bench: List<BenchUnitUi> = emptyList(),
    val benchCapacity: Int = PlayerState.BENCH_SIZE,
    val board: List<PlacedUnitUi> = emptyList(),
    val boardCapacity: Int = 1,
    /** 보드에 구성원이 한 명이라도 있는 시너지. 미발동도 포함한다. */
    val synergies: List<SynergyUi> = emptyList(),
    /** 탭으로 고른 유닛. 판매 버튼이 이 유닛을 대상으로 한다. */
    val selectedId: String? = null,
    val poolRemaining: Int = 0,
    /** 지금까지 고른 증강. 로드맵 9단계. */
    val augments: List<AugmentUi> = emptyList(),
    val pendingAugments: List<AugmentUi> = emptyList(),
    val rerollCost: Int = EconomyRules.REROLL_COST,
    val message: String? = null,
) {
    /**
     * 증강을 고르기 전에는 다음 라운드로 넘어갈 수 없다. 명세서 7장이 증강 화면을 **모달**로
     * 지정했기 때문이다. 판단은 `core-game` 이 하고 화면은 그 값을 그대로 쓴다.
     */
    val awaitingAugmentChoice: Boolean get() = pendingAugments.isNotEmpty()

    val canReroll: Boolean get() = gold >= rerollCost
    val canBuyExp: Boolean get() = gold >= EconomyRules.BUY_EXP_COST && level < PlayerState.MAX_LEVEL
    val benchIsFull: Boolean get() = bench.size >= benchCapacity

    /** 선택된 유닛. 벤치와 보드 어느 쪽에 있어도 찾는다. */
    val selectedUnit: SelectedUnitUi?
        get() {
            val id = selectedId ?: return null
            bench.firstOrNull { it.instanceId == id }?.let {
                return SelectedUnitUi(id, it.name, it.sellPrice, onBoard = false)
            }
            board.firstOrNull { it.instanceId == id }?.let { placed ->
                return SelectedUnitUi(id, placed.name, placed.sellPrice, onBoard = true)
            }
            return null
        }
}

/** 선택된 유닛의 표시용 요약. */
data class SelectedUnitUi(
    val instanceId: String,
    val name: String,
    val sellPrice: Int,
    val onBoard: Boolean,
)

/**
 * 로드맵 2단계 화면. 상점에서 유닛을 사 벤치에 올리고, 팔고, 리롤하고, 경험치를 산다.
 *
 * 게임 규칙은 전부 [PlanningSession] 에 있고 이 클래스는 그 결과를 화면용 데이터로 옮기기만 한다.
 * 규칙이 ViewModel 로 새어 들어오면 안드로이드 없이 테스트할 수 없게 되므로 경계를 지킨다.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val repository: GameDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    private lateinit var session: PlanningSession
    private lateinit var pool: UnitPool
    private var skillNamesById: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val units = repository.units()
            skillNamesById = repository.skills().associate { it.id to it.name }

            pool = UnitPool(units = units)
            session = PlanningSession(
                pool = pool,
                roller = ShopRoller(pool),
                initialPlayer = PlayerState(
                    playerId = "player",
                    displayName = "나",
                    isBot = false,
                ),
                // Room 에서 읽은 증강 목록을 쓴다. 마스터 데이터 상수를 직접 보면 DB 시딩이
                // 어긋나도 화면만 멀쩡해 보인다. 여기서 한 번만 만들어 매 라운드 재조회를 피한다.
                augmentRoller = AugmentRoller(catalog = repository.augments()),
            )
            session.nextRound()
            _uiState.update { it.copy(isLoading = false) }
            publish()
        }
    }

    fun buy(slotIndex: Int) = runAction { session.buy(slotIndex) }

    fun sell(instanceId: String) = runAction { session.sell(instanceId) }

    /**
     * 드래그를 놓았을 때 부르는 함수. 놓은 곳이 보드면 배치, 벤치면 회수다.
     *
     * 어디에 놓였는지 판단하는 계산은 [com.leechanghyun.autobattler.core.board.PlayfieldLayout] 이 한다.
     */
    fun onDrop(instanceId: String, target: DropTarget) = runAction {
        when (target) {
            is DropTarget.Board -> session.moveToBoard(instanceId, target.coord)
            DropTarget.Bench -> session.returnToBench(instanceId)
        }
    }

    fun reroll() = runAction { session.reroll() }

    /** 제시된 증강 하나를 고른다. 로드맵 9단계 완료 기준. */
    fun chooseAugment(augmentId: String) = runAction { session.chooseAugment(augmentId) }

    fun buyExp() = runAction { session.buyExp() }

    /** 다음 라운드로 넘어간다. 수입과 자동 경험치를 받고 상점이 새로 채워진다. */
    fun nextRound() {
        session.nextRound()
        publish()
    }

    /** 탭으로 유닛을 고르거나 선택을 해제한다. */
    fun select(instanceId: String?) = _uiState.update { it.copy(selectedId = instanceId) }

    /** 선택된 유닛을 판다. */
    fun sellSelected() {
        val id = _uiState.value.selectedId ?: return
        runAction { session.sell(id) }
        _uiState.update { it.copy(selectedId = null) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun runAction(action: () -> PlanningResult) {
        val result = action()
        publish(message = (result as? PlanningResult.Failure)?.error?.toMessage())
    }

    private fun publish(message: String? = null) {
        val player = session.player
        _uiState.update { state ->
            state.copy(
                roundLabel = session.roundLabel,
                gold = player.gold,
                hp = player.hp,
                level = player.level,
                exp = player.exp,
                expToNext = Economy.expToNextLevel(player),
                slots = buildSlots(),
                bench = buildBench(),
                board = buildBoard(),
                boardCapacity = player.boardCapacity,
                synergies = buildSynergies(),
                poolRemaining = pool.totalRemaining(),
                augments = player.augments.map { it.toUi() },
                pendingAugments = session.pendingAugments.orEmpty().map { it.toUi() },
                rerollCost = session.rerollCost,
                selectedId = state.selectedId?.takeIf { id ->
                    (player.bench + player.board).any { it.instanceId == id }
                },
                message = message,
            )
        }
    }

    private fun buildSlots(): List<ShopSlotUi> {
        val player = session.player
        val benchFull = player.bench.size >= PlayerState.BENCH_SIZE
        return session.offer.slots.mapIndexed { index, slot ->
            val unit = slot.unit
            ShopSlotUi(
                index = index,
                unitId = unit?.id,
                name = unit?.name.orEmpty(),
                cost = unit?.cost ?: 0,
                origin = unit?.origin?.displayName.orEmpty(),
                unitClass = unit?.unitClass?.displayName.orEmpty(),
                skillName = unit?.let { skillNamesById[it.skillId] }.orEmpty(),
                purchased = slot.purchased,
                buyable = unit != null &&
                    !slot.purchased &&
                    !benchFull &&
                    player.gold >= unit.cost,
            )
        }
    }

    private fun AugmentDef.toUi() = AugmentUi(id = id, name = name, description = description)

    private fun buildBench(): List<BenchUnitUi> = session.player.bench.map { unit ->
        BenchUnitUi(
            instanceId = unit.instanceId,
            name = unit.unitDef.name,
            cost = unit.unitDef.cost,
            starLevel = unit.starLevel,
            origin = unit.unitDef.origin.displayName,
            unitClass = unit.unitDef.unitClass.displayName,
            sellPrice = Economy.sellPrice(unit),
        )
    }

    /**
     * 시너지 패널. 로드맵 5단계.
     *
     * 규칙과 수치는 전부 [com.leechanghyun.autobattler.core.synergy.SynergyEngine] 이 이미 계산했다.
     * 여기서 다시 세면 안드로이드 없이 검증할 수 없는 로직이 생긴다.
     */
    private fun buildSynergies(): List<SynergyUi> = session.synergy.activeTraits.map { trait ->
        SynergyUi(
            name = trait.name,
            memberCount = trait.memberCount,
            tier = trait.tier,
            activeThreshold = trait.activeThreshold,
            nextThreshold = trait.nextThreshold,
            effect = trait.effectText,
        )
    }

    private fun buildBoard(): List<PlacedUnitUi> = session.player.board.mapNotNull { unit ->
        val coord = unit.position ?: return@mapNotNull null
        PlacedUnitUi(
            instanceId = unit.instanceId,
            coord = coord,
            name = unit.unitDef.name,
            cost = unit.unitDef.cost,
            starLevel = unit.starLevel,
            sellPrice = Economy.sellPrice(unit),
        )
    }

    private fun PlanningError.toMessage(): String = when (this) {
        PlanningError.NOT_ENOUGH_GOLD -> "골드가 부족합니다"
        PlanningError.BENCH_FULL -> "벤치가 가득 찼습니다"
        PlanningError.SLOT_UNAVAILABLE -> "이미 구매했거나 빈 칸입니다"
        PlanningError.UNIT_NOT_FOUND -> "해당 유닛을 찾을 수 없습니다"
        PlanningError.MAX_LEVEL -> "이미 최대 레벨입니다"
        PlanningError.INVALID_COORD -> "보드 밖입니다"
        PlanningError.BOARD_FULL -> "레벨을 올려야 더 배치할 수 있습니다"
        PlanningError.ITEM_NOT_FOUND -> "해당 아이템을 찾을 수 없습니다"
        PlanningError.ITEM_SLOTS_FULL -> "아이템 칸이 가득 찼습니다"
        PlanningError.ITEM_NOT_COMBINABLE -> "조합할 수 없는 아이템입니다"
        PlanningError.SAME_ITEM_SLOT -> "서로 다른 아이템 두 개를 골라야 합니다"
        PlanningError.NO_AUGMENT_OFFER -> "지금은 증강을 고르는 라운드가 아닙니다"
        PlanningError.AUGMENT_NOT_OFFERED -> "이번에 제시된 증강이 아닙니다"
    }
}
