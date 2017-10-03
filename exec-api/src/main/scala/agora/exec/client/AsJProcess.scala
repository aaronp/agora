package agora.exec.client

import scala.util.control.NonFatal

object AsJProcess {
  def unapply(sprocess: scala.sys.process.Process): Option[java.lang.Process] = {
    try {
      val field = sprocess.getClass.getDeclaredField("p")
      field.setAccessible(true)
      val jProcess = field.get(sprocess).asInstanceOf[java.lang.Process]
      Option(jProcess)
    } catch {
      case NonFatal(_) => None
    }
  }

}
