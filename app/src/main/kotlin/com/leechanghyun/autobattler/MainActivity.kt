package com.leechanghyun.autobattler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leechanghyun.autobattler.ui.shop.ShopRoute
import com.leechanghyun.autobattler.ui.theme.AutoBattlerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 단일 Activity. 화면 전환은 로드맵 2단계 이후 Navigation 으로 붙인다.
 *
 * 1단계에서는 완료 기준인 "상점 5칸에 실제 유닛 데이터가 랜덤 노출"만 확인할 수 있게
 * 상점 화면 하나만 띄운다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AutoBattlerTheme {
                ShopRoute()
            }
        }
    }
}
