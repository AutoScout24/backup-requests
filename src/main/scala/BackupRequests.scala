package com.autoscout24.backupRequests

import TypedEvents.BackupRequestFired
import akka.actor.ActorSystem
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.autoscout24.eventpublisher24.request.ScoutRequestMeta
import com.google.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Execute a future op with the possibility to retry it after some duration if the future
  * has not yet completed or has been completed with a failure.
  */
@Singleton
class BackupRequests @Inject()(actorSystem: ActorSystem, eventPublisher: TypedEventPublisher) {

  /**
    * Uses backup requests to reduce p99.9 response times.
    *
    * @param op                  Executes the IO operation
    * @param backupRequestsAfter A sequence of durations when the backup request should be executed
    */
  def executeWithBackup[T](op: () => Future[T], backupRequestsAfter: Seq[FiniteDuration])
                          (implicit maybeScoutRequestMeta: Option[ScoutRequestMeta]): Future[T] = {
    val initial = op().recoverWith {
      case t =>
        eventPublisher.publish(BackupRequestFired(originalRequestCompleted = true, Some(t)))
        op()
    }
    backupRequestsAfter.foldLeft(initial) {
      (acc, timeout) => setupBackupTimeout(acc, op, timeout)
    }
  }

  private def setupBackupTimeout[T](previous: Future[T], op: () => Future[T], timeout: FiniteDuration)
                                   (implicit maybeScoutRequestMeta: Option[ScoutRequestMeta]): Future[T] = {
    val backup = akka.pattern.after(timeout, using = actorSystem.scheduler) {
      val value = previous.value // nonEmpty and flatMap below use the same snapshot in time for the future value
      val completed = value.nonEmpty
      val maybeFailure = value.flatMap(_.failed.toOption)

      if (completed) previous
      else {
        eventPublisher.publish(BackupRequestFired(completed, maybeFailure))
        op()
      }
    }

    Future.firstCompletedOf(previous :: backup :: Nil)
  }
}
