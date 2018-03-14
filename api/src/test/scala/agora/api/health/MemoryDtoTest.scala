package agora.api.health

import agora.BaseApiSpec

class MemoryDtoTest extends BaseApiSpec {

  def emptyDto = MemoryDto(0, 0, 0, 0, 0)

  "MemoryDto.recalculatePercent" should {
    "return 100 if the max memory is <= 0" in {
      emptyDto.copy(max = 0).recalculatePercent.usedPercent shouldBe 100
    }
    "return the percentage of used memory to max memory" in {
      emptyDto.copy(max = 10, used = 5).recalculatePercent.usedPercent shouldBe 50
      emptyDto.copy(max = 100, used = 5).recalculatePercent.usedPercent shouldBe 5
      emptyDto.copy(max = 100, used = 200).recalculatePercent.usedPercent shouldBe 200
    }
  }

}
