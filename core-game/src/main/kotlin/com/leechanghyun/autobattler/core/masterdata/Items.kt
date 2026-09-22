package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.StatType

/**
 * 기본 아이템(컴포넌트) 9종. 명세서 4-5 의 이름 목록 그대로.
 *
 * 스탯 증가량은 명세서에 없어 임의 초기값으로 채웠고 11단계에서 조정한다.
 * 비율 스탯(공격속도/치명타)은 0.15 == +15% 로 읽는다.
 *
 * 완성 아이템 45종은 [COMPLETED_ITEM_DEFS] 에 있고, 조합은
 * [MasterData.combine], 전투 반영은 [com.leechanghyun.autobattler.core.items.ItemStats] 가 한다(7단계).
 *
 * 9종 중 **관통의흔장(치명타)과 재생의흔장(체력 재생) 둘은 아직 전투에 닿지 않는다.**
 * 전투에 난수가 없고 틱 회복 장치도 없기 때문이며, 이유와 감시 테스트는 `ItemStats` 문서에 있다.
 */
internal val ITEM_COMPONENT_DEFS: List<ItemDef> = listOf(
    ItemDef(
        id = "comp_might",
        name = "힘의흔장",
        statModifiers = mapOf(StatType.ATTACK_DAMAGE to 10f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_wisdom",
        name = "지혜의흔장",
        statModifiers = mapOf(StatType.ABILITY_POWER to 10f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_vitality",
        name = "활력의흔장",
        statModifiers = mapOf(StatType.MAX_HP to 150f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_swiftness",
        name = "신속의흔장",
        statModifiers = mapOf(StatType.ATTACK_SPEED to 0.15f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_guard",
        name = "수호의흔장",
        statModifiers = mapOf(StatType.ARMOR to 20f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_resist",
        name = "저항의흔장",
        statModifiers = mapOf(StatType.MAGIC_RESIST to 20f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_pierce",
        name = "관통의흔장",
        statModifiers = mapOf(StatType.CRIT_CHANCE to 0.20f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_regen",
        name = "재생의흔장",
        statModifiers = mapOf(StatType.HP_REGEN to 5f),
        isComponent = true,
    ),
    ItemDef(
        id = "comp_mana",
        name = "마나의흔장",
        statModifiers = mapOf(StatType.MANA to 15f),
        isComponent = true,
    ),
)
