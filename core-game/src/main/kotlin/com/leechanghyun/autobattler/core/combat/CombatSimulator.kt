package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexGrid
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.HexCoord
import com.leechanghyun.autobattler.core.synergy.SynergyState

/**
 * 틱 기반 자동전투 시뮬레이터. 명세서 4-6.
 *
 * 100ms 틱마다 살아 있는 모든 유닛이 한 번씩 행동한다. 유닛 한 기의 우선순위는
 *   1. 사거리 안이고 마나가 가득 찼으면 **스킬**
 *   2. 사거리 안이고 공격 대기가 끝났으면 **기본 공격**
 *   3. 사거리 밖이면 **최단 경로로 한 칸 이동** (헥스 그리드 BFS)
 * 이고, 대상은 항상 **가장 가까운 적**이다.
 *
 * ### 결정적으로 동작한다
 * 난수를 전혀 쓰지 않는다. 유닛 행동 순서는 id 순으로 고정하고, 같은 거리의 후보가 여럿이면
 * 전장 좌표 순서로 고른다. 같은 배치를 넣으면 항상 같은 결과가 나오므로 단위테스트로 검증할 수 있고,
 * 8인 라운드에서 재현되지 않는 버그가 생기지 않는다. 치명타 같은 확률 요소는 11단계에서 붙인다.
 *
 * ### 시너지 (5단계)
 * 시너지는 [setupFrom] 에서 유닛을 조립할 때 [com.leechanghyun.autobattler.core.synergy.UnitBuffs]
 * 로 한 번에 주입된다. 스탯 배수, 방어력 점수, 주기 쉴드, 연쇄 번개가 그렇게 들어왔다.
 * 폭풍의 부족의 "일정 확률로"는 난수가 아니라 정수 충전으로 옮겼다. 위의 결정성 규칙은 그대로다.
 *
 * ### 아직 반영하지 않은 것
 * 방어력과 마법저항력은 한 풀로 합쳐져 있다. 피해에 종류 구분이 없어 나눌 수 없기 때문이며,
 * 7단계에서 수호의흔장/저항의흔장이 실제로 달라야 할 때 쪼갠다.
 * 기계공학자 6인의 거대 골렘은 명세만 내보내고 실제 소환은 10단계다.
 * 아이템 효과는 7단계에서 같은 [com.leechanghyun.autobattler.core.synergy.UnitBuffs] 로 합류한다.
 */
class CombatSimulator(private val field: HexGrid = CombatField.grid) {

    /** 좌표 순서를 고정해 같은 거리의 후보 중 하나를 결정적으로 고르기 위한 색인. */
    private val coordOrder: Map<HexCoord, Int> =
        field.coords.withIndex().associate { (index, coord) -> coord to index }

    /**
     * 전투를 끝까지 돌린다.
     *
     * @param units 양 진영의 유닛. 전장 좌표가 이미 들어 있어야 한다.
     * @param maxTicks 이 틱을 넘기면 무승부 판정으로 강제 종료한다.
     */
    fun simulate(units: List<CombatUnit>, maxTicks: Int = CombatRules.MAX_TICKS): CombatOutcome {
        require(units.map { it.id }.toSet().size == units.size) { "유닛 id 가 중복됐다" }
        require(units.all { field.contains(it.position) }) { "전장 밖에 있는 유닛이 있다" }

        val order = units.sortedBy { it.id }
        val events = mutableListOf<CombatEvent>()
        var tick = 0

        while (tick < maxTicks && !isOver(units)) {
            tick++
            refreshShields(tick, order, events)
            val occupied = units.filter { it.isAlive }.associateBy { it.position }.toMutableMap()

            for (unit in order) {
                if (!unit.isAlive) continue
                unit.attackCooldown = (unit.attackCooldown - 1).coerceAtLeast(0)
                unit.moveCooldown = (unit.moveCooldown - 1).coerceAtLeast(0)

                val target = nearestEnemy(unit, units) ?: continue
                val inRange = HexGrid.distance(unit.position, target.position) <= unit.attackRange

                when {
                    inRange && unit.canCastSkill -> castSkill(tick, unit, target, units, events)
                    inRange && unit.attackCooldown == 0 -> attack(tick, unit, target, units, events)
                    !inRange && unit.moveCooldown == 0 -> move(tick, unit, target, occupied, events)
                }
            }
        }

        return buildOutcome(units, tick, timedOut = tick >= maxTicks && !isOver(units), events = events)
    }

    /**
     * 시너지까지 함께 받아 전투를 돌린다. 5단계 완료 기준이 쓰는 진입점이다.
     *
     * 발동한 시너지를 0틱 이벤트로 먼저 찍고 결과에도 실어 돌려준다. 시너지는 전투 내내 바뀌지
     * 않으므로 0틱이 정직한 시점이다. 틱 루프는 건드리지 않아 4단계 전투 동작이 그대로다.
     */
    fun simulate(setup: CombatSetup, maxTicks: Int = CombatRules.MAX_TICKS): CombatOutcome {
        val traitEvents = CombatTeam.entries.flatMap { team ->
            setup.synergyFor(team).active.map { active ->
                CombatEvent.TraitActivated(
                    tick = 0,
                    team = team,
                    traitId = active.traitId,
                    tier = active.tier,
                    threshold = active.activeThreshold ?: 0,
                    memberCount = active.memberCount,
                )
            }
        }
        val outcome = simulate(setup.units, maxTicks)
        return outcome.copy(
            events = traitEvents + outcome.events,
            synergyByTeam = setup.synergyByTeam,
        )
    }

    // --- 행동 ---

    private fun attack(
        tick: Int,
        attacker: CombatUnit,
        target: CombatUnit,
        units: List<CombatUnit>,
        events: MutableList<CombatEvent>,
    ) {
        val dealt = target.takeDamage(attacker.attackDamage)
        attacker.attackCooldown = attacker.attackIntervalTicks
        attacker.gainMana(CombatRules.MANA_PER_ATTACK)
        target.gainMana(CombatRules.MANA_PER_HIT_TAKEN)

        events += CombatEvent.Attacked(tick, attacker.id, target.id, dealt.hpLost, dealt.absorbed)
        if (!target.isAlive) events += CombatEvent.Died(tick, target.id)

        chainLightning(tick, attacker, target, units, events)
    }

    /**
     * 심연의 아이들의 주기 쉴드. 명세서 4-4.
     *
     * 유닛의 행동과 무관한 유일한 전역 단계다. 명세서가 "주기적으로 아군 전체"라고 했으므로
     * 사거리 밖에서 걷고 있는 유닛도 받아야 하기 때문이다.
     *
     * 발동 틱을 `(tick - 1) % 주기` 로 재는 이유는 `tick` 이 이미 증가한 뒤라서다.
     * 그냥 `tick % 주기` 로 하면 첫 주기를 맨몸으로 보낸다.
     * 순회 순서는 id 순([order])이라 결정성 규칙을 그대로 따른다.
     */
    private fun refreshShields(tick: Int, order: List<CombatUnit>, events: MutableList<CombatEvent>) {
        for (unit in order) {
            if (!unit.isAlive || !unit.buffs.hasShield) continue
            if ((tick - 1) % unit.buffs.shieldPeriodTicks != 0) continue
            unit.refreshShield()
            if (unit.shieldPerRefresh > 0) events += CombatEvent.Shielded(tick, unit.id, unit.shieldPerRefresh)
        }
    }

    /**
     * 폭풍의 부족의 연쇄 번개. 명세서 4-4.
     *
     * 명세서는 "일정 확률로"라고 적지만 이 엔진은 난수를 쓰지 않는다. 그래서 정수 누적 충전으로
     * 옮겼다. 기본 공격마다 단계별 충전량을 더하고 100 을 넘으면 터뜨린 뒤 100 을 뺀다.
     * 기대 발동 빈도가 확률과 같고 분산만 0 이며, M번 공격하면 발동 횟수가 정확히
     * `M x 충전량 / 100` 이라 어떤 튜닝 값에도 성립하는 식으로 단언할 수 있다.
     *
     * 충전은 기본 공격에서만 쌓인다. 대상은 주 대상을 빼고, 살아 있는 적 중
     * (주 대상 기준 거리 → 좌표 순서 → id) 정렬 상위 몇 명이다. 엔진이 이미 쓰는 결정적 기준이다.
     * 피해는 [CombatUnit.takeDamage] 를 다시 지나므로 방어력과 쉴드가 적용되고, 맞은 쪽은
     * 다른 피해와 똑같이 마나를 얻는다.
     */
    private fun chainLightning(
        tick: Int,
        attacker: CombatUnit,
        primary: CombatUnit,
        units: List<CombatUnit>,
        events: MutableList<CombatEvent>,
    ) {
        if (!attacker.buffs.hasChain) return
        if (!attacker.chargeChain()) return

        val targets = units.filter { it.isAlive && it.team == attacker.team.opponent && it.id != primary.id }
            .sortedWith(
                compareBy(
                    { HexGrid.distance(primary.position, it.position) },
                    { coordOrder[it.position] ?: Int.MAX_VALUE },
                    { it.id },
                ),
            )
            .take(attacker.buffs.chainTargets)
        // 터졌는데 튈 곳이 없으면 충전만 소모하고 조용히 불발한다.
        if (targets.isEmpty()) return

        val damage = attacker.buffs.chainDamage
        targets.forEach { victim ->
            victim.takeDamage(damage)
            victim.gainMana(CombatRules.MANA_PER_HIT_TAKEN)
        }

        events += CombatEvent.ChainLightning(tick, attacker.id, primary.id, targets.map { it.id }, damage)
        targets.filterNot { it.isAlive }.forEach { events += CombatEvent.Died(tick, it.id) }
    }

    /**
     * 스킬을 쓴다. 명세서 4-6.
     *
     * 4단계에서는 명세서가 요구한 "스킬 1종"으로 **피해형 스킬**만 구현한다.
     * 반경이 0이면 대상 하나, 1 이상이면 대상 주변까지 함께 맞는다.
     * 회복이나 군중제어 같은 다른 종류는 이후 단계에서 [com.leechanghyun.autobattler.core.model.SkillDef.id] 로 분기해 붙인다.
     */
    private fun castSkill(
        tick: Int,
        caster: CombatUnit,
        target: CombatUnit,
        units: List<CombatUnit>,
        events: MutableList<CombatEvent>,
    ) {
        val hit = units.filter { candidate ->
            candidate.isAlive &&
                candidate.team == caster.team.opponent &&
                HexGrid.distance(candidate.position, target.position) <= caster.skill.areaRadius
        }.sortedBy { it.id }

        caster.spendMana()
        caster.attackCooldown = caster.attackIntervalTicks

        hit.forEach { victim ->
            victim.takeDamage(caster.skillPower)
            victim.gainMana(CombatRules.MANA_PER_HIT_TAKEN)
        }

        events += CombatEvent.SkillCast(tick, caster.id, caster.skill.id, hit.map { it.id }, caster.skillPower)
        hit.filterNot { it.isAlive }.forEach { events += CombatEvent.Died(tick, it.id) }
    }

    private fun move(
        tick: Int,
        unit: CombatUnit,
        target: CombatUnit,
        occupied: MutableMap<HexCoord, CombatUnit>,
        events: MutableList<CombatEvent>,
    ) {
        val step = stepToward(unit, target, occupied) ?: return
        val from = unit.position

        occupied.remove(from)
        unit.position = step
        occupied[step] = unit
        unit.moveCooldown = CombatRules.MOVE_INTERVAL_TICKS

        events += CombatEvent.Moved(tick, unit.id, from, step)
    }

    // --- 판단 ---

    /** 가장 가까운 적. 거리가 같으면 id 순으로 골라 결과를 고정한다. */
    private fun nearestEnemy(unit: CombatUnit, units: List<CombatUnit>): CombatUnit? =
        units.filter { it.isAlive && it.team == unit.team.opponent }
            .minWithOrNull(
                compareBy({ HexGrid.distance(unit.position, it.position) }, { it.id }),
            )

    /**
     * 목표를 향해 한 칸 나아갈 위치. 이미 사거리 안이거나 길이 막혔으면 null.
     *
     * "대상을 공격할 수 있는 칸"들을 출발점으로 삼아 BFS 로 거리를 재고,
     * 지금 위치보다 더 가까워지는 이웃 칸으로 한 칸 움직인다.
     * 목표 쪽에서 거꾸로 퍼뜨리므로 BFS 를 한 번만 돌려도 된다.
     */
    private fun stepToward(
        unit: CombatUnit,
        target: CombatUnit,
        occupied: Map<HexCoord, CombatUnit>,
    ): HexCoord? {
        val range = unit.attackRange
        val goals = field.coords.filter { coord ->
            HexGrid.distance(coord, target.position) <= range &&
                (coord == unit.position || occupied[coord] == null)
        }
        if (goals.isEmpty() || unit.position in goals) return null

        val distance = HashMap<HexCoord, Int>(field.coords.size)
        val queue = ArrayDeque<HexCoord>()
        goals.forEach { goal ->
            distance[goal] = 0
            queue += goal
        }

        while (queue.isNotEmpty()) {
            val coord = queue.removeFirst()
            val next = distance.getValue(coord) + 1
            for (neighbor in field.neighbors(coord)) {
                if (neighbor in distance) continue
                // 자기 자신이 서 있는 칸은 지나갈 수 있는 칸으로 친다.
                if (neighbor != unit.position && occupied[neighbor] != null) continue
                distance[neighbor] = next
                queue += neighbor
            }
        }

        val current = distance[unit.position] ?: return null
        return field.neighbors(unit.position)
            .filter { occupied[it] == null }
            .filter { (distance[it] ?: Int.MAX_VALUE) < current }
            .minWithOrNull(compareBy({ distance.getValue(it) }, { coordOrder.getValue(it) }))
    }

    private fun isOver(units: List<CombatUnit>): Boolean =
        CombatTeam.entries.any { team -> units.none { it.isAlive && it.team == team } }

    private fun buildOutcome(
        units: List<CombatUnit>,
        ticks: Int,
        timedOut: Boolean,
        events: List<CombatEvent>,
    ): CombatOutcome {
        val playerHp = units.filter { it.isAlive && it.team == CombatTeam.PLAYER }.sumOf { it.hp }
        val enemyHp = units.filter { it.isAlive && it.team == CombatTeam.ENEMY }.sumOf { it.hp }

        val winner = when {
            playerHp > 0 && enemyHp == 0 -> CombatWinner.PLAYER
            enemyHp > 0 && playerHp == 0 -> CombatWinner.ENEMY
            // 시간 초과로 양쪽이 남았으면 남은 체력이 많은 쪽이 이긴다.
            playerHp > enemyHp -> CombatWinner.PLAYER
            enemyHp > playerHp -> CombatWinner.ENEMY
            else -> CombatWinner.DRAW
        }

        return CombatOutcome(
            winner = winner,
            ticks = ticks,
            timedOut = timedOut,
            survivors = units.filter { it.isAlive }.associate { it.id to it.hp },
            events = events,
        )
    }

    companion object {
        /**
         * 양쪽의 보드 배치로 전투를 준비한다.
         *
         * 벤치에 있는 유닛은 전투에 참가하지 않는다. 양쪽 개체 id 가 겹칠 수 있으므로
         * 진영 이름을 앞에 붙여 구분한다.
         */
        fun unitsFrom(playerBoard: List<BoardUnit>, enemyBoard: List<BoardUnit>): List<CombatUnit> =
            setupFrom(playerBoard, enemyBoard).units

        /**
         * 양쪽 보드와 각자의 시너지로 전투를 준비한다. 5단계.
         *
         * 여기가 버프의 이음매다. 이 함수만이 한 팀의 보드 전체를 한 번에 보고, 벤치를 빼는 일은
         * 이미 [CombatUnit.from] 에서 끝났으며, id 접두어를 붙이려고 어차피 유닛을 다시 만들고 있다.
         * 그 재생성이 그대로 버프 주입 지점이 된다.
         *
         * 시너지를 넘기지 않으면 4단계와 완전히 같은 전투가 된다. 보드에서 **자동으로** 시너지를
         * 계산하지 않는 것은 의도다. 기존 호출부의 동작이 조용히 바뀌면 안 된다.
         *
         * 기계공학자 6단계가 내보내는 소환 명세는 여기서 **의도적으로 무시한다.** 거대 골렘은
         * 10단계 PvE 몬스터와 같은 경로로 붙인다. [com.leechanghyun.autobattler.core.synergy.SummonSpec] 참고.
         */
        fun setupFrom(
            playerBoard: List<BoardUnit>,
            enemyBoard: List<BoardUnit>,
            playerSynergy: SynergyState = SynergyState.NONE,
            enemySynergy: SynergyState = SynergyState.NONE,
        ): CombatSetup {
            fun build(board: List<BoardUnit>, team: CombatTeam, synergy: SynergyState) =
                board.mapNotNull { CombatUnit.from(it, team) }
                    .map { unit ->
                        CombatUnit(
                            id = "${team.name.lowercase()}_${unit.id}",
                            team = unit.team,
                            def = unit.def,
                            skill = unit.skill,
                            starLevel = unit.starLevel,
                            position = unit.position,
                            buffs = synergy.combat.forUnit(unit.def),
                        )
                    }

            return CombatSetup(
                units = build(playerBoard, CombatTeam.PLAYER, playerSynergy) +
                    build(enemyBoard, CombatTeam.ENEMY, enemySynergy),
                synergyByTeam = mapOf(
                    CombatTeam.PLAYER to playerSynergy,
                    CombatTeam.ENEMY to enemySynergy,
                ),
            )
        }
    }
}
