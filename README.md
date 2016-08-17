# Backup Requests Library

Backup requests library for Scala applications

## Status

[![Build Status](https://travis-ci.org/AutoScout24/backup-requests.svg)](https://travis-ci.org/AutoScout24/backup-requests)
[![Coverage Status](https://coveralls.io/repos/AutoScout24/backup-requests/badge.svg)](https://coveralls.io/r/AutoScout24/backup-requests)

## Setup

Add to your `build.sbt` following resolver with dependency:

```scala
resolvers += Resolver.bintrayRepo("tatsu-kondor", "maven")

libraryDependencies += "com.autoscout24" %% "backup-requests" % "(see version number above)"
```

## How To Use

In your guice module:

```scala

class AppModule extends AbstractModule{
  ...
    
  @Provides @Singleton
  def backupRequests(actorSystem: ActorSystem): BackupRequests = new BackupRequests(actorSystem)

}

```

In your code:

```scala

import play.api.libs.concurrent.Execution.Implicits._
import com.autoscout24.backupRequests.BackupRequests

def backupRequestsCallback(maybeMetadata: Option[ScoutRequestMeta], maybeFailure: Option[Throwable]): Unit = {
  eventPublisher.publish(BackupRequestFired(maybeFailure.isDefined, maybeFailure)(maybeMetadata))
}

backupRequests.executeWithBackup(doRequest, Seq(200 milliseconds), Some(scoutRequestMeta), backupRequestsCallback)

```

## Copyright

Copyright (C) 2016 AutoScout24 GmbH.

Distributed under the MIT License.