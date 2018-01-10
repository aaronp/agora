package agora.api.data

trait LowPriorityDataImplicits {
  implicit def asJsonIsEmpty     = agora.api.json.JsonIsEmpty
  implicit def asJsonDiffIsEmpty = agora.api.json.JsonDiffIsEmpty
}
