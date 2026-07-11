# TODO 담당자 배정 푸시 발송 Cloud Function (이슈 #60)
# todo-items 문서 쓰기 시 담당자에게 FCM 푸시를 보낸다. 편집자 본인은 대상에서 제외한다.
# 대상 계산 규칙·메시지 형식은 doc/dev/infra/functions.md 참고. Deploy: `firebase deploy --only functions`

from firebase_admin import initialize_app, firestore, messaging
from firebase_functions import firestore_fn, logger
from firebase_functions.options import set_global_options
from google.cloud.firestore_v1.base_query import FieldFilter

# 비용 제어: 동시 실행 컨테이너 상한.
set_global_options(max_instances=10)

initialize_app()

# 담당자(enum 이름) → 대상 이메일 매핑. 앱 TodoAssignee.kt와 동일하게 유지한다.
# 이 매핑을 바꿀 때는 앱과 이 파일을 함께 고친다.
EMAIL_JEON_JIHOON = "supawer0728@gmail.com"
EMAIL_KWON_YUKYEONG = "fmx.yu.k@gmail.com"
ASSIGNEE_EMAILS = {
    # SHARED(공동)는 두 사용자 모두를 대상으로 한다. 앱의 기본값이기도 하다.
    "SHARED": [EMAIL_JEON_JIHOON, EMAIL_KWON_YUKYEONG],
    "JEON_JIHOON": [EMAIL_JEON_JIHOON],
    "KWON_YUKYEONG": [EMAIL_KWON_YUKYEONG],
}
# 앱 TodoAssignee.DEFAULT와 동일하게, 알 수 없는 값은 SHARED로 처리한다.
DEFAULT_ASSIGNEE = "SHARED"

# 앱 notification.CHANNEL_ID_TODO_ASSIGNMENT와 일치해야 한다. 백그라운드/종료 상태에서 FCM이
# notification 페이로드를 시스템 트레이에 자동 표시할 때 이 채널로 라우팅된다.
ASSIGNMENT_CHANNEL_ID = "todo_assignment"


@firestore_fn.on_document_written(document="todo-items/{itemId}", region="asia-northeast3")
def on_todo_item_written(event: firestore_fn.Event[firestore_fn.Change | None]) -> None:
    item_id = event.params["itemId"]
    change = event.data

    after_snapshot = change.after if change is not None else None
    if after_snapshot is None:
        # 삭제된 문서 → 발송할 것이 없다.
        logger.info("todo-item 삭제 → 알림 없음", item_id=item_id)
        return
    after = after_snapshot.to_dict() or {}

    before_snapshot = change.before if change is not None else None
    before = before_snapshot.to_dict() if before_snapshot is not None else None
    is_create = before is None

    assignee = after.get("assignee") or DEFAULT_ASSIGNEE
    title = after.get("title") or ""
    editor_uid = after.get("lastEditedByUid")

    # 배정(assignee)·제목(title)이 모두 그대로면 상태 순환·완료 전진 등 배정과 무관한 쓰기이므로
    # 알림을 보내지 않는다. 레거시 문서(assignee 필드 없음)가 처음 저장되며 None→"SHARED"로
    # 정규화되는 경우를 오탐하지 않도록, 양쪽을 대상 계산과 동일하게 정규화한 뒤 비교한다.
    if before is not None:
        before_assignee = before.get("assignee") or DEFAULT_ASSIGNEE
        before_title = before.get("title") or ""
        if before_assignee == assignee and before_title == title:
            logger.info("assignee·title 무변경 → 알림 생략", item_id=item_id)
            return
    target_emails = ASSIGNEE_EMAILS.get(assignee, ASSIGNEE_EMAILS[DEFAULT_ASSIGNEE])

    db = firestore.client()
    notification_title = "새 할일이 등록되었습니다" if is_create else "할일이 수정되었습니다"

    sent = 0
    notified_uids: set[str] = set()
    for email in target_emails:
        users = db.collection("users").where(filter=FieldFilter("email", "==", email)).stream()
        for user_doc in users:
            uid = user_doc.id
            # 편집자 본인 제외(자기 자신에게 알림 금지). SHARED면 상대방만 남는다.
            if uid == editor_uid:
                logger.info("편집자 본인 제외", item_id=item_id, uid=uid)
                continue
            # 같은 사용자가 여러 이메일 조회에 중복 등장해도 한 번만 보낸다.
            if uid in notified_uids:
                continue

            user = user_doc.to_dict() or {}
            token = (user.get("pushToken") or {}).get("token")
            if not token:
                logger.info("pushToken 없음 → 스킵", item_id=item_id, uid=uid)
                continue

            message = messaging.Message(
                notification=messaging.Notification(title=notification_title, body=title),
                android=messaging.AndroidConfig(
                    notification=messaging.AndroidNotification(channel_id=ASSIGNMENT_CHANNEL_ID),
                ),
                token=token,
            )
            try:
                messaging.send(message)
                sent += 1
                notified_uids.add(uid)
                logger.info("푸시 발송 성공", item_id=item_id, uid=uid)
            except Exception as exc:  # noqa: BLE001 - 한 대상 실패가 다른 대상 발송을 막지 않게 한다.
                logger.error("푸시 발송 실패", item_id=item_id, uid=uid, error=str(exc))

    logger.info("todo-item 알림 처리 완료", item_id=item_id, assignee=assignee, sent=sent)
