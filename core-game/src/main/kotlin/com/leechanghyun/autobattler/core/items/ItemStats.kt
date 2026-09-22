package com.leechanghyun.autobattler.core.items

import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.StatType
import com.leechanghyun.autobattler.core.synergy.UnitBuffs
import kotlin.math.roundToInt

/**
 * 장착한 아이템을 전투가 읽는 보정 묶음으로 환산한다. 명세서 4-5, 로드맵 7단계.
 *
 * 아이템은 [StatType] 별 실수 증가량을 들고 있고 전투는 [UnitBuffs] 만 읽는다. 그 사이를 잇는
 * **유일한** 함수다. 시너지·아이템·증강이 전부 [UnitBuffs] 한 타입으로 모이도록 5단계에서
 * 미리 자리를 잡아 뒀기 때문에, 7단계가 할 일은 이 환산표 하나뿐이다.
 *
 * ### 반올림은 맨 끝에 한 번만 한다
 * 아이템마다 반올림하면 `0.5 + 0.5` 가 `1 + 1 = 2` 가 된다. 합을 먼저 내고 마지막에 한 번만
 * 반올림해야 "컴포넌트 2개의 합 == 완성 아이템"이라는 [com.leechanghyun.autobattler.core.masterdata.CompletedItems]
 * 의 전제가 장착 뒤에도 유지된다.
 *
 * ### 아직 전투에 닿지 않는 스탯이 둘 있다
 * - [StatType.CRIT_CHANCE] — 치명타는 확률이고 4단계 전투는 난수를 한 줄도 쓰지 않는다.
 *   폭풍의 부족처럼 정수 충전으로 바꿔야 하는데, 그건 전투 규칙을 늘리는 일이라 7단계 완료 기준과
 *   무관하다. 지금 float 을 아무 필드에나 흘려 넣으면 "치명타가 붙었다"는 착각만 만든다.
 * - [StatType.HP_REGEN] — 틱마다 체력을 회복시키는 장치가 전투에 아예 없다.
 *
 * 둘 다 조용히 버려지므로 `ItemStatsTest` 가 "아직 전투에 반영되지 않는다"를 명시적으로 단언한다.
 * 그 테스트가 빨개지는 날이 구현이 끝난 날이다. [targetFieldOf] 는 컴포넌트가 아니라 **컴파일러용
 * 덫**이다. `else` 가지가 없어서 [StatType] 에 값을 하나 더하면 빌드가 깨지고, 그 순간 "이 스탯은
 * 어디로 가는가"를 반드시 결정하게 된다.
 *
 * ### 완성 아이템 45종의 고유 효과는 아직 없다
 * [ItemDef.effectId] 를 읽는 코드는 이 파일에도 전투에도 없다. 명세서 4-5 는 효과 문구만 주고
 * 수치를 주지 않아 지금 구현하면 전부 임의 값이 된다. 스탯 합산만으로 완료 기준을 만족하며,
 * 고유 효과는 11단계 밸런스에서 수치와 함께 붙인다.
 */
object ItemStats {

    /**
     * 아이템 목록을 하나의 보정으로 합친다. 빈 목록이면 [UnitBuffs.NONE] 이다.
     *
     * 같은 아이템을 여러 개 끼면 그만큼 곱해져 더해진다. 중복 장착 금지 규칙은 명세서에 없다.
     */
    fun buffsOf(items: List<ItemDef>): UnitBuffs {
        if (items.isEmpty()) return UnitBuffs.NONE

        val totals = HashMap<StatType, Float>(StatType.entries.size)
        items.forEach { item ->
            item.statModifiers.forEach { (stat, value) ->
                totals[stat] = (totals[stat] ?: 0f) + value
            }
        }
        fun total(stat: StatType): Float = totals[stat] ?: 0f

        return UnitBuffs(
            hpFlat = total(StatType.MAX_HP).roundToInt(),
            attackFlat = total(StatType.ATTACK_DAMAGE).roundToInt(),
            skillPowerFlat = total(StatType.ABILITY_POWER).roundToInt(),
            attackSpeedPercent = total(StatType.ATTACK_SPEED),
            // 방어력과 마법저항력은 한 풀이다. 이유는 UnitBuffs 문서 참고.
            armorFlat = (total(StatType.ARMOR) + total(StatType.MAGIC_RESIST)).roundToInt(),
            startingManaFlat = total(StatType.MANA).roundToInt(),
        )
    }

    /** 아이템 1개짜리 [buffsOf]. */
    fun buffsOf(item: ItemDef): UnitBuffs = buffsOf(listOf(item))

    /**
     * 스탯이 [UnitBuffs] 의 어느 항목으로 가는지. 아직 전투에 닿지 않으면 null.
     *
     * **[buffsOf] 는 이 함수를 부르지 않는다.** 오직 [StatType] 이 늘어났을 때 `when` 이
     * 불완전해져 컴파일이 깨지게 하려고 존재한다. 테스트가 [buffsOf] 와의 일치를 검사한다.
     */
    internal fun targetFieldOf(stat: StatType): String? = when (stat) {
        StatType.MAX_HP -> "hpFlat"
        StatType.ATTACK_DAMAGE -> "attackFlat"
        StatType.ABILITY_POWER -> "skillPowerFlat"
        StatType.ATTACK_SPEED -> "attackSpeedPercent"
        StatType.ARMOR -> "armorFlat"
        StatType.MAGIC_RESIST -> "armorFlat"
        StatType.MANA -> "startingManaFlat"
        StatType.CRIT_CHANCE -> null
        StatType.HP_REGEN -> null
    }
}
