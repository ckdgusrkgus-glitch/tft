package com.leechanghyun.autobattler.core.model

/**
 * "2-1" 같은 스테이지-라운드 번호. 명세서 4-8(증강)과 4-9(크립)가 쓰는 표기다.
 *
 * 엔진이 세는 라운드는 1부터 죽 올라가는 **평면 번호** 하나뿐이고, 스테이지 표기는 그것을 나눠
 * 보여주는 계산일 뿐이다. 두 값을 따로 들고 다니면 한쪽만 올라간 상태가 만들어진다.
 *
 * ### 스테이지당 4라운드는 명세서에서 나온다
 * 4-9 가 크립 라운드를 "**1-4, 2-4, 3-4, 4-4** (스테이지별 4번째 라운드)"로 적는다. 스테이지의
 * 4번째 라운드가 그 스테이지의 마지막이라는 뜻이므로 [ROUNDS_PER_STAGE] 는 4다. 같은 문장이
 * "증강 라운드와 겹치지 않도록 분리"라고 덧붙이는데, 4-8 의 2-1 / 3-2 / 4-2 는 실제로 N-4 와
 * 겹치지 않는다. 두 표가 서로를 검증해 주므로 이 값은 임의 초기값이 아니다.
 *
 * 평면 번호로는 증강이 5 / 10 / 14, 크립이 4 / 8 / 12 / 16 라운드다.
 *
 * ### "크립 라운드인가"는 여기 두지 않는다
 * `roundInStage == 4` 는 스테이지 5, 6, 7... 에서도 영원히 참이다. 몬스터 데이터는 스테이지
 * 1~4 까지만 있으므로 그 판정은 몬스터 표를 실제로 들여다보는 10단계의 일이다. 여기에 두면
 * "크립 라운드인데 몬스터가 없다"가 조용히 생긴다.
 */
@JvmInline
value class StageRound(val flat: Int) {

    init {
        require(flat >= 1) { "라운드 번호는 1부터 센다. 받은 값: $flat" }
    }

    /** 스테이지 번호. 1부터 센다. */
    val stage: Int get() = (flat - 1) / ROUNDS_PER_STAGE + 1

    /** 스테이지 안에서 몇 번째 라운드인지. 1..[ROUNDS_PER_STAGE] 다. */
    val roundInStage: Int get() = (flat - 1) % ROUNDS_PER_STAGE + 1

    /** 화면과 로그에 쓰는 "2-1" 표기. */
    val label: String get() = "$stage-$roundInStage"

    override fun toString(): String = label

    companion object {
        /** 한 스테이지의 라운드 수. 명세서 4-9 의 "스테이지별 4번째 라운드"에서 나온다. */
        const val ROUNDS_PER_STAGE = 4

        /** 1부터 세는 평면 라운드 번호로 만든다. */
        fun ofFlat(flatRound: Int): StageRound = StageRound(flatRound)

        /** "2-1" 처럼 스테이지와 스테이지 내 번호로 만든다. */
        fun of(stage: Int, roundInStage: Int): StageRound {
            require(stage >= 1) { "스테이지는 1부터 센다. 받은 값: $stage" }
            require(roundInStage in 1..ROUNDS_PER_STAGE) {
                "스테이지 내 라운드는 1..$ROUNDS_PER_STAGE 다. 받은 값: $roundInStage"
            }
            return StageRound((stage - 1) * ROUNDS_PER_STAGE + roundInStage)
        }
    }
}
