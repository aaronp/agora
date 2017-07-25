package agora.exec.run

import agora.domain.CloseableIterator
import agora.exec.model.{RunProcess, Upload}
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.test.TestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AppendedClues, Matchers, WordSpec}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try

class InMemoryLogTest extends WordSpec with Matchers with ScalaFutures with AppendedClues {

  import InMemoryLogTest._

  "CachingRunner.InMemory.cache" should {
    "Cache if the computation took longer than the computationThreshold and the output sise is less than the size threshold" in {
      TestUtils.withMaterializer { implicit mat =>
        import mat._

        val durationOverWhichToCache = 10.millis
        val underlying               = new TestRunner()
        val sizeThreshold            = 10
        val run                      = CachingRunner(underlying, durationOverWhichToCache, sizeThreshold)
        val key                      = CachingRunner.keyForProc(RunProcess(Nil))
        run.isCached(key) shouldBe false
        val fakeStartTime                   = System.currentTimeMillis() + durationOverWhichToCache.toMillis
        val timeAtWhichCalcExceedsThreshold = fakeStartTime + durationOverWhichToCache.toMillis

        // call the method under test. If our 'process' completes within the 'durationOverWhichToCache' then it should cache
        val result = run.cache(key, RunProcess(Nil), fakeStartTime, underlying.fixedResult.future)

        val expectedValues = (0 until sizeThreshold).map("line " + _).toList
        val iter = CloseableIterator(expectedValues.iterator) {
          while (System.currentTimeMillis() < timeAtWhichCalcExceedsThreshold) {
            Thread.sleep(10)
          }
        }
        underlying.complete(iter)

        result.futureValue.toList shouldBe expectedValues
        run.isCached(key) shouldBe true

        // trying to run again should now return the cached result
        val cachedResult = run.run(RunProcess(Nil)).futureValue
        cachedResult.toList shouldBe expectedValues
        withClue("we expected the second run to be a cache hit and therefore not invoke the underlying runner") {
          underlying.processes.size shouldBe 0
        }
      }
    }
  }

}

object InMemoryLogTest {

  class TestRunner(implicit ec: ExecutionContext = ExecutionContext.global) extends ProcessRunner {
    val fixedResult = Promise[Iterator[String]]()
    val processes   = ListBuffer[RunProcess]()

    def complete(lines: Iterator[String]) = {
      fixedResult.tryComplete(Try(lines))
    }

    override def run(proc: RunProcess): ProcessOutput = {
      processes += proc
      fixedResult.future
    }
  }

}
