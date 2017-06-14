package agora.rest.support

import akka.http.scaladsl.client.RequestBuilding
import agora.rest.{BaseSpec, HasMaterializer}
import io.circe.generic.auto._
import scala.concurrent.duration._

class RequestDaoTest extends BaseSpec with RequestBuilding with HasMaterializer {

  import RequestDaoTest._
  import materializer._

  "RequestDao.save" should {
    "write down requests" in withDir { dir =>
      val dao = RequestDao(dir)

      val request  = Post("foo/bar", ThisIsMyRequest(1, 2, 3))
      val id       = dao.writeDown(request).futureValue
      val readBack = dao.read(id).futureValue

      readBack.uri shouldBe request.uri
      readBack.method shouldBe request.method
      readBack.headers shouldBe request.headers

      readBack.entity.toStrict(1.second).futureValue.data.utf8String shouldBe request.entity.toStrict(1.second).futureValue.data.utf8String
    }
  }
}

object RequestDaoTest {

  case class ThisIsMyRequest(x: Int, y: Int, z: Int)

}
