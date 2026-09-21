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
 * @param isComponent true 면 기본 아이템(컴포넌트), false 면 컴포넌트 2개를 합친 완성 아이템이다.
 * @param recipe 완성 아이템일 때 재료 컴포넌트 2개의 id. 컴포넌트면 비어 있다.
 */
data class ItemDef(
    val id: String,
    val name: String,
    val statModifiers: Map<StatType, Float>,
    val isComponent: Boolean,
    val recipe: List<String> = emptyList(),
) {
    init {
        if (isComponent) {
            require(recipe.isEmpty()) { "컴포넌트 $id 는 조합식을 가질 수 없다" }
        } else {
            require(recipe.size == 2) { "완성 아이템 $id 는 컴포넌트 2개로 조합되어야 한다" }
        }
    }
}
