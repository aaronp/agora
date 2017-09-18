package agora.io.dao.instances

import java.nio.file.Path

import agora.io.implicits._
import agora.io.dao.{FromBytes, IdDao, Persist}

class FileIdDao[T: Persist: FromBytes](dir: Path) extends IdDao[String, T] {
  override type Result       = Path
  override type RemoveResult = Path

  override def save(id: String, value: T) = {
    val file = dir.resolve(id)
    implicitly[Persist[T]].write(file, value)
    file
  }

  override def remove(id: String) = dir.resolve(id).delete()

  override def get(id: String) = {
    val file = dir.resolve(id)
    if (file.exists) {
      implicitly[FromBytes[T]].read(file.bytes).toOption
    } else {
      None
    }
  }
}
