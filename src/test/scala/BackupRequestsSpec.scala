package com.autoscout24.backupRequests

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.ActorSystem
import com.google.common.base.Stopwatch
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Span, _}
import org.specs2.mutable._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BackupRequestsSpec extends Specification with ScalaFutures with IntegrationPatience {

  val actorSystem = ActorSystem("BlockingIoSpec")
  val twoSecTimeout = Timeout(Span(2000, Milliseconds))

  val backupRequests = new BackupRequests(actorSystem)
  val callbackCalled = new AtomicBoolean(false)
  def backupRequestCallback(maybeMeta: Option[String], maybeFailure: Option[Throwable]): Unit = callbackCalled.set(true)

  "A backup request is executed after a timeout, if the operation does not complete fast enough" in {
    val timeout = 1.millisecond
    val count = new AtomicInteger(0)
    callbackCalled.set(false)

    def slowFirstRequestTakes2Seconds() =
      if (count.incrementAndGet() == 1) {
        Future {
          Thread.sleep(2000)
          "first request completed"
        }
      } else {
        Future.successful("second request completed")
      }

    val result = backupRequests.executeWithBackup(slowFirstRequestTakes2Seconds,
      Seq(timeout),
      Some("metaValue"),
      backupRequestCallback).futureValue

    count.intValue() mustEqual 2
    result mustEqual "second request completed"
    callbackCalled.get mustEqual true
  }

  "A backup request is made immediately if the previous request returns an error" in {
    val count = new AtomicInteger(0)
    callbackCalled.set(false)

    def firstRequestsIsFailingSecondSucceeds(): Future[String] =
      if (count.incrementAndGet() == 1) Future.failed(new InterruptedException("Bang! There goes your database."))
      else Future.successful("second request completed")

    val stopWatch = Stopwatch.createStarted()

    val result = backupRequests.executeWithBackup(firstRequestsIsFailingSecondSucceeds,
      Seq(1000.milliseconds),
      Some("metaValue"),
      backupRequestCallback).futureValue(twoSecTimeout)
    result mustEqual "second request completed"

    stopWatch.stop()
    stopWatch.elapsed(TimeUnit.MILLISECONDS) must beLessThan(1000l)
  }


  "If the initial request completes before the timeout, no further request is made" in {
    val count = new AtomicInteger(0)
    callbackCalled.set(false)

    def fasterThan200ms() = Future.successful(count.incrementAndGet())

    backupRequests.executeWithBackup(fasterThan200ms,
      Seq(200.milliseconds),
      Some("metaValue"),
      backupRequestCallback)
    val afterOneSec = akka.pattern.after(1.second, using = actorSystem.scheduler) { Future.successful(())}

    afterOneSec.futureValue(twoSecTimeout)
    count.intValue() mustEqual 1
  }

  "The result is available as soon as one of the requests completes" in {
    def allRequestsFast() = Future.successful("response")
    callbackCalled.set(false)


    val stopWatch = Stopwatch.createStarted()
    backupRequests.executeWithBackup(allRequestsFast,
      Seq(500.milliseconds),
      None,
      backupRequestCallback).futureValue(twoSecTimeout)

    stopWatch.stop()
    stopWatch.elapsed(TimeUnit.MILLISECONDS) must beLessThan(200l)
  }
}

