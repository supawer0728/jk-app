package com.jkapp.push

// 대상(토큰)이 이미 해석·제외 처리된 발송 요청(이슈 #89). 담당자 이메일 매핑, 편집자 본인 제외 등
// "누구에게 보낼지"는 이 패키지가 모르고, 호출한 feature(예: todo.TodoFirestoreRepositoryImpl)가
// 계산을 끝낸 뒤 이 data class로 넘긴다. PushRepository.createPush가 pushes 문서로 적재하면
// Cloud Functions가 tokens 그대로 FCM을 발송한다.
data class PushMessage(
    val title: String,
    val body: String,
    // Android 알림 채널 id(예: notification.CHANNEL_ID_TODO_ASSIGNMENT = "todo_assignment").
    // 이 패키지는 채널의 의미를 모르며 문자열을 그대로 전달할 뿐이다.
    val channelId: String,
    val tokens: List<String>,
)
