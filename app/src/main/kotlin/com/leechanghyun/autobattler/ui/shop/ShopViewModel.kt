package com.leechanghyun.autobattler.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leechanghyun.autobattler.core.board.DropTarget
import com.leechanghyun.autobattler.core.economy.Economy
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.planning.PlanningError
import com.leechanghyun.autobattler.core.planning.PlanningResult
import com.leechanghyun.autobattler.core.planning.PlanningSession
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
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
 */
data class ShopUiState(
    val isLoading: Boolean = true,
    val round: Int = 0,
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
    /** 탭으로 고른 유닛. 판매 버튼이 이 유닛을 대상으로 한다. */
    val selectedId: String? = null,
    val poolRemaining: Int = 0,
    val message: String? = null,
) {
    val canReroll: Boolean get() = gold >= EconomyRules.REROLL_COST
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
                round = session.round,
                gold = player.gold,
                hp = player.hp,
                level = player.level,
                exp = player.exp,
                expToNext = Economy.expToNextLevel(player),
                slots = buildSlots(),
                bench = buildBench(),
                board = buildBoard(),
                boardCapacity = player.boardCapacity,
                poolRemaining = pool.totalRemaining(),
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
    }
}
