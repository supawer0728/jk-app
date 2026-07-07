import { onDocumentWritten } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

export const helloWorldTrigger = onDocumentWritten(
  { document: "todo-items/{itemId}", region: "asia-northeast3" },
  (event) => {
    logger.info("hello world trigger fired", { itemId: event.params.itemId });
  }
);
