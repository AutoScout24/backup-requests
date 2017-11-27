package com.autoscout24.backupRequests

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Execute a future op with the possibility to retry it after some duration if the future
  * has not yet completed or has been completed with a failure.
  */
@Singleton
class BackupRequests @Inject()(actorSystem: ActorSystem) {

  /**
    * Uses backup requests to reduce p99.9 response times.
    *
    * @param op                  Executes the IO operation
    * @param backupRequestsAfter A sequence of durations when the backup request should be executed
    */
  def executeWithBackup[T, M](
      op: () => Future[T],
      backupRequestsAfter: Seq[FiniteDuration],
      maybeMetadata: Option[M],
      backupRequestFiredCallback: (Option[M], Option[Throwable]) => Unit)(
      implicit ec: ExecutionContext): Future[T] = {
    val initial = op().recoverWith {
      case t =>
        backupRequestFiredCallback(maybeMetadata, Some(t))
        op()
    }
    backupRequestsAfter.foldLeft(initial) { (acc, timeout) =>
      setupBackupTimeout(acc,
                         op,
                         timeout,
                         maybeMetadata,
                         backupRequestFiredCallback)
    }
  }

  private def setupBackupTimeout[T, M](
      previous: Future[T],
      op: () => Future[T],
      timeout: FiniteDuration,
      maybeMetadata: Option[M],
      backupRequestFiredCallback: (Option[M], Option[Throwable]) => Unit)(
      implicit ec: ExecutionContext): Future[T] = {
    val backup = akka.pattern.after(timeout, using = actorSystem.scheduler) {
      val value = previous.value // nonEmpty and flatMap below use the same snapshot in time for the future value
      val completed = value.nonEmpty
      val maybeFailure = value.flatMap(_.failed.toOption)

      if (completed) previous
      else {
        backupRequestFiredCallback(maybeMetadata, maybeFailure)
        op()
      }
    }

    BackupRequests.firstSuccessfulCompletedOf(previous :: backup :: Nil)
  }
}

object BackupRequests {

  def firstSuccessfulCompletedOf[T](futures: TraversableOnce[Future[T]])(
      implicit executor: ExecutionContext): Future[T] = {
    val p = Promise[T]()
    val completeWithSuccessfulValue: PartialFunction[T, Unit] = {
      case v => p trySuccess v
    }
    futures foreach { _ foreach completeWithSuccessfulValue }

    val matFutures: TraversableOnce[Future[Try[T]]] = futures.map { future =>
      val f: Future[Try[T]] = future
        .map(v => Success(v))
        .recover { case t: Throwable => Failure(t) }
      f
    }

    val sequence = Future.sequence(matFutures)

    sequence.foreach { results =>
      val futureSeq = results.toSeq
      val allFailure = results.forall(_.isFailure)
      if (allFailure)
        p.tryComplete(futureSeq.headOption.getOrElse(Failure(
          new IllegalArgumentException("futures argument must not be empty"))))
    }

    p.future
  }
}
