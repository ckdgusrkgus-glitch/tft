package com.leechanghyun.autobattler.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leechanghyun.autobattler.core.masterdata.EconomyRules
import com.leechanghyun.autobattler.ui.theme.AutoBattlerTheme
import com.leechanghyun.autobattler.ui.theme.costColor

/** Hilt 로 ViewModel 을 받아 [ShopScreen] 에 상태와 콜백을 넘기는 진입 Composable. */
@Composable
fun ShopRoute(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShopScreen(
        state = state,
        onBuy = viewModel::buy,
        onSell = viewModel::sell,
        onReroll = viewModel::reroll,
        onBuyExp = viewModel::buyExp,
        onNextRound = viewModel::nextRound,
        onMessageShown = viewModel::consumeMessage,
    )
}

/**
 * 로드맵 2단계 화면. 위에서부터 플레이어 정보, 상점 5칸, 벤치 순이다.
 *
 * ViewModel 을 직접 받지 않고 상태와 콜백만 받아 Preview 와 테스트가 쉽다.
 */
@Composable
fun ShopScreen(
    state: ShopUiState,
    onBuy: (Int) -> Unit,
    onSell: (String) -> Unit,
    onReroll: () -> Unit,
    onBuyExp: () -> Unit,
    onNextRound: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerHeader(state)
            ActionBar(state, onReroll, onBuyExp, onNextRound)

            Text("상점", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.slots.forEach { slot ->
                    ShopSlotCard(slot = slot, onBuy = { onBuy(slot.index) })
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "벤치 ${state.bench.size} / ${state.benchCapacity}  (탭하면 판매)",
                style = MaterialTheme.typography.titleMedium,
            )
            BenchRow(state, onSell)
        }
    }
}

@Composable
private fun PlayerHeader(state: ShopUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${state.round}라운드", style = MaterialTheme.typography.titleMedium)
                Text("체력 ${state.hp}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.gold}골드",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            val needed = state.expToNext
            Text(
                if (needed == null) {
                    "레벨 ${state.level} (최대)"
                } else {
                    "레벨 ${state.level} · 경험치 ${state.exp} (다음까지 $needed)"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (needed != null) {
                val total = state.exp + needed
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else state.exp / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "남은 유닛 풀 ${state.poolRemaining}장",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionBar(
    state: ShopUiState,
    onReroll: () -> Unit,
    onBuyExp: () -> Unit,
    onNextRound: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onReroll, enabled = state.canReroll) {
            Text("새로고침 ${EconomyRules.REROLL_COST}골드")
        }
        OutlinedButton(onClick = onBuyExp, enabled = state.canBuyExp) {
            Text("경험치 ${EconomyRules.BUY_EXP_COST}골드")
        }
        Button(onClick = onNextRound) { Text("다음 라운드") }
    }
}

@Composable
private fun ShopSlotCard(slot: ShopSlotUi, onBuy: () -> Unit) {
    val isEmpty = slot.unitId == null || slot.purchased

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (slot.buyable) Modifier.clickable(onClick = onBuy) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (slot.purchased) "구매함" else "빈 칸",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Card
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 명세서 2장 방침대로 실제 일러스트 대신 코스트 색상 원형 placeholder 를 쓴다.
            UnitAvatar(name = slot.name, cost = slot.cost)

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(slot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${slot.origin} · ${slot.unitClass}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(slot.skillName, style = MaterialTheme.typography.bodySmall)
            }

            Text(
                "${slot.cost}골드",
                style = MaterialTheme.typography.titleSmall,
                color = if (slot.buyable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

@Composable
private fun BenchRow(state: ShopUiState, onSell: (String) -> Unit) {
    if (state.bench.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("상점에서 유닛을 사면 여기에 올라갑니다", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.bench, key = { it.instanceId }) { unit ->
            Card(
                modifier = Modifier
                    .width(96.dp)
                    .clickable { onSell(unit.instanceId) },
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    UnitAvatar(name = unit.name, cost = unit.cost, size = 36)
                    Text(
                        unit.name,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text("${"★".repeat(unit.starLevel)} · ${unit.sellPrice}골드", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun UnitAvatar(name: String, cost: Int, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(costColor(cost), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ShopScreenPreview() {
    AutoBattlerTheme {
        ShopScreen(
            state = ShopUiState(
                isLoading = false,
                round = 3,
                gold = 14,
                hp = 92,
                level = 5,
                exp = 8,
                expToNext = 22,
                poolRemaining = 281,
                slots = listOf(
                    ShopSlotUi(0, "steel_guard", "강철수호병", 1, "기계공학자", "수호자", "강철 방벽", false, true),
                    ShopSlotUi(1, "storm_mage", "폭풍마도사", 2, "폭풍의 부족", "마법사", "연쇄 번개", false, true),
                    ShopSlotUi(2, "abyss_devourer", "심연포식자", 3, "심연의 아이들", "마법사", "공포", true, false),
                    ShopSlotUi(3, "golden_giant", "황금거인", 4, "황금가문", "수호자", "거인의 강타", false, false),
                    ShopSlotUi(4, null, "", 0, "", "", "", false, false),
                ),
                bench = listOf(
                    BenchUnitUi("u0", "강철수호병", 1, 1, "기계공학자", "수호자", 1),
                    BenchUnitUi("u1", "어둠칼날", 2, 2, "심연의 아이들", "검사", 5),
                ),
            ),
            onBuy = {},
            onSell = {},
            onReroll = {},
            onBuyExp = {},
            onNextRound = {},
            onMessageShown = {},
        )
    }
}
