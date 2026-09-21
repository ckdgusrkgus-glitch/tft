package com.leechanghyun.autobattler.core.persistence

import com.leechanghyun.autobattler.core.model.StatType

/**
 * 리스트/맵을 문자열 한 칸에 넣고 빼는 인코더.
 *
 * Room 은 컬럼 하나에 원시 타입만 넣을 수 있어서, `List<Int>` 나 `Map<StatType, Float>` 같은 값은
 * 문자열로 바꿔 저장해야 한다(TypeConverter). 그 변환 로직을 안드로이드에 의존하지 않는 이 모듈에 두면
 * 단위테스트로 검증할 수 있고, 앱 쪽 TypeConverter 는 이 함수를 부르기만 하는 얇은 껍데기가 된다.
 *
 * 구분자로는 사람이 쓰는 문자와 겹치지 않는 ASCII 제어문자를 쓴다.
 * (0x1E = record separator, 0x1F = unit separator)
 */
object ValueCodec {

    private const val RECORD = '\u001E'
    private const val UNIT = '\u001F'

    fun encodeStringList(values: List<String>): String = values.joinToString(RECORD.toString())

    fun decodeStringList(encoded: String): List<String> =
        if (encoded.isEmpty()) emptyList() else encoded.split(RECORD)

    fun encodeIntList(values: List<Int>): String = encodeStringList(values.map { it.toString() })

    fun decodeIntList(encoded: String): List<Int> = decodeStringList(encoded).map { it.toInt() }

    fun encodeIntStringMap(value: Map<Int, String>): String =
        encodeStringList(value.entries.map { "${it.key}$UNIT${it.value}" })

    fun decodeIntStringMap(encoded: String): Map<Int, String> =
        decodeStringList(encoded).associate { entry ->
            val key = entry.substringBefore(UNIT)
            val text = entry.substringAfter(UNIT, "")
            key.toInt() to text
        }

    fun encodeStatMap(value: Map<StatType, Float>): String =
        encodeStringList(value.entries.map { "${it.key.name}$UNIT${it.value}" })

    fun decodeStatMap(encoded: String): Map<StatType, Float> =
        decodeStringList(encoded).associate { entry ->
            val key = entry.substringBefore(UNIT)
            val number = entry.substringAfter(UNIT, "")
            StatType.valueOf(key) to number.toFloat()
        }
}
