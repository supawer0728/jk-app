# 푸시 발송 전담 Cloud Function (이슈 #89)
# pushes 컬렉션에 문서가 생성되면(status="pending") 문서에 이미 적재된 토큰 목록으로 FCM을
# 발송하고, 결과를 status/sentAt/results로 기록한다. 발송 대상(담당자 해석, 편집자 제외 등) 계산은
# 앱(com.jkapp.push, com.jkapp.todo.TodoFirestoreRepositoryImpl)이 담당하므로 이 함수는 users나
# todo-items를 조회하지 않는다. 스키마·규칙은 doc/dev/infra/push.md, functions.md 참고.
# Deploy: `firebase deploy --only functions`

from firebase_admin import initialize_app, firestore, messaging
from firebase_functions import firestore_fn, logger
from firebase_functions.options import set_global_options

# 비용 제어: 동시 실행 컨테이너 상한.
set_global_options(max_instances=10)

initialize_app()

STATUS_PENDING = "pending"
STATUS_SENT = "sent"
STATUS_FAILED = "failed"


@firestore_fn.on_document_created(document="pushes/{pushId}", region="asia-northeast3")
def on_push_created(event: firestore_fn.Event[firestore_fn.DocumentSnapshot | None]) -> None:
    push_id = event.params["pushId"]
    snapshot = event.data
    if snapshot is None:
        logger.info("push 문서 없음 → 스킵", push_id=push_id)
        return
    data = snapshot.to_dict() or {}

    status = data.get("status")
    if status != STATUS_PENDING:
        # 재처리(에뮬레이터 재시도 등)로 이미 처리된 문서가 다시 트리거되는 경우를 막는다.
        logger.info("status가 pending이 아님 → 스킵", push_id=push_id, status=status)
        return

    title = data.get("title") or ""
    body = data.get("body") or ""
    channel_id = data.get("channelId") or ""
    tokens = data.get("tokens") or []

    results = []
    sent_count = 0
    for token in tokens:
        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            android=messaging.AndroidConfig(
                notification=messaging.AndroidNotification(channel_id=channel_id),
            ),
            token=token,
        )
        try:
            messaging.send(message)
            results.append({"token": token, "success": True, "error": None})
            sent_count += 1
            logger.info("푸시 발송 성공", push_id=push_id, token=token)
        except Exception as exc:  # noqa: BLE001 - 한 토큰 실패가 다른 토큰 발송을 막지 않게 한다.
            results.append({"token": token, "success": False, "error": str(exc)})
            logger.error("푸시 발송 실패", push_id=push_id, token=token, error=str(exc))

    final_status = STATUS_SENT if sent_count > 0 else STATUS_FAILED
    db = firestore.client()
    db.collection("pushes").document(push_id).update({
        "status": final_status,
        "sentAt": firestore.SERVER_TIMESTAMP,
        "results": results,
    })
    logger.info(
        "push 처리 완료", push_id=push_id, status=final_status, sent=sent_count, total=len(tokens)
    )
