package crud.api

import agora.BaseIOSpec
import agora.io.ToBytes
import cats.effect.IO
import monix.eval.Task
import monix.execution.Scheduler

class CrudDslTest extends BaseIOSpec {

  "CrudDsl" should {
    "explain operations" in {
      case class Person(name: String, age: Int)

      import cats.free.Free
      import cats.instances.int._
      implicit val personToBytes = ToBytes.lift[Person](_.toString.getBytes)

      BaseIOSpec.withDir("CrudDslTest") { tmpDir =>
        val personDsl = CrudRequest.For[Int, Person](tmpDir)

        val program: Free[CrudRequest, (Boolean, Boolean)] = {
          for {
            sue <- personDsl.create(1, Person("Sue", 8))
            martha <- personDsl.create(2, Person("MArtha", 9))
            alex <- personDsl.create(3, Person("Alex", 10))
            marthaDeleted1 <- personDsl.delete(martha)
            marthaDeleted2 <- personDsl.delete(martha)
          } yield (marthaDeleted1, marthaDeleted2)
        }

        import CrudRequest._
        import cats.instances.all._

        val (explained, _) = program.foldMap(DocInterpreter).run
        explained shouldBe List(
          "create 1 w/ Person(Sue,8)",
          "create 2 w/ Person(MArtha,9)",
          "create 3 w/ Person(Alex,10)",
          "delete 2",
          "delete 2"
        )

        val performedInTask: Task[(Boolean, Boolean)] = program.foldMap(personDsl.TaskInterpreter)
        performedInTask.runAsync(Scheduler.global).futureValue shouldBe(true, false)

        val performedInIO: IO[(Boolean, Boolean)] = program.foldMap(personDsl.IOInterpreter)
        performedInIO.unsafeRunSync() shouldBe(true, false)
      }
    }
  }
}
