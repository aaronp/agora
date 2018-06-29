package crud.monix

import agora.BaseIOSpec
import crud.api.{CrudDsl, CrudRequest}
import crud.free.CrudInterpreter
import monix.execution.Scheduler.Implicits.global
import monix.reactive._
import org.scalatest.GivenWhenThen

class CrudDslMonixTest extends BaseIOSpec with GivenWhenThen {

  "CrudDslMonix" should {
    "should pipe events" in {

      BaseIOSpec.withDir("test") { dir =>

        Given("a publisher of CRUD requests")
        val commandStream: CrudDslMonix[String, Int] = CrudDslMonix[String, Int]()


        And("a pipe to execute commands which flow through them")
        // pipe those commands through something which can execute them (and apply back-pressure via Task)
        val runTask = CrudInterpreter.task[String, Int](dir)
        val results: Observable[Any] = commandStream.mapTask { request =>
          runTask.run(request)
        }

        When("we request some files to be created")
        commandStream.create("foo", 123)
        commandStream.create("bar", 456)

        Then("initially nothing should happen as nothing is pulling/requesting the results")
        dir.children.size shouldBe 0
        commandStream.onComplete()

        // don't create them twice - just return the saved results
        val createFiles = results.toListL.memoizeOnSuccess
        dir.children.size shouldBe 0

        When("The observable of results is materialised")
        val ids = createFiles.runSyncUnsafe(testTimeout)

        Then("the files should be created")
        ids shouldBe List("foo", "bar")
        dir.children.map(_.fileName) should contain only("foo", "bar")
      }
    }
    "publish out our crud events" in {

      val dsl = CrudDslMonix[String, Int]()

      val firstCreate = dsl.create("foo", 1)
      val secondCreate = dsl.create("second id", 2)
      val firstDelete = dsl.delete("third id")


      // will contain e.g.
      // [1]
      // [2,1]
      // [3,2,1]
      val listObs = dsl.take(3).scan(List[CrudRequest[_]]()) {
        case (list, next) => next :: list
      }
      val allFoldedElmsInReverseOrder: List[List[CrudRequest[_]]] = listObs.toListL.runSyncUnsafe(testTimeout)

      allFoldedElmsInReverseOrder.last.reverse shouldBe List(
        firstCreate,
        secondCreate,
        firstDelete
      )

      val secondDelete = dsl.delete("another id")

      dsl.take(4).scan(List[CrudRequest[_]]()) {
        case (list, next) => next :: list
      }.toListL.runSyncUnsafe(testTimeout).last.reverse shouldBe List(
        firstCreate,
        secondCreate,
        firstDelete,
        secondDelete
      )

    }
  }
  "pipe.multicast" should {
    "drop new" in {
      val pipe: Pipe[String, String] = Pipe.replay[String]
      val (observer, observable) = pipe.concurrent
      //      pipe.concurrent(DropNewAndSignal(3, n => s"dropped $n new".some))

      observable.doOnNext { next =>
        println(s"got: $next")
      }.subscribe()

      (0 to 5).foreach(i => observer.onNext(s"pushing $i"))
      observer.onComplete()

      val list: List[String] = observable.toListL.runSyncUnsafe(testTimeout)
      list shouldBe List("pushing 0", "pushing 1", "pushing 2", "pushing 3", "pushing 4", "pushing 5")

    }
  }
}
