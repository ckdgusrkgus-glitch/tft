package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.ItemDef
import com.leechanghyun.autobattler.core.model.StatType

/**
 * 완성 아이템의 조합식과 고유 효과. 스탯은 [COMPLETED_ITEM_DEFS] 가 재료 합으로 자동 계산한다.
 *
 * @param first 재료 컴포넌트 1
 * @param second 재료 컴포넌트 2
 */
private data class CompletedItemSpec(
    val id: String,
    val name: String,
    val first: String,
    val second: String,
    val description: String,
    val effectId: String,
)

// 컴포넌트 id 를 짧게 쓰기 위한 별칭.
private const val MIGHT = "comp_might"          // 힘의흔장   - 공격력
private const val SWIFT = "comp_swiftness"      // 신속의흔장 - 공격속도
private const val WISDOM = "comp_wisdom"        // 지혜의흔장 - 주문력
private const val MANA = "comp_mana"            // 마나의흔장 - 마나
private const val GUARD = "comp_guard"          // 수호의흔장 - 방어력
private const val RESIST = "comp_resist"        // 저항의흔장 - 마법저항력
private const val VITAL = "comp_vitality"       // 활력의흔장 - 최대 체력
private const val PIERCE = "comp_pierce"        // 관통의흔장 - 치명타 확률
private const val REGEN = "comp_regen"          // 재생의흔장 - 체력 재생

/**
 * 완성 아이템 45종의 조합식.
 *
 * 컴포넌트 9종을 2개씩 조합하면 중복을 뺀 경우의 수가 9 x 10 / 2 = **45가지**다.
 * (같은 컴포넌트 2개를 쓰는 조합 9가지 + 서로 다른 조합 36가지)
 * 명세서 4-5 는 "3x3=9칸"이라고 적었지만 컴포넌트가 9종이므로 실제로는 9x9 격자가 맞고,
 * 사용자 확인을 거쳐 원작 방식대로 45종을 채웠다.
 *
 * 효과 문구는 원작의 아이템 구성을 참고하되 이름과 표현은 자체 창작이다.
 * 수치는 [effectId] 로 분기하는 로드맵 7단계에서 정한다.
 */
private val COMPLETED_ITEM_SPECS: List<CompletedItemSpec> = listOf(
    // 힘의흔장(공격력) 계열
    CompletedItemSpec("item_executioner_blade", "처형검", MIGHT, MIGHT, "공격력이 크게 증가하고 처치 시 공격력이 누적된다", "BONUS_AD_STACK"),
    CompletedItemSpec("item_giant_slayer", "거인사냥꾼", MIGHT, SWIFT, "최대 체력이 높은 적에게 주는 피해가 증가한다", "DAMAGE_VS_HIGH_HP"),
    CompletedItemSpec("item_bloodmagic_blade", "흡혈마검", MIGHT, WISDOM, "가한 피해의 일부만큼 체력이 가장 낮은 아군을 회복시킨다", "SPELL_VAMP_ALLY"),
    CompletedItemSpec("item_warspear", "격전의창", MIGHT, MANA, "기본 공격 시 마나를 추가로 회복한다", "MANA_ON_ATTACK"),
    CompletedItemSpec("item_nightfall_edge", "밤의칼날", MIGHT, GUARD, "체력이 낮아지면 잠시 은신하며 공격속도를 얻는다", "STEALTH_ON_LOW_HP"),
    CompletedItemSpec("item_thirsting_sword", "갈증의검", MIGHT, RESIST, "가한 피해의 일부만큼 자신을 회복하고 위급할 때 보호막을 얻는다", "LIFESTEAL_SHIELD"),
    CompletedItemSpec("item_giants_resolve", "거인의결의", MIGHT, VITAL, "체력이 낮아지면 공격력과 최대 체력이 증가한다", "RAGE_ON_LOW_HP"),
    CompletedItemSpec("item_infinity_blade", "무한의검", MIGHT, PIERCE, "치명타 확률과 치명타 피해량이 증가한다", "CRIT_DAMAGE_UP"),
    CompletedItemSpec("item_reaping_sword", "수확의검", MIGHT, REGEN, "공격할 때마다 체력을 회복한다", "HEAL_ON_ATTACK"),

    // 신속의흔장(공격속도) 계열
    CompletedItemSpec("item_rapid_cannon", "연사포", SWIFT, SWIFT, "공격속도가 크게 증가하고 사거리가 한 칸 늘어난다", "RANGE_AND_SPEED"),
    CompletedItemSpec("item_rage_blade", "분노의검", SWIFT, WISDOM, "공격할 때마다 공격속도가 계속 누적된다", "ATTACK_SPEED_STACK"),
    CompletedItemSpec("item_static_dagger", "전격단검", SWIFT, MANA, "일정 횟수 공격할 때마다 여러 적에게 마법 피해를 준다", "BURST_EVERY_N_ATTACKS"),
    CompletedItemSpec("item_unyielding_will", "불굴의투지", SWIFT, GUARD, "공격하거나 피격될 때마다 공격력과 주문력이 누적된다", "COMBAT_STACK"),
    CompletedItemSpec("item_storm_bow", "폭풍의활", SWIFT, RESIST, "공격할 때 다른 적에게도 화살을 한 발 더 쏜다", "EXTRA_TARGET_ATTACK"),
    CompletedItemSpec("item_beast_fang", "야수의송곳니", SWIFT, VITAL, "스킬을 쓴 뒤 잠시 공격속도가 증가한다", "SPEED_AFTER_SKILL"),
    CompletedItemSpec("item_last_whisper", "최후의속삭임", SWIFT, PIERCE, "치명타가 터지면 대상의 방어력이 감소한다", "ARMOR_SHRED_ON_CRIT"),
    CompletedItemSpec("item_swift_regalia", "쾌속재생갑", SWIFT, REGEN, "공격속도가 증가하고 매초 체력을 회복한다", "SPEED_AND_REGEN"),

    // 지혜의흔장(주문력) 계열
    CompletedItemSpec("item_archmage_crown", "대마도사의관", WISDOM, WISDOM, "주문력이 매우 크게 증가한다", "HUGE_AP"),
    CompletedItemSpec("item_archangel_staff", "대천사의지팡이", WISDOM, MANA, "전투 중 일정 시간마다 주문력이 영구히 증가한다", "AP_OVER_TIME"),
    CompletedItemSpec("item_mage_guard", "마력수호갑", WISDOM, GUARD, "전투 시작 시 보호막을 얻고 보호막이 사라지면 주문력을 얻는다", "SHIELD_THEN_AP"),
    CompletedItemSpec("item_ionic_orb", "이온충격구", WISDOM, RESIST, "주변 적의 마법저항력을 낮추고 적이 스킬을 쓰면 감전시킨다", "MR_SHRED_ZAP"),
    CompletedItemSpec("item_book_of_agony", "고통의서", WISDOM, VITAL, "스킬이 적중한 적을 태우고 받는 회복량을 줄인다", "BURN_AND_WOUND"),
    CompletedItemSpec("item_jeweled_gauntlet", "마법치명장갑", WISDOM, PIERCE, "스킬 피해도 치명타가 터진다", "SPELL_CAN_CRIT"),
    CompletedItemSpec("item_book_of_life", "생명의성서", WISDOM, REGEN, "주문력이 증가하고 스킬로 준 피해의 일부를 회복한다", "SPELL_VAMP"),

    // 마나의흔장 계열
    CompletedItemSpec("item_azure_blessing", "푸른축복", MANA, MANA, "스킬을 사용하면 소모한 마나의 일부를 돌려받는다", "MANA_REFUND"),
    CompletedItemSpec("item_protector_vow", "수호자의맹세", MANA, GUARD, "체력이 낮아지면 보호막과 방어력을 얻는다", "SHIELD_ON_LOW_HP"),
    CompletedItemSpec("item_adaptive_helm", "적응형투구", MANA, RESIST, "앞열이면 방어 능력치를, 뒷열이면 주문력을 추가로 얻는다", "POSITION_ADAPTIVE"),
    CompletedItemSpec("item_redemption_relic", "구원의성물", MANA, VITAL, "일정 시간마다 주변 아군의 체력을 회복시킨다", "PERIODIC_AOE_HEAL"),
    CompletedItemSpec("item_hand_of_judgment", "심판의손", MANA, PIERCE, "전투마다 공격력 강화와 회복 강화 중 하나가 무작위로 적용된다", "RANDOM_BUFF"),
    CompletedItemSpec("item_spring_chalice", "샘물의성배", MANA, REGEN, "마나가 가득 찰 때마다 체력을 회복한다", "HEAL_ON_FULL_MANA"),

    // 수호의흔장(방어력) 계열
    CompletedItemSpec("item_bramble_armor", "가시갑옷", GUARD, GUARD, "방어력이 크게 증가하고 피격 시 주변 적에게 피해를 반사한다", "THORNS"),
    CompletedItemSpec("item_gargoyle_plate", "석상의갑주", GUARD, RESIST, "자신을 노리는 적이 많을수록 방어력과 마법저항력이 증가한다", "DEFENSE_PER_ATTACKER"),
    CompletedItemSpec("item_sunflare_cape", "태양불꽃망토", GUARD, VITAL, "주변 적을 지속적으로 불태우고 받는 회복량을 줄인다", "AURA_BURN"),
    CompletedItemSpec("item_steadfast_heart", "강철의심장", GUARD, PIERCE, "받는 피해를 일정 비율 줄이며 체력이 높을수록 더 많이 줄인다", "DAMAGE_REDUCTION"),
    CompletedItemSpec("item_enduring_scale", "불굴의비늘", GUARD, REGEN, "방어력이 증가하고 매초 체력을 회복한다", "ARMOR_AND_REGEN"),

    // 저항의흔장(마법저항) 계열
    CompletedItemSpec("item_dragon_claw", "용의발톱", RESIST, RESIST, "마법저항력이 크게 증가하고 마법 피해를 받으면 체력을 회복한다", "MAGIC_HEAL"),
    CompletedItemSpec("item_spirit_visage", "혼백의투구", RESIST, VITAL, "최대 체력과 마법저항력이 증가하고 받는 회복량이 강화된다", "HEAL_AMPLIFY"),
    CompletedItemSpec("item_quicksilver_sash", "수은장식띠", RESIST, PIERCE, "전투 시작 후 일정 시간 동안 군중제어에 면역이 된다", "CC_IMMUNE"),
    CompletedItemSpec("item_cleansing_cloak", "정화의망토", RESIST, REGEN, "마법저항력이 증가하고 매초 체력을 회복한다", "MR_AND_REGEN"),

    // 활력의흔장(체력) 계열
    CompletedItemSpec("item_titan_belt", "거인의허리띠", VITAL, VITAL, "최대 체력이 매우 크게 증가한다", "HUGE_HP"),
    CompletedItemSpec("item_wardbreaker", "방벽파괴자", VITAL, PIERCE, "보호막을 가진 적에게 주는 피해가 증가한다", "DAMAGE_VS_SHIELD"),
    CompletedItemSpec("item_regrowth_plate", "재생의갑주", VITAL, REGEN, "최대 체력이 증가하고 매초 체력을 회복한다", "HP_AND_REGEN"),

    // 관통의흔장(치명타) 계열
    CompletedItemSpec("item_thief_gloves", "도적의장갑", PIERCE, PIERCE, "매 라운드 무작위 완성 아이템 2개를 대신 장착한다", "RANDOM_ITEMS"),
    CompletedItemSpec("item_leeching_gloves", "흡혈의장갑", PIERCE, REGEN, "치명타가 터질 때 체력을 회복한다", "HEAL_ON_CRIT"),

    // 재생의흔장 계열
    CompletedItemSpec("item_relic_of_renewal", "재생의성물", REGEN, REGEN, "매초 회복량이 크게 증가하고 받는 모든 회복 효과가 강화된다", "SUPER_REGEN"),
)

/**
 * 완성 아이템 45종.
 *
 * 스탯은 재료 컴포넌트 2개의 스탯을 더해서 만든다. 손으로 45벌을 적지 않으므로
 * "재료는 바꿨는데 스탯 표를 안 고치는" 종류의 실수가 원천적으로 생기지 않는다.
 */
internal val COMPLETED_ITEM_DEFS: List<ItemDef> = run {
    val componentsById = ITEM_COMPONENT_DEFS.associateBy { it.id }
    COMPLETED_ITEM_SPECS.map { spec ->
        val first = requireNotNull(componentsById[spec.first]) { "알 수 없는 컴포넌트: ${spec.first}" }
        val second = requireNotNull(componentsById[spec.second]) { "알 수 없는 컴포넌트: ${spec.second}" }
        ItemDef(
            id = spec.id,
            name = spec.name,
            statModifiers = sumStats(first.statModifiers, second.statModifiers),
            isComponent = false,
            recipe = listOf(spec.first, spec.second),
            description = spec.description,
            effectId = spec.effectId,
        )
    }
}

/** 스탯 맵 두 개를 더한다. 같은 스탯은 합산하고, 한쪽에만 있으면 그대로 가져온다. */
private fun sumStats(
    first: Map<StatType, Float>,
    second: Map<StatType, Float>,
): Map<StatType, Float> {
    val merged = LinkedHashMap<StatType, Float>(first)
    second.forEach { (stat, value) -> merged[stat] = (merged[stat] ?: 0f) + value }
    return merged
}
