package com.autoscout24.backupRequests

import com.autoscout24.eventpublisher24.events.TypedEvent
import com.autoscout24.eventpublisher24.request.ScoutRequestMeta

object TypedEvents {
  case class BackupRequestFired(originalRequestCompleted: Boolean, maybeOriginalRequestFailure: Option[Throwable])(implicit maybeSoutRequestMeta: Option[ScoutRequestMeta])
    extends TypedEvent("backup-request-fired", maybeSoutRequestMeta)
}
