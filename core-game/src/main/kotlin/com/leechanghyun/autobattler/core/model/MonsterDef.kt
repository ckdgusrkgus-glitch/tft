package com.leechanghyun.autobattler.core.model

/**
 * PvE 크립 라운드 몬스터 마스터 데이터. 명세서 4-9.
 *
 * @param stage 등장 스테이지. 라운드 표기는 "$stage-4" 이다.
 * @param minions 보조 미니언. 없으면 null. (2-4 바위골렘만 해당)
 * @param rewardGold 승리 시 추가 골드. 보상 수치는 임의 초기값이며 11단계에서 조정한다.
 */
data class MonsterDef(
    val id: String,
    val name: String,
    val stage: Int,
    val count: Int,
    val hp: Int,
    val attack: Int,
    val minions: MinionGroup? = null,
    val rewardComponents: Int = 0,
    val rewardCompletedItem: Boolean = false,
    val rewardGold: Int = 0,
) {
    init {
        require(count >= 1) { "몬스터 $id 의 마리 수는 1 이상이어야 한다" }
        require(hp > 0) { "몬스터 $id 의 체력은 0보다 커야 한다" }
    }

    /** 몬스터 라운드 표기. 예: "2-4" */
    val roundLabel: String get() = "$stage-$MONSTER_ROUND_IN_STAGE"

    companion object {
        /** 크립 라운드는 각 스테이지의 4번째 라운드에 고정이다. 명세서 4-9. */
        const val MONSTER_ROUND_IN_STAGE = 4
    }
}

/** 몬스터에 딸린 보조 개체. 명세서 4-9 의 "(+미니언 2)". */
data class MinionGroup(
    val count: Int,
    val hp: Int,
    val attack: Int,
)
