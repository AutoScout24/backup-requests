package com.autoscout24.backupRequests

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.ActorSystem
import com.google.common.base.Stopwatch
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Span, _}
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class BackupRequestsSpec
    extends WordSpec
    with ScalaFutures
    with IntegrationPatience
    with MustMatchers {

  val actorSystem = ActorSystem("BlockingIoSpec")
  val twoSecTimeout = Timeout(Span(2000, Milliseconds))

  val backupRequests = new BackupRequests(actorSystem)
  val callbackCalled = new AtomicBoolean(false)

  def backupRequestCallback(maybeMeta: Option[String],
                            maybeFailure: Option[Throwable]): Unit =
    callbackCalled.set(true)

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

    val result = backupRequests
      .executeWithBackup(slowFirstRequestTakes2Seconds _,
                         Seq(timeout),
                         Some("metaValue"),
                         backupRequestCallback)
      .futureValue

    count.intValue() must be(2)
    result must be("second request completed")
    callbackCalled.get mustEqual true
  }

  "A backup request is made immediately if the previous request returns an error" in {
    val count = new AtomicInteger(0)
    callbackCalled.set(false)

    def firstRequestsIsFailingSecondSucceeds(): Future[String] =
      if (count.incrementAndGet() == 1)
        Future.failed(
          new InterruptedException("Bang! There goes your database."))
      else Future.successful("second request completed")

    val stopWatch = Stopwatch.createStarted()

    val result = backupRequests
      .executeWithBackup(firstRequestsIsFailingSecondSucceeds _,
                         Seq(1000.milliseconds),
                         Some("metaValue"),
                         backupRequestCallback)
      .futureValue(twoSecTimeout)
    result mustEqual "second request completed"

    stopWatch.stop()
    stopWatch.elapsed(TimeUnit.MILLISECONDS) must be < 1000l
  }

  "If the initial request completes before the timeout, no further request is made" in {
    val count = new AtomicInteger(0)
    callbackCalled.set(false)

    def fasterThan200ms() = Future.successful(count.incrementAndGet())

    backupRequests.executeWithBackup(fasterThan200ms _,
                                     Seq(200.milliseconds),
                                     Some("metaValue"),
                                     backupRequestCallback)
    val afterOneSec =
      akka.pattern.after(1.second, using = actorSystem.scheduler) {
        Future.successful(())
      }

    afterOneSec.futureValue(twoSecTimeout)
    count.intValue() mustEqual 1
  }

  "The result is available as soon as one of the requests completes" in {
    def allRequestsFast() = Future.successful("response")
    callbackCalled.set(false)

    val stopWatch = Stopwatch.createStarted()
    backupRequests
      .executeWithBackup(allRequestsFast _,
                         Seq(500.milliseconds),
                         None,
                         backupRequestCallback)
      .futureValue(twoSecTimeout)

    stopWatch.stop()
    stopWatch.elapsed(TimeUnit.MILLISECONDS) must be < 200l
  }

  "A successful first request is taken even if the failed request is faster" in {
    val count = new AtomicInteger(0)

    def firstFailsAndSecondIsSlowSuccessfulRequest() =
      if (count.incrementAndGet() == 1) {
        Future {
          Thread.sleep(400)
          "success"
        }
      } else {
        Future.failed(new Exception("boom!"))
      }

    def backupRequestsCallback(m: Option[String],
                               t: Option[Throwable]): Unit = {}

    backupRequests
      .executeWithBackup(firstFailsAndSecondIsSlowSuccessfulRequest _,
                         Seq(10.milliseconds),
                         None,
                         backupRequestsCallback)
      .futureValue must be("success")
  }

  "A successful second request is taken even if the failed first request is faster" in {
    val count = new AtomicInteger(0)

    def firstFailsAndSecondIsSlowSuccessfulRequest() =
      if (count.incrementAndGet() == 1)
        Future.failed(new Exception("boom!"))
      else
        Future { Thread.sleep(400); "success" }

    def backupRequestsCallback(m: Option[String],
                               t: Option[Throwable]): Unit = {}

    backupRequests
      .executeWithBackup(firstFailsAndSecondIsSlowSuccessfulRequest _,
                         Seq(10.milliseconds),
                         None,
                         backupRequestsCallback)
      .futureValue must be("success")
  }

  "Promise is completed even if all requests fail" in {
    def failAlways(): Future[String] = Future.failed(new Exception("boom!"))

    def backupRequestsCallback(m: Option[String],
                               t: Option[Throwable]): Unit = {}

    val result = backupRequests.executeWithBackup(failAlways _,
                                                  Seq(10.milliseconds),
                                                  None,
                                                  backupRequestsCallback)
    result.foreach(_ =>
      fail("These backup requests should not complete successfully"))
    result.failed.foreach(t => t.getMessage must be("boom!"))

    Try(result.futureValue)
  }
}
