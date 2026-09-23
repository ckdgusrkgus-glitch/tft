package com.leechanghyun.autobattler.ui.augment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leechanghyun.autobattler.ui.shop.AugmentUi
import com.leechanghyun.autobattler.ui.theme.AutoBattlerTheme

/**
 * 증강 선택 모달. 명세서 4-8, 7장 3번 화면. 로드맵 9단계 완료 기준의 **노출** 절반이다.
 *
 * ### 왜 Dialog 이고, 왜 닫을 수 없는가
 * 명세서 7장이 이 화면을 `증강 선택 화면 (모달)` 이라고 적었다. 그냥 겹쳐 그린 [Column] 은
 * 뒤쪽 버튼이 그대로 눌리므로 모달이 아니다. 뒤로 가기와 바깥 탭도 막는다. 지금 이 앱에는
 * 진행 상황을 저장하는 곳이 한 군데도 없어서(로드맵 11단계) 모달을 닫고 그대로 앱을 떠나면
 * 증강이 아니라 판 전체가 사라진다. 고를 때까지 닫히지 않는 편이 덜 나쁘다.
 *
 * 후보가 비어 있으면 아무것도 그리지 않는다. 그리지 않을지 판단하는 것은 화면이 아니라
 * `core-game` 이고, 이 함수는 받은 목록이 비었는지만 본다.
 */
@Composable
fun AugmentSelectOverlay(
    candidates: List<AugmentUi>,
    onChoose: (String) -> Unit,
) {
    if (candidates.isEmpty()) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("증강 선택", style = MaterialTheme.typography.titleLarge)
                Text(
                    "하나를 고르면 판이 끝날 때까지 유지됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidates.forEach { augment ->
                    AugmentCard(augment = augment, onClick = { onChoose(augment.id) })
                }
            }
        }
    }
}

@Composable
private fun AugmentCard(augment: AugmentUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                augment.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(augment.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AugmentSelectOverlayPreview() {
    AutoBattlerTheme {
        AugmentSelectOverlay(
            candidates = listOf(
                AugmentUi("aug_golden_touch", "황금손길", "매 라운드 종료 시 +2골드"),
                AugmentUi("aug_rapid_growth", "급속성장", "즉시 경험치 +8"),
                AugmentUi("aug_iron_will", "강철의의지", "아군 전체 방어력 +10"),
            ),
            onChoose = {},
        )
    }
}
