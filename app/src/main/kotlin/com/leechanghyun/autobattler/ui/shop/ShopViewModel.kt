package com.leechanghyun.autobattler.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leechanghyun.autobattler.core.economy.ShopOffer
import com.leechanghyun.autobattler.core.economy.ShopRoller
import com.leechanghyun.autobattler.core.economy.UnitPool
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.core.model.PlayerState
import com.leechanghyun.autobattler.data.repository.GameDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 상점 화면 상태.
 *
 * @param isLoading DB 시딩과 첫 조회가 끝나기 전 상태
 * @param slots 상점 5칸. 유닛이 없는 칸은 null 이다.
 */
data class ShopUiState(
    val isLoading: Boolean = true,
    val level: Int = 3,
    val slots: List<ShopSlotUi> = emptyList(),
    val poolRemaining: Int = 0,
    val rollCount: Int = 0,
)

/** 상점 1칸의 표시용 데이터. */
data class ShopSlotUi(
    val unitId: String?,
    val name: String,
    val cost: Int,
    val origin: String,
    val unitClass: String,
    val skillName: String,
)

/**
 * 로드맵 1단계 완료 기준을 화면에서 확인하기 위한 ViewModel.
 *
 * Room 에서 읽어온 **실제 마스터 데이터**로 공용 풀을 만들고,
 * 순수 Kotlin 로직인 [ShopRoller] 로 5칸을 뽑는다.
 * 구매/판매와 골드 차감은 로드맵 2단계에서 붙인다.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val repository: GameDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    private lateinit var pool: UnitPool
    private lateinit var roller: ShopRoller
    private var currentOffer: ShopOffer = ShopOffer.EMPTY
    private var skillNamesById: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val units = repository.units()
            skillNamesById = repository.skills().associate { it.id to it.name }

            pool = UnitPool(units = units)
            roller = ShopRoller(pool)
            reroll()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** 상점을 새로 뽑는다. 이전 상점의 유닛은 풀로 되돌아간다. */
    fun reroll() {
        val level = _uiState.value.level
        currentOffer = roller.roll(level = level, previous = currentOffer)
        _uiState.update { state ->
            state.copy(
                slots = currentOffer.toUiSlots(),
                poolRemaining = pool.totalRemaining(),
                rollCount = state.rollCount + 1,
            )
        }
    }

    /**
     * 레벨을 바꾼다. 레벨이 높을수록 고코스트 유닛이 잘 나온다. (명세서 4-1 확률표)
     *
     * 1단계에서는 확률표가 실제로 동작하는지 눈으로 확인하려고 수동으로 조절한다.
     * 경험치로 레벨이 오르는 정식 흐름은 로드맵 2단계에서 붙인다.
     */
    fun changeLevel(delta: Int) {
        val next = (_uiState.value.level + delta).coerceIn(1, PlayerState.MAX_LEVEL)
        if (next == _uiState.value.level) return
        _uiState.update { it.copy(level = next) }
        reroll()
    }

    private fun ShopOffer.toUiSlots(): List<ShopSlotUi> = slots.map { slot ->
        val unit = slot.unit
        if (unit == null) {
            ShopSlotUi(null, "", 0, "", "", "")
        } else {
            ShopSlotUi(
                unitId = unit.id,
                name = unit.name,
                cost = unit.cost,
                origin = unit.origin.displayName,
                unitClass = unit.unitClass.displayName,
                skillName = skillNamesById[unit.skillId].orEmpty(),
            )
        }
    }

    /** 상점 슬롯 수. UI 가 자리를 미리 잡을 때 쓴다. */
    val slotCount: Int get() = EconomyRules.SHOP_SLOT_COUNT
}
