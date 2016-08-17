# Backup Requests Library

Backup requests library for Scala applications. Inspired by Google's idea to do additional duplicate requests to achieve better latency. 
Based on the code by the "that's classifed" Autoscout24 team.

This library enables you to do additional `scala.concurrent.Future` executions if a former execution 

* does not return a result within some duration or 

* if they are completed with an exception

## Status

[![Build Status](https://travis-ci.org/AutoScout24/backup-requests.svg)](https://travis-ci.org/AutoScout24/backup-requests)
[![Coverage Status](https://coveralls.io/repos/AutoScout24/backup-requests/badge.svg)](https://coveralls.io/r/AutoScout24/backup-requests)
[![Download](https://api.bintray.com/packages/tatsu-kondor/maven/backup-requests/images/download.svg) ](https://bintray.com/tatsu-kondor/maven/backup-requests/_latestVersion)

## Setup

Add to your `build.sbt` following resolver with dependency:

```scala
resolvers += Resolver.bintrayRepo("tatsu-kondor", "maven")

libraryDependencies += "com.autoscout24" %% "backup-requests" % "(see version number above)"
```

## How To Use

How to integrate it in your play guice application module:

```scala

class AppModule extends AbstractModule {
  ...
    
  @Provides @Singleton
  def backupRequests(actorSystem: ActorSystem): BackupRequests = new BackupRequests(actorSystem)

}

```

In your code (example):

```scala

import play.api.libs.concurrent.Execution.Implicits._
import com.autoscout24.backupRequests.BackupRequests

def doRequest[T](): () => Future[T] = ...

def backupRequestsCallback(maybeMetadata: Option[YetAnotherMetadata], maybeFailure: Option[Throwable]): Unit = {
  eventPublisher.publish(BackupRequestFired(maybeFailure.isDefined, maybeFailure)(maybeMetadata))
}

backupRequests.executeWithBackup(doRequest, Seq(200 milliseconds), Some(scoutRequestMeta), backupRequestsCallback)

```

The above code executes the doRequest() again if the initial doRequest call is not completed successfully after 200ms. If the initial (and only the initial!) call
is completed with a failure, also an additional call is done. If you want more than one backup request you simply may specify more durations.

## Copyright

Copyright (C) 2016 AutoScout24 GmbH.

Distributed under the MIT License.