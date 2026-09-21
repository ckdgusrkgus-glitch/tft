package com.leechanghyun.autobattler.core.persistence

import com.leechanghyun.autobattler.core.masterdata.MasterData
import com.leechanghyun.autobattler.core.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Room TypeConverter 가 쓰는 인코딩이 값을 잃지 않는지 검증한다. */
class ValueCodecTest {

    @Test
    fun `문자열 리스트를 왕복해도 값이 유지된다`() {
        val values = listOf("comp_might", "comp_wisdom")
        assertEquals(values, ValueCodec.decodeStringList(ValueCodec.encodeStringList(values)))
        assertEquals(emptyList<String>(), ValueCodec.decodeStringList(ValueCodec.encodeStringList(emptyList())))
    }

    @Test
    fun `정수 리스트를 왕복해도 값이 유지된다`() {
        val thresholds = listOf(2, 4, 6)
        assertEquals(thresholds, ValueCodec.decodeIntList(ValueCodec.encodeIntList(thresholds)))
    }

    @Test
    fun `한글이 섞인 효과 설명 맵을 왕복해도 값이 유지된다`() {
        val effects = mapOf(
            2 to "아군 전체 방어력/마법저항력 증가",
            6 to "아군 전체 방어력/마법저항력 대폭 증가 + 거대 골렘 소환",
        )
        assertEquals(effects, ValueCodec.decodeIntStringMap(ValueCodec.encodeIntStringMap(effects)))
    }

    @Test
    fun `스탯 맵을 왕복해도 값이 유지된다`() {
        val stats = mapOf(StatType.ATTACK_SPEED to 0.15f, StatType.MAX_HP to 150f)
        assertEquals(stats, ValueCodec.decodeStatMap(ValueCodec.encodeStatMap(stats)))
    }

    @Test
    fun `마스터 데이터 전체가 인코딩을 통과한다`() {
        MasterData.traits.forEach { trait ->
            assertEquals(trait.thresholds, ValueCodec.decodeIntList(ValueCodec.encodeIntList(trait.thresholds)))
            assertEquals(
                trait.effectsByThreshold,
                ValueCodec.decodeIntStringMap(ValueCodec.encodeIntStringMap(trait.effectsByThreshold)),
            )
        }
        MasterData.items.forEach { item ->
            assertEquals(
                item.statModifiers,
                ValueCodec.decodeStatMap(ValueCodec.encodeStatMap(item.statModifiers)),
            )
            assertEquals(item.recipe, ValueCodec.decodeStringList(ValueCodec.encodeStringList(item.recipe)))
        }
    }
}
