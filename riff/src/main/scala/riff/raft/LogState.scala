package riff.raft
import java.nio.file.Path

final case class LogState(commitIndex : Int, latestTerm : Int, latestIndex : Int)

object LogState {

  import agora.io.implicits._

  /**
    * Represents a file in the form {{{<last committed>:<latest term>:<latest index>}}}
    */
  final case class StateFile(file: Path) {
    def currentState(): LogState = StateFile.parse(file.text)
    def update(state : LogState): Path = file.text = {
      import state._
      s"$commitIndex:$latestTerm:$latestIndex"
    }
  }
  object StateFile {
    val CommitTermAndIndex = "([0-9]+):([0-9]+):([0-9]+)".r
    def parse(contents: String): LogState = {
      contents match {
        case CommitTermAndIndex(c, t, i) => LogState(c.toInt, t.toInt, i.toInt)
        case _                           => LogState(0, 0, 0)
      }
    }
  }
}