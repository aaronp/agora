package lupin.pub.sequenced

import lupin.BaseFlowSpec
import lupin.pub.sequenced.DurableProcessorDao.InvalidIndexException

class DurableProcessorDaoTest extends BaseFlowSpec {

  "DurableProcessorDao.finalIndex" should {
    "return the max persisted index" in {
      withDir { dir =>
        val dao = DurableProcessorDao.inDir[String](dir, 2)
        dao.finalIndex shouldBe None

        dao.writeDown(10, "ten") shouldBe true
        dao.finalIndex shouldBe None

        dao.markComplete(11)
        dao.finalIndex shouldBe Option(11)

        val anotherDaoInSameDir = DurableProcessorDao.inDir[String](dir, 2)
        anotherDaoInSameDir.finalIndex shouldBe Option(11)
      }
    }
  }
  "DurableProcessorDao.maxIndex" should {
    "return the max persisted index" in {
      withDir { dir =>
        val dao = DurableProcessorDao.inDir[String](dir, 2)
        dao.maxIndex shouldBe None

        dao.writeDown(10, "ten") shouldBe true
        dao.maxIndex shouldBe Option(10)

        val anotherDaoInSameDir = DurableProcessorDao.inDir[String](dir, 2)
        anotherDaoInSameDir.maxIndex shouldBe Option(10)

        dao.writeDown(110, "one ten") shouldBe true
        dao.maxIndex shouldBe Option(110)
      }
    }
  }

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
