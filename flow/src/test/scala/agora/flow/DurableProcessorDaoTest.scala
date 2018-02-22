package agora.flow

import agora.flow.DurableProcessorDao.InvalidIndexException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._

class DurableProcessorDaoTest extends BaseFlowSpec {

  "DurableProcessorDao.inDir" should {
    "only keep the specified number of elements" in {
      withDir { dir =>
        val dao = DurableProcessorDao.inDir[String](dir, 2)

        dao.writeDown(10, "ten") shouldBe true
        dir.children.map(_.fileName).toList shouldBe List("10")

        dao.writeDown(11, "eleven") shouldBe true
        dir.children.map(_.fileName).toList should contain only ("10", "11")

        dao.writeDown(12, "twelve") shouldBe true
        withClue("10 should be deleted as we only are keeping 2") {
          dir.children.map(_.fileName).toList should contain only ("11", "12")
        }

        dao.at(12).get shouldBe "twelve"
        dao.at(11).get shouldBe "eleven"
        val InvalidIndexException(idx, _) = intercept[InvalidIndexException] {
          dao.at(10).get
        }
        idx shouldBe 10
      }
    }
  }
}
