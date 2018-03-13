package agora.exec.test

import java.time.ZoneOffset

import agora.api.time.{Timestamp, now}
import agora.exec.events._

import scala.util.Success

trait QueryClientScenarios {
  self: ExecutionIntegrationTest =>

  "QueryClient.systemStartTimesBetween" should {

    "be able to check system startup" in {
      conf.eventMonitorConfig.enabled shouldBe true
      val found: StartTimesBetweenResponse = conf.queryClient().query(StartTimesBetween(started, now())).futureValue

      found.systemStarts.size should be >= 0
      found.systemStarts.foreach { start =>
        withClue(s"${start.startTime} shouldBe after the start time of this test: $started") {
          start.startTime.isAfter(started) shouldBe true
        }
      }
    }
  }
  "QueryClient query StartTimesBetween" should {

    "be able to check system startup" in {
      conf.eventMonitorConfig.enabled shouldBe true
      val found: StartTimesBetweenResponse = conf.queryClient().query(StartTimesBetween(started, now())).futureValue

      found.systemStarts.size should be >= 0
      found.systemStarts.foreach { start =>
        withClue(s"${start.startTime} shouldBe after the start time of this test: $started") {
          start.startTime.isAfter(started) shouldBe true
        }
      }
    }
  }

  def findJobBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false) = {
    val StartedBetweenResponse(List(startedJob)) = conf.queryClient().query(StartedBetween(from, to, verbose)).futureValue
    val jobId                                    = startedJob.id

    jobId -> conf.queryClient().query(FindJob(jobId)).futureValue
  }

  "QueryClient query FindJob" should {
    "find jobs by their ID" in {
      val before = now()
      client.run("pwd").futureValue

      val (jobId, found) = findJobBetween(before, now())
      found.received.map(_.id) shouldBe Some(jobId)
    }
  }

  "QueryClient query StartedBetween" should {
    "not include the received details if verbose is false" in {

      val before = now()
      client.run("pwd").futureValue
      val done = now()

      val StartedBetweenResponse(List(startedJob)) = conf.queryClient().query(StartedBetween(before, done, verbose = false)).futureValue
      startedJob.started.isBefore(before) shouldBe false
      startedJob.started.isAfter(done) shouldBe false
      startedJob.details shouldBe None
    }
    "find jobs started in a time window and include the received job details if verbose is true" in {

      val before = now()
      client.run("pwd").futureValue
      val done = now()

      val StartedBetweenResponse(List(startedJob)) = conf.queryClient().query(StartedBetween(before, done, verbose = true)).futureValue
      startedJob.started.isBefore(before) shouldBe false
      startedJob.started.isAfter(done) shouldBe false
      startedJob.details.map(_.job.commandString) shouldBe Some("pwd")
    }

    "find all started jobs based on a filter" in {
      val before = now()
      client.run("echo", "hello").futureValue
      client.run("echo", "world").futureValue

      val StartedBetweenResponse(startedJobs) = conf.queryClient().query(StartedBetween(before, now(), true)).futureValue
      startedJobs.size shouldBe 2

      val StartedBetweenResponse(List(hello)) = conf.queryClient().query(StartedBetween(before, now(), true, filter = JobFilter("hello"))).futureValue
      hello.details.map(_.job.commandString) shouldBe Some("echo hello")

      val StartedBetweenResponse(List(world)) = conf.queryClient().query(StartedBetween(before, now(), true, filter = JobFilter("world"))).futureValue
      world.details.map(_.job.commandString) shouldBe Some("echo world")
    }
  }

  "QueryClient query CompletedBetween" should {
    "find jobs completed in a time window" in {

      val before = now()
      client.run("sleep", "0.25").futureValue
      val done = now()

      val CompletedBetweenResponse(List(completedJob)) = conf.queryClient().query(CompletedBetween(before, done)).futureValue
      completedJob.completed.isBefore(before) shouldBe false
      completedJob.completed.isAfter(done) shouldBe false
      completedJob.exitCode shouldBe Success(0)
    }
    "find jobs completed in a time window based on a filter" in {

      val before = now()
      client.run("echo", "first").futureValue
      client.run("echo", "second").futureValue
      val done = now()

      val CompletedBetweenResponse(List(first)) = conf.queryClient().query(CompletedBetween(before, done, filter = JobFilter("first"))).futureValue
      first.details.map(_.job.commandString) shouldBe Some("echo first")
      val CompletedBetweenResponse(List(second)) = conf.queryClient().query(CompletedBetween(before, done, filter = JobFilter("second"))).futureValue
      second.details.map(_.job.commandString) shouldBe Some("echo second")
      val CompletedBetweenResponse(both) = conf.queryClient().query(CompletedBetween(before, done, filter = JobFilter("s"))).futureValue
      both.size shouldBe 2

      val CompletedBetweenResponse(none) = conf.queryClient().query(CompletedBetween(before, done, filter = JobFilter("z"))).futureValue
      none shouldBe empty
    }
  }

  "QueryClient query NotFinishedBetween" should {
    "find jobs still running at a given time" in {

      val before = now()
      client.run("sleep", "0.25").futureValue

      val (_, found) = findJobBetween(before, now())

      val jobReceivedTime = found.received.get.received
      val jobStartTime    = found.started.get.started
      val completedTime   = found.completed.get.completed

      withClue(s"received at $jobReceivedTime, started at $jobStartTime, completed at $completedTime") {
        val NotFinishedBetweenResponse(stillRunning) = conf.queryClient().query(NotFinishedBetween(before, completedTime.minusNanos(1000000))).futureValue
        withClue(stillRunning.toString) {
          stillRunning.size shouldBe 1
        }
      }
    }
    "not find jobs which completed within the time specified" in {

      val before = now()
      client.run("sleep", "0.25").futureValue

      val (_, found) = findJobBetween(before, now())

      val jobReceivedTime = found.received.get.received
      val jobStartTime    = found.started.get.started
      val completedTime   = found.completed.get.completed

      withClue(s"received at $jobReceivedTime, started at $jobStartTime, completed at $completedTime") {
        val NotFinishedBetweenResponse(stillRunning) = conf.queryClient().query(NotFinishedBetween(before, completedTime.plusSeconds(1))).futureValue
        stillRunning shouldBe empty
      }
    }
  }

  "QueryClient query NotStartedBetween" should {
    "find jobs received but not started at a given time" in {
      conf.eventMonitorConfig.enabled shouldBe true

      val before = now()
      client.run("echo", "runme").futureValue

      val (_, found) = findJobBetween(before, now())

      val jobReceivedTime = found.received.get.received
      val jobStartTime    = found.started.get.started
      val completedTime   = found.completed.get.completed

      withClue(s"received at $jobReceivedTime, started at $jobStartTime, completed at $completedTime") {
        val took = if (jobReceivedTime.isBefore(jobStartTime)) {
          java.time.Duration.between(jobReceivedTime, jobStartTime)
        } else {
          // we need to investigate this -- we should always record 'received' before started
          java.time.Duration.between(jobStartTime, jobReceivedTime)
        }

        val betweenReceiveAndStart                = jobReceivedTime.plus(took.dividedBy(2))
        val NotStartedBetweenResponse(notStarted) = conf.queryClient().query(NotStartedBetween(before, betweenReceiveAndStart)).futureValue
        notStarted.size shouldBe 1
        notStarted.map(_.job.commandString) shouldBe List("echo runme")
      }

    }
    "not include jobs received and started within the specified time range" in {
      conf.eventMonitorConfig.enabled shouldBe true

      val before = now()
      client.run("echo", "runme").futureValue

      val (_, found) = findJobBetween(before, now())

      val jobReceivedTime = found.received.get.received
      val jobStartTime    = found.started.get.started
      val completedTime   = found.completed.get.completed

      withClue(s"received at $jobReceivedTime, started at $jobStartTime, completed at $completedTime, searching 'til ${jobStartTime.plusSeconds(1)}") {
        jobReceivedTime.isAfter(jobStartTime) shouldBe false
        val NotStartedBetweenResponse(notStarted) = conf.queryClient().query(NotStartedBetween(before, jobStartTime.plusSeconds(1))).futureValue
        notStarted shouldBe empty
      }
    }
  }

  "QueryClient query FindFirst" should {

    // TODO - test can fail w/ e.g.
    //
    // firstStarted =2018-03-12T17:54:35.655Z, firstReceived=2018-03-12T17:54:35.877Z, firstCompleted=2018-03-12T17:54:35.893Z
    //
    // we should sandbox this test
    "find the first started, received and completed job times" ignore {
      client.run("echo", "a").futureValue
      val beforeSecond = now()
      client.run("echo", "b").futureValue
      val FindFirstResponse(Some(firstReceived))  = conf.queryClient().query(FindFirst.received).futureValue
      val FindFirstResponse(Some(firstStarted))   = conf.queryClient().query(FindFirst.started).futureValue
      val FindFirstResponse(Some(firstCompleted)) = conf.queryClient().query(FindFirst.completed).futureValue

      withClue(s"firstReceived=$firstReceived, firstStarted =$firstStarted, firstCompleted=$firstCompleted") {
        firstReceived.isBefore(beforeSecond) shouldBe true
        firstReceived.isAfter(firstStarted) shouldBe false
        firstStarted.isAfter(firstCompleted) shouldBe false
      }
    }

  }

  "QueryClient query ReceivedBetween" should {
    "find jobs received within a time window when requested in a different time zone" in {
      val beforeEastern = now(ZoneOffset.ofHours(6))
      val beforeBST     = now(ZoneOffset.ofHours(0))
      client.run("echo", "a").futureValue
      val betweenEastern = now(ZoneOffset.ofHours(6))
      val betweenBST     = now(ZoneOffset.ofHours(0))
      client.run("echo", "b").futureValue
      val ReceivedBetweenResponse(List(first1)) = conf.queryClient().query(ReceivedBetween(beforeEastern, betweenBST)).futureValue
      val ReceivedBetweenResponse(List(first2)) = conf.queryClient().query(ReceivedBetween(beforeEastern, betweenEastern)).futureValue
      val ReceivedBetweenResponse(List(first3)) = conf.queryClient().query(ReceivedBetween(beforeBST, betweenEastern)).futureValue
      first1.job.commandString shouldBe "echo a"
      first2.job.commandString shouldBe "echo a"
      first3.job.commandString shouldBe "echo a"
    }

    "find jobs received within a time window" in {
      val before: Timestamp = now()
      client.run("echo", "a").futureValue
      val between = now()
      client.run("echo", "b").futureValue

      val ReceivedBetweenResponse(List(first)) = conf.queryClient().query(ReceivedBetween(before, between)).futureValue
      first.job.commandString shouldBe "echo a"
    }
  }
}
