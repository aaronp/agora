package agora.rest.stream

trait Labels {
  def labelFor(path: List[String]): String

  def pathForLabel(label: String): Option[List[String]]
}

object Labels {
  def apply(initialMap: Map[List[String], String] = Map()) = new Buffer(initialMap)

  class Buffer(initialMap: Map[List[String], String] = Map()) extends Labels {
    private var labelsByPath = initialMap
    def setLabel(path: List[String], label: String) = {
      labelsByPath = labelsByPath.updated(path, label)
    }

    def default(path: List[String]) = path.map(_.capitalize).mkString(" ")

    override def labelFor(path: List[String]): String = labelsByPath.getOrElse(path, default(path))

    override def pathForLabel(label: String): Option[List[String]] = {
      val found = labelsByPath.collectFirst {
        case (key, value) if value == label => key
      }
      found
    }
  }
}
