package agora

import com.typesafe.config.{Config, ConfigFactory}

package object config {

  def configForArgs(args: Array[String], fallback: Config = ConfigFactory.empty): Config = {
    import agora.config.RichConfig.implicits._
    fallback.withUserArgs(args)
  }

}
