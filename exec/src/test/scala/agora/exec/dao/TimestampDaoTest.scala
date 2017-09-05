package agora.exec.dao

import agora.api.BaseSpec
import io.circe.generic.auto._

object TimestampDaoTest {
  case class Value(id: String)
}
class TimestampDaoTest extends BaseSpec {
  "TimestampDao.find" should {
    "find saved values within a time range" in {
      withDir { dir =>
//        val dao = TimestampDao(dir)
      }

    }
  }
}
