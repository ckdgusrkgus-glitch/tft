package com.leechanghyun.autobattler.core.model

/** 아이템이 올려주는 스탯 종류. 명세서 4-5 컴포넌트 9종에 대응한다. */
enum class StatType(val displayName: String) {
    ATTACK_DAMAGE("공격력"),
    ABILITY_POWER("주문력"),
    MAX_HP("최대 체력"),
    ATTACK_SPEED("공격속도"),
    ARMOR("방어력"),
    MAGIC_RESIST("마법저항력"),
    CRIT_CHANCE("치명타 확률"),
    HP_REGEN("체력 재생"),
    MANA("마나"),
}

/**
 * 아이템 마스터 데이터. 명세서 4-5 + 5장 데이터 모델 초안.
 *
 * @param statModifiers 스탯별 증가량. 비율 증가(공격속도/치명타 등)는 0.1 == +10% 로 해석한다.
 *   완성 아이템의 값은 재료 컴포넌트 2개의 합이다.
 * @param isComponent true 면 기본 아이템(컴포넌트), false 면 컴포넌트 2개를 합친 완성 아이템이다.
 * @param recipe 완성 아이템일 때 재료 컴포넌트 2개의 id. 컴포넌트면 비어 있다.
 * @param description 완성 아이템의 고유 효과 설명. 컴포넌트는 빈 문자열이다.
 * @param effectId 전투 로직이 고유 효과를 분기할 때 쓰는 키.
 *   **아직 이 값을 읽는 코드가 어디에도 없다.** 7단계는 스탯 합산까지만 했고, 명세서 4-5 가
 *   효과 문구만 주고 수치를 주지 않아 지금 구현하면 45종이 전부 임의 값이 된다. 11단계 밸런스에서
 *   수치와 함께 붙인다. `ItemStatsTest`/`ItemCombatTest` 의 감시 테스트가 이 사실을 지킨다.
 */
data class ItemDef(
    val id: String,
    val name: String,
    val statModifiers: Map<StatType, Float>,
    val isComponent: Boolean,
    val recipe: List<String> = emptyList(),
    val description: String = "",
    val effectId: String = "",
) {
    init {
        if (isComponent) {
            require(recipe.isEmpty()) { "컴포넌트 $id 는 조합식을 가질 수 없다" }
        } else {
            require(recipe.size == 2) { "완성 아이템 $id 는 컴포넌트 2개로 조합되어야 한다" }
        }
    }

    /** 조합식을 순서 무관하게 비교하기 위한 키. 예: "comp_might+comp_wisdom" */
    val recipeKey: String get() = recipeKeyOf(recipe)

    companion object {
        /** 컴포넌트 2개를 순서와 무관한 하나의 키로 만든다. */
        fun recipeKeyOf(componentIds: List<String>): String = componentIds.sorted().joinToString("+")

        fun recipeKeyOf(first: String, second: String): String = recipeKeyOf(listOf(first, second))
    }
}
