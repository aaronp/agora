package jabroni.exec.dao

import jabroni.exec.model.{RunProcess, Upload}
import jabroni.rest.{BaseSpec, HasMaterializer}

class ExecDaoTest extends BaseSpec with HasMaterializer {

  "ExecDao.get" should {
    "read back saved processes" in {
      val daoDir = "target/tmp/execDaoTest".asPath.mkDirs()

      try {
        val dao = ExecDao(daoDir)
        val expected = RunProcess("foo")
        val meh = Upload("meh".asPath.text = "i am an upload")
        val foo = Upload("foo".asPath.text = "so am I")

        // first save...
        dao.save("123", expected, List(meh, foo)).futureValue

        daoDir.children.map(_.fileName).toList should contain only ("123")
        daoDir.resolve("123").children.toList.map(_.fileName) should contain only("meh", "foo", ExecDao.RunProcessFileName)

        // then read back...
        val (rp, uploads) = dao.get("123").futureValue
        rp shouldBe expected
        uploads.map(_.name) should contain only("meh", "foo")
        uploads.map(_.name.asPath.text) should contain only("i am an upload", "so am I")
      } finally {
        daoDir.delete()
      }
    }
  }

}
