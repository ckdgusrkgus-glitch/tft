pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

/**
 * Gradle 을 돌리는 JDK 가 지원 범위 안인지 먼저 확인한다.
 *
 * Gradle 8.11.1 은 자바 23 까지만 그 위에서 돌 수 있고, 자바 25 는 Gradle 9.1.0 이상을 요구한다.
 * (https://docs.gradle.org/current/userguide/compatibility.html)
 * 안드로이드 스튜디오가 번들하는 JBR 이 25 로 올라가면서 기본 설정 그대로는 빌드가 깨지는데,
 * 그때 나오는 메시지가 `* What went wrong:` 아래 `25.0.2` 한 줄이 전부라 원인을 알 수 없다.
 * 그래서 여기서 먼저 걸러 무엇을 어떻게 고쳐야 하는지 알려 준다.
 *
 * `JavaVersion.current()` 를 쓰지 않는 이유는 Gradle 이 모르는 버전에서 어떻게 동작할지 보장이 없어서다.
 * 시스템 속성 문자열의 앞쪽 숫자만 직접 떼어 내면 어떤 값이 와도 예외가 나지 않는다.
 */
val runningJavaMajor = System.getProperty("java.version").orEmpty()
    .takeWhile { it.isDigit() }
    .toIntOrNull()
    ?: 0

check(runningJavaMajor in 17..21) {
    """
    |이 프로젝트는 JDK 17~21 에서 빌드된다. 지금 Gradle 은 JDK ${System.getProperty("java.version")} 위에서 돌고 있다.
    |
    |자바 24 이상은 Gradle 8.11.1 이 아예 못 받는다(자바 24 는 Gradle 8.14, 자바 25 는 9.1.0 필요).
    |22·23 은 Gradle 은 받지만 이 프로젝트가 검증한 건 LTS 인 17 과 21 뿐이다.
    |
    |고치는 법 (JDK 21 권장):
    |  안드로이드 스튜디오: Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK
    |                      에서 21 을 고른다. 없으면 같은 자리의 "Download JDK..." 로 21 을 받는다.
    |  터미널(./gradlew): 사용자 홈의 .gradle/gradle.properties 에 org.gradle.java.home 을 그 JDK 경로로 적거나,
    |                     JAVA_HOME 을 그 JDK 로 바꾼다. 스튜디오의 Gradle JDK 설정은 터미널에 적용되지 않는다.
    |
    |바꾼 뒤에는 ./gradlew --stop 으로 옛 데몬을 내려야 한다.
    """.trimMargin()
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "autobattler"
include(":app")
include(":core-game")
