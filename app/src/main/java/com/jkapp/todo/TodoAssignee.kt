package com.jkapp.todo

// enum 엔트리는 companion object보다 먼저 초기화되므로, 엔트리 생성자에서 참조할 이메일 상수는
// 파일 최상위에 둔다. 공개 API(TodoAssignee.EMAIL_*)는 companion에서 이 값을 다시 노출한다.
private const val JEON_JIHOON_EMAIL = "supawer0728@gmail.com"
private const val KWON_YUKYEONG_EMAIL = "fmx.yu.k@gmail.com"

// 가족 2인 협업용 담당자. 이메일 매핑은 추후 푸시 알림 대상 식별에 쓰기 위해 하드코딩한다(이슈 #71).
// 목록은 상수/enum으로 고정하며, 별도 사용자 관리 화면은 두지 않는다.
enum class TodoAssignee(val emails: List<String>) {
    // 공동은 두 사용자 모두를 대상으로 한다. 기존(담당자 개념이 없던) 문서의 기본값이기도 하다.
    SHARED(listOf(JEON_JIHOON_EMAIL, KWON_YUKYEONG_EMAIL)),
    KWON_YUKYEONG(listOf(KWON_YUKYEONG_EMAIL)),
    JEON_JIHOON(listOf(JEON_JIHOON_EMAIL)),
    ;

    companion object {
        const val EMAIL_JEON_JIHOON = JEON_JIHOON_EMAIL
        const val EMAIL_KWON_YUKYEONG = KWON_YUKYEONG_EMAIL

        val DEFAULT = SHARED

        fun fromNameOrDefault(name: String?): TodoAssignee =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEFAULT
    }
}
