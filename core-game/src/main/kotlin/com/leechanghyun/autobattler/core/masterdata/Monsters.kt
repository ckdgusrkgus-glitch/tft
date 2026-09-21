package com.leechanghyun.autobattler.core.masterdata

import com.leechanghyun.autobattler.core.model.MinionGroup
import com.leechanghyun.autobattler.core.model.MonsterDef

/**
 * PvE 크립 라운드 몬스터 4종. 명세서 4-9 표 그대로.
 *
 * 보상 수치는 명세서가 "초기값"이라고 명시했으므로 11단계에서 조정한다.
 */
internal val MONSTER_DEFS: List<MonsterDef> = listOf(
    MonsterDef(
        id = "creep_wild_dogs",
        name = "들개무리",
        stage = 1,
        count = 3,
        hp = 300,
        attack = 20,
        rewardComponents = 1,
    ),
    MonsterDef(
        id = "creep_rock_golem",
        name = "바위골렘",
        stage = 2,
        count = 1,
        hp = 1800,
        attack = 40,
        minions = MinionGroup(count = 2, hp = 250, attack = 40),
        rewardComponents = 2,
    ),
    MonsterDef(
        id = "creep_venom_spiders",
        name = "독거미떼",
        stage = 3,
        count = 5,
        hp = 500,
        attack = 30,
        rewardComponents = 2,
        rewardGold = 3,
    ),
    MonsterDef(
        id = "creep_ancient_dragon",
        name = "고대수호룡",
        stage = 4,
        count = 1,
        hp = 4500,
        attack = 80,
        rewardCompletedItem = true,
    ),
)
