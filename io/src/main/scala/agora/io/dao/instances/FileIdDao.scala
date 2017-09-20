package agora.io.dao.instances

import java.nio.file.Path

import agora.io.dao.{FromBytes, IdDao, Persist}
import agora.io.implicits._

class FileIdDao[T: Persist: FromBytes](dir: Path) extends IdDao[String, T] {
  override type Result       = Path
  override type RemoveResult = Path

  private val fromBytes = implicitly[FromBytes[T]]
  private val persist   = implicitly[Persist[T]]

  override def save(id: String, value: T) = {
    val file = dir.resolve(id)
    persist.write(file, value)
    file
  }

  override def remove(id: String) = dir.resolve(id).delete()

  override def get(id: String) = {
    getFile(id).flatMap { file =>
      fromBytes.read(file.bytes).toOption
    }
  }

  def getFile(id: String) = {
    val file = dir.resolve(id)
    if (file.exists) {
      Option(file)
    } else {
      None
    }
  }
}
