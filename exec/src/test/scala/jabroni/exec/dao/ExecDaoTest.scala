package jabroni.exec.dao

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.rest.{BaseSpec, HasMaterializer}

import scala.concurrent.ExecutionContext

class ExecDaoTest extends BaseSpec with HasMaterializer {

  "ExecDao.get" should {
    "read back saved processes" in {
      withDir { daoDir =>
        withDir { inputDir =>

          val dao = ExecDao(daoDir)(ExecutionContext.Implicits.global)
          val expected = RunProcess("foo")
          val meh = Upload(inputDir.resolve("meh").text = "i am an upload")
          val foo = Upload(inputDir.resolve("meh_too").text = "so am I")

          // first save...
          dao.save("123", expected, List(meh, foo)).futureValue

          daoDir.children.map(_.fileName).toList should contain only ("123")
          daoDir.resolve("123").children.toList.map(_.fileName) should contain only("meh", "meh_too", ExecDao.RunProcessFileName)

          // then read back...
          val (rp, uploads) = dao.get("123").futureValue
          rp shouldBe expected
          uploads.map(_.name) should contain only("meh", "meh_too")

          def read(s: Source[ByteString, Any]) = s.runWith(Sink.head).futureValue.utf8String

          uploads.map(_.source).map(read) should contain only("i am an upload", "so am I")
        }
      }
    }
  }
}
