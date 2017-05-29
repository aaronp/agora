package jabroni.exec.run

import jabroni.exec.dao.{ExecDao, UploadDao}
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.rest.{BaseSpec, HasMaterializer}

class RequestLoggingRunnerTest extends BaseSpec with HasMaterializer {

  "RequestLoggingRunner" should {
    "write down requests" in {
      withDir { dir =>
        withDir { localDir =>

          val dao = ExecDao(dir)
          val rlr = new RequestLoggingRunner(LocalRunner(UploadDao(localDir)), dao)

          val input: Upload = Upload.forText("input", "some\ninput\ndata")
          val output: Upload = Upload.forText("output", "some\ninitial\noutput")

          // call the method under test -- the request should be written down
          // TODO - add working dir to RunProcess
          val rp = RunProcess("sh", "-c", "cat $INPUT > $OUTPUT").addMetadata("find" -> "me")

          val result = rlr.run(rp, List(input, output)).futureValue.toList
          result shouldBe Nil

          //val List(input, output) = saved.read.futureValue
          val found = dao.findJobsForEntry("find", "me")
          found.size shouldBe 1

          val (readBack, uploads) = dao.get(found.head).futureValue
          uploads.size shouldBe 2
          readBack.command shouldBe List("sh", "-c", "cat $INPUT > $OUTPUT")
          uploads.map(_.name) should contain only("input", "output")

        }
      }

    }
  }
}
