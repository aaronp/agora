package agora.exec

import com.typesafe.scalalogging.StrictLogging

/**
  * Just like [[ExecMain]], but will run on the test classpath
  */
object ExecMainDebug extends StrictLogging {
  def main(args: Array[String]): Unit = {
    val conf = ExecConfig(args)
    logger.trace(conf.describe)
    conf.show() match {
      case Some(value) => println(value)
      case None        => conf.start()
    }
  }
}
