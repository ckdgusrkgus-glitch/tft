package com.leechanghyun.autobattler.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.leechanghyun.autobattler.core.board.DropTarget
import com.leechanghyun.autobattler.core.board.HexBoard
import com.leechanghyun.autobattler.core.board.PixelPoint
import com.leechanghyun.autobattler.core.board.PlayfieldLayout
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.ui.shop.BenchUnitUi
import com.leechanghyun.autobattler.ui.shop.PlacedUnitUi
import com.leechanghyun.autobattler.ui.theme.costColor

/** 드래그 중인 유닛과 손가락 위치. */
private data class DragState(
    val instanceId: String,
    val name: String,
    val cost: Int,
    val point: Offset,
)

/**
 * 헥스 보드와 벤치를 한 캔버스에 그리고 드래그앤드롭을 처리한다. 명세서 4-6.
 *
 * 보드와 벤치를 따로 두면 두 컴포저블 사이를 오가는 드래그의 좌표를 맞추기 번거롭다.
 * 하나의 캔버스에 같은 좌표계로 그리면 "손가락이 놓인 지점"을 한 번만 계산하면 된다.
 *
 * 어느 칸에 놓였는지 판단하는 계산은 전부 [PlayfieldLayout] 에 있고 단위테스트로 검증된다.
 * 이 파일은 그 결과를 그리고 터치 좌표를 넘기기만 한다.
 */
@Composable
fun PlayfieldView(
    board: List<PlacedUnitUi>,
    bench: List<BenchUnitUi>,
    selectedId: String?,
    onDrop: (instanceId: String, target: DropTarget) -> Unit,
    onSelect: (instanceId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val layout = remember(widthPx) { PlayfieldLayout.forWidth(widthPx) }
        val heightDp = with(density) { layout.totalHeight.toDp() }
        val textMeasurer = rememberTextMeasurer()

        var drag by remember { mutableStateOf<DragState?>(null) }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(layout, board, bench) {
                    detectDragGestures(
                        onDragStart = { start -> drag = pickUp(layout, board, bench, start) },
                        onDrag = { change, amount ->
                            change.consume()
                            drag = drag?.let { it.copy(point = it.point + amount) }
                        },
                        onDragEnd = {
                            val dragged = drag
                            drag = null
                            if (dragged != null) {
                                val target = layout.hitTest(dragged.point.toPixelPoint())
                                if (target != null) onDrop(dragged.instanceId, target)
                            }
                        },
                        onDragCancel = { drag = null },
                    )
                }
                .pointerInput(layout, board, bench) {
                    // 탭은 판매할 유닛을 고르는 용도다. 드래그로 옮기다가 실수로 팔리지 않도록
                    // 판매는 선택한 뒤 버튼을 한 번 더 누르게 했다.
                    detectTapGestures { tap ->
                        onSelect(pickUp(layout, board, bench, tap)?.instanceId)
                    }
                },
        ) {
            drawBoard(layout, board, drag?.instanceId, selectedId, textMeasurer)
            drawBench(layout, bench, drag?.instanceId, selectedId, textMeasurer)
            drag?.let { drawDraggedUnit(it, layout, textMeasurer) }
        }
    }
}

/** 드래그가 시작된 지점에 있던 유닛을 찾는다. 빈 칸에서 시작하면 null. */
private fun pickUp(
    layout: PlayfieldLayout,
    board: List<PlacedUnitUi>,
    bench: List<BenchUnitUi>,
    start: Offset,
): DragState? {
    val point = start.toPixelPoint()

    layout.coordAt(point)?.let { coord ->
        board.firstOrNull { it.coord == coord }?.let { placed ->
            return DragState(placed.instanceId, placed.name, placed.cost, start)
        }
    }

    if (layout.benchRect.contains(point)) {
        val index = (0 until layout.benchSlots).firstOrNull { layout.benchSlotRect(it).contains(point) }
        val unit = index?.let(bench::getOrNull) ?: return null
        return DragState(unit.instanceId, unit.name, unit.cost, start)
    }
    return null
}

private fun DrawScope.drawBoard(
    layout: PlayfieldLayout,
    board: List<PlacedUnitUi>,
    draggingId: String?,
    selectedId: String?,
    textMeasurer: TextMeasurer,
) {
    val occupied = board.associateBy { it.coord }

    HexBoard.coords.forEach { coord ->
        val path = hexPath(layout, coord)
        drawPath(path, color = EMPTY_CELL_FILL)
        drawPath(path, color = CELL_BORDER, style = Stroke(width = 2f))

        val placed = occupied[coord] ?: return@forEach
        if (placed.instanceId == draggingId) return@forEach

        val center = layout.centerOf(coord)
        drawUnitToken(
            centerX = center.x,
            centerY = center.y,
            radius = layout.hexSize * 0.55f,
            cost = placed.cost,
            label = placed.name.take(1),
            starLevel = placed.starLevel,
            selected = placed.instanceId == selectedId,
            textMeasurer = textMeasurer,
        )
    }
}

private fun DrawScope.drawBench(
    layout: PlayfieldLayout,
    bench: List<BenchUnitUi>,
    draggingId: String?,
    selectedId: String?,
    textMeasurer: TextMeasurer,
) {
    for (index in 0 until layout.benchSlots) {
        val rect = layout.benchSlotRect(index)
        drawRect(
            color = EMPTY_CELL_FILL,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
        )
        drawRect(
            color = CELL_BORDER,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(width = 2f),
        )

        val unit = bench.getOrNull(index) ?: continue
        if (unit.instanceId == draggingId) continue

        drawUnitToken(
            centerX = rect.centerX,
            centerY = rect.centerY,
            radius = rect.width * 0.36f,
            cost = unit.cost,
            label = unit.name.take(1),
            starLevel = unit.starLevel,
            selected = unit.instanceId == selectedId,
            textMeasurer = textMeasurer,
        )
    }
}

private fun DrawScope.drawDraggedUnit(
    drag: DragState,
    layout: PlayfieldLayout,
    textMeasurer: TextMeasurer,
) {
    drawUnitToken(
        centerX = drag.point.x,
        centerY = drag.point.y,
        radius = layout.hexSize * 0.62f,
        cost = drag.cost,
        label = drag.name.take(1),
        starLevel = 0,
        selected = false,
        textMeasurer = textMeasurer,
    )
}

/**
 * 유닛 1기를 그린다.
 *
 * 명세서 2장 방침대로 실제 일러스트 없이 코스트 색상 원형 placeholder 로 표현한다.
 * [starLevel] 이 0이면 별을 그리지 않는다. (드래그 중인 유닛)
 */
private fun DrawScope.drawUnitToken(
    centerX: Float,
    centerY: Float,
    radius: Float,
    cost: Int,
    label: String,
    starLevel: Int,
    selected: Boolean,
    textMeasurer: TextMeasurer,
) {
    drawCircle(color = costColor(cost), radius = radius, center = Offset(centerX, centerY))
    if (selected) {
        drawCircle(
            color = SELECTION_RING,
            radius = radius + 3f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 4f),
        )
    }

    val nameLayout = textMeasurer.measure(label, TOKEN_TEXT_STYLE)
    drawText(
        textLayoutResult = nameLayout,
        topLeft = Offset(
            centerX - nameLayout.size.width / 2f,
            centerY - nameLayout.size.height / 2f,
        ),
    )

    if (starLevel <= 1) return
    val starLayout = textMeasurer.measure("★".repeat(starLevel), STAR_TEXT_STYLE)
    drawText(
        textLayoutResult = starLayout,
        topLeft = Offset(centerX - starLayout.size.width / 2f, centerY - radius - starLayout.size.height),
    )
}

private fun hexPath(layout: PlayfieldLayout, coord: HexCoord): Path {
    val corners = layout.cornersOf(coord)
    return Path().apply {
        moveTo(corners.first().x, corners.first().y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}

private fun Offset.toPixelPoint() = PixelPoint(x, y)

private val EMPTY_CELL_FILL = Color(0x22FFFFFF)
private val CELL_BORDER = Color(0x66888888)
private val TOKEN_TEXT_STYLE = TextStyle(color = Color.Black, fontSize = 13.sp)
private val STAR_TEXT_STYLE = TextStyle(color = Color(0xFFFFC107), fontSize = 10.sp)
private val SELECTION_RING = Color(0xFFFFFFFF)
