package jabroni

import com.typesafe.scalalogging.StrictLogging

object LogCheck extends App with StrictLogging {

  logger.info("where is this coming from?")
  logger.debug("debuging")
  logger.warn("warning")

}
