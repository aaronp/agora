package example.automatedturk

case class TurkResources(systemUsagePercent: Int)

case class AskQuestion(question: String, uses: TurkResources)

case class Reply(reply: String)
