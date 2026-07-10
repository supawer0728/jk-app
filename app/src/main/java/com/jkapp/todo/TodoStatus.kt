package com.jkapp.todo

// 완료/미완료 이분법을 대체하는 3단계 상태. ordinal 순서(미진행 -> 진행중 -> 완료)는 목록 정렬의
// 2차 키로 그대로 사용하므로 순서를 바꾸지 않는다.
enum class TodoStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
}

// status 없이 isCompleted만 저장된 기존 Firestore 문서를 읽을 때의 마이그레이션 규칙.
// 완료였으면 DONE, 아니면 NOT_STARTED로 본다(진행중은 새 개념이라 과거 데이터에 존재하지 않는다).
fun todoStatusFromLegacyCompleted(isCompleted: Boolean): TodoStatus =
    if (isCompleted) TodoStatus.DONE else TodoStatus.NOT_STARTED

fun String.toTodoStatusOrNull(): TodoStatus? =
    runCatching { TodoStatus.valueOf(this) }.getOrNull()
