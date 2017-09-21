package agora.exec.events

import java.util.concurrent.atomic.AtomicInteger

import agora.BaseSpec
import agora.rest.HasMaterializer
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class HouseKeepingTest extends BaseSpec with HasMaterializer with Eventually {
  "Housekeeping.every(...)" should {
    "housekeep every (...)" in {
      val counter1 = new AtomicInteger(0)
      val counter2 = new AtomicInteger(0)
      val houseKeeping: HouseKeeping = HouseKeeping.every(100.millis)
      houseKeeping.registerHousekeepingEvent { () =>
        if (counter1.incrementAndGet() == 3) {
          houseKeeping.registerHousekeepingEvent { () =>
            counter2.incrementAndGet()
          }
        }
      }
      houseKeeping.isCancelled shouldBe false

      eventually {
        counter2.get should be > 1
      }
      counter1.get should be > counter2.get

      houseKeeping.cancel()

      houseKeeping.isCancelled shouldBe true
    }
  }

}
