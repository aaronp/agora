package agora.exec.run

import agora.exec.dao.{ExecDao, UploadDao}
import agora.exec.model.{RunProcess, Upload}
import agora.rest.{BaseSpec, HasMaterializer}

class RequestLoggingRunnerTest extends BaseSpec with HasMaterializer {

  "RequestLoggingRunner" should {
    "write down requests" in {
      withDir { requestSaveToDir =>
        withDir { localDir =>
          // create some runner which we'll wrap
          val underlying = LocalRunner(UploadDao(localDir))

          // create a request logging runners
          val dao = ExecDao(requestSaveToDir)(materializer.executionContext)
          val rlr = new RequestLoggingRunner(underlying, dao)

          // TODO - add working dir to RunProcess
          val input: Upload  = Upload.forText("input", "some\ninput\ndata")
          val output: Upload = Upload.forText("output", "some\ninitial\noutput")
          val rp             = RunProcess("sh", "-c", "cat $INPUT > $OUTPUT").addMetadata("find" -> "me")

          // call the method under test -- the request should be written down
          val result = rlr.run(rp, List(input, output)).futureValue.toList
          result shouldBe Nil

          //val List(input, output) = saved.read.futureValue
          val found = dao.findJobsForEntry("find", "me")
          found.size shouldBe 1

          val (readBack, uploads) = dao.get(found.head).futureValue
          uploads.size shouldBe 2
          readBack.command shouldBe List("sh", "-c", "cat $INPUT > $OUTPUT")
          uploads.map(_.name) should contain only ("input", "output")

        }
      }

    }
  }
}
