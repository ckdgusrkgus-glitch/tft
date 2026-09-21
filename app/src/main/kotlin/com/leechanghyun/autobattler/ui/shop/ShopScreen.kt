package com.leechanghyun.autobattler.ui.shop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leechanghyun.autobattler.ui.theme.AutoBattlerTheme
import com.leechanghyun.autobattler.ui.theme.costColor

/** Hilt 로 ViewModel 을 받아 [ShopScreen] 에 상태를 넘기는 진입 Composable. */
@Composable
fun ShopRoute(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShopScreen(
        state = state,
        onReroll = viewModel::reroll,
        onLevelChange = viewModel::changeLevel,
    )
}

/**
 * 상점 화면. 로드맵 1단계 완료 기준인 "상점 5칸에 실제 유닛 데이터가 랜덤 노출"을 눈으로 확인하는 화면이다.
 *
 * ViewModel 을 직접 받지 않고 상태와 콜백만 받는 형태(stateless composable)라 Preview 와 테스트가 쉽다.
 */
@Composable
fun ShopScreen(
    state: ShopUiState,
    onReroll: () -> Unit,
    onLevelChange: (Int) -> Unit,
) {
    Scaffold { innerPadding ->
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
            Text("상점", style = MaterialTheme.typography.headlineSmall)
            Text(
                "레벨 ${state.level} · 남은 풀 ${state.poolRemaining}장 · ${state.rollCount}번째 상점",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLevelChange(-1) }) { Text("레벨 -") }
                OutlinedButton(onClick = { onLevelChange(1) }) { Text("레벨 +") }
                Button(onClick = onReroll) { Text("새로고침") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.slots.forEach { slot -> ShopSlotCard(slot) }
            }
        }
    }
}

@Composable
private fun ShopSlotCard(slot: ShopSlotUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (slot.unitId == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("빈 칸", style = MaterialTheme.typography.bodyMedium)
            }
            return@Card
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 명세서 2장 방침대로 실제 일러스트 대신 계열 색상 원형 placeholder 를 쓴다.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(costColor(slot.cost), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    slot.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(slot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${slot.origin} · ${slot.unitClass}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(slot.skillName, style = MaterialTheme.typography.bodySmall)
            }

            Text("${slot.cost}골드", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShopScreenPreview() {
    AutoBattlerTheme {
        ShopScreen(
            state = ShopUiState(
                isLoading = false,
                level = 5,
                poolRemaining = 281,
                rollCount = 3,
                slots = listOf(
                    ShopSlotUi("steel_guard", "강철수호병", 1, "기계공학자", "수호자", "강철 방벽"),
                    ShopSlotUi("storm_mage", "폭풍마도사", 2, "폭풍의 부족", "마법사", "연쇄 번개"),
                    ShopSlotUi("abyss_devourer", "심연포식자", 3, "심연의 아이들", "마법사", "공포"),
                    ShopSlotUi("golden_giant", "황금거인", 4, "황금가문", "수호자", "거인의 강타"),
                    ShopSlotUi(null, "", 0, "", "", ""),
                ),
            ),
            onReroll = {},
            onLevelChange = {},
        )
    }
}
