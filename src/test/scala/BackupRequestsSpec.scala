package com.autoscout24.backupRequests

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.autoscout24.eventpublisher24.events.TypedEventPublisher
import com.autoscout24.eventpublisher24.request.ScoutRequestMeta
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Span, _}
import org.specs2.mutable._
import org.mockito.Mockito._
import com.google.common.base.Stopwatch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BackupRequestsSpec extends Specification with ScalaFutures with IntegrationPatience {

  val actorSystem = ActorSystem("BlockingIoSpec")
  val eventPublisher = mock(classOf[TypedEventPublisher])
  implicit val scoutRequestMeta: Option[ScoutRequestMeta] = Some(ScoutRequestMeta())
  val twoSecTimeout = Timeout(Span(2000, Milliseconds))

  val backupRequests = new BackupRequests(actorSystem, eventPublisher)

  "A backup request is executed after a timeout, if the operation does not complete fast enough" in {
    val timeout = 1.millisecond
    val count = new AtomicInteger(0)

    def slowFirstRequestTakes2Seconds() =
      if (count.incrementAndGet() == 1) {
        Future {
          Thread.sleep(2000)
          "first request completed"
        }
      } else {
        Future.successful("second request completed")
      }

    val result = backupRequests.executeWithBackup(slowFirstRequestTakes2Seconds, Seq(timeout)).futureValue

    count.intValue() mustEqual 2
    result mustEqual "second request completed"
  }

  "A backup request is made immediately if the previous request returns an error" in {
    val count = new AtomicInteger(0)

    def firstRequestsIsFailingSecondSucceeds(): Future[String] =
      if (count.incrementAndGet() == 1) Future.failed(new InterruptedException("Bang! There goes your database."))
      else Future.successful("second request completed")

    val stopWatch = Stopwatch.createStarted()

    val result = backupRequests.executeWithBackup(firstRequestsIsFailingSecondSucceeds, Seq(1000.milliseconds)).futureValue(twoSecTimeout)
    result mustEqual "second request completed"

    stopWatch.stop()
    stopWatch.elapsed(TimeUnit.MILLISECONDS) must beLessThan(1000l)
  }


  "If the initial request completes before the timeout, no further request is made" in {
    val count = new AtomicInteger(0)

    def fasterThan200ms() = Future.successful(count.incrementAndGet())

    backupRequests.executeWithBackup(fasterThan200ms, Seq(200.milliseconds))
    val afterOneSec = akka.pattern.after(1.second, using = actorSystem.scheduler) { Future.successful(())}

    afterOneSec.futureValue(twoSecTimeout)
    count.intValue() mustEqual 1
  }

  "The result is available as soon as one of the requests completes" in {
    def allRequestsFast() = Future.successful("response")

    val stopWatch = Stopwatch.createStarted()
    backupRequests.executeWithBackup(allRequestsFast, Seq(500.milliseconds)).futureValue(twoSecTimeout)

    stopWatch.stop()
    stopWatch.elapsed(TimeUnit.MILLISECONDS) must beLessThan(200l)
  }
}

