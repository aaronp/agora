package agora.api.data

trait LowPriorityDataImplicits {
  implicit def asJsonIsEmpty     = agora.json.JsonIsEmpty
  implicit def asJsonDiffIsEmpty = agora.json.JsonDiffIsEmpty
}
