package com.leechanghyun.autobattler.core.combat

import com.leechanghyun.autobattler.core.board.HexGrid
import com.leechanghyun.autobattler.core.model.BoardUnit
import com.leechanghyun.autobattler.core.model.HexCoord

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
 * ### 아직 반영하지 않은 것
 * 방어력/마법저항은 유닛 마스터 데이터에 없어(명세서 4-4 표에 열이 없다) 피해가 그대로 들어간다.
 * 시너지 버프는 5단계, 아이템 효과는 7단계에서 이 계산에 끼워 넣는다.
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
            val occupied = units.filter { it.isAlive }.associateBy { it.position }.toMutableMap()

            for (unit in order) {
                if (!unit.isAlive) continue
                unit.attackCooldown = (unit.attackCooldown - 1).coerceAtLeast(0)
                unit.moveCooldown = (unit.moveCooldown - 1).coerceAtLeast(0)

                val target = nearestEnemy(unit, units) ?: continue
                val inRange = HexGrid.distance(unit.position, target.position) <= unit.def.attackRange

                when {
                    inRange && unit.canCastSkill -> castSkill(tick, unit, target, units, events)
                    inRange && unit.attackCooldown == 0 -> attack(tick, unit, target, events)
                    !inRange && unit.moveCooldown == 0 -> move(tick, unit, target, occupied, events)
                }
            }
        }

        return buildOutcome(units, tick, timedOut = tick >= maxTicks && !isOver(units), events = events)
    }

    // --- 행동 ---

    private fun attack(tick: Int, attacker: CombatUnit, target: CombatUnit, events: MutableList<CombatEvent>) {
        val dealt = target.takeDamage(attacker.attackDamage)
        attacker.attackCooldown = attacker.attackIntervalTicks
        attacker.gainMana(CombatRules.MANA_PER_ATTACK)
        target.gainMana(CombatRules.MANA_PER_HIT_TAKEN)

        events += CombatEvent.Attacked(tick, attacker.id, target.id, dealt)
        if (!target.isAlive) events += CombatEvent.Died(tick, target.id)
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
        val range = unit.def.attackRange
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
        fun unitsFrom(playerBoard: List<BoardUnit>, enemyBoard: List<BoardUnit>): List<CombatUnit> {
            fun build(board: List<BoardUnit>, team: CombatTeam) =
                board.mapNotNull { CombatUnit.from(it, team) }
                    .map { unit ->
                        CombatUnit(
                            id = "${team.name.lowercase()}_${unit.id}",
                            team = unit.team,
                            def = unit.def,
                            skill = unit.skill,
                            starLevel = unit.starLevel,
                            position = unit.position,
                        )
                    }

            return build(playerBoard, CombatTeam.PLAYER) + build(enemyBoard, CombatTeam.ENEMY)
        }
    }
}
