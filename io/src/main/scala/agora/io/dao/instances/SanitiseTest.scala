package agora.io.dao.instances

import agora.io.MD5

import scala.compat.Platform

object SanitiseTest extends App {

  def time(f: Int => Unit) = {
    f(-1)
    val start = Platform.currentTime
    (0 to 1000).foreach(f)
    Platform.currentTime - start
  }

  def sanitiseValue(str: String) = {
    str.filter(_.isLetterOrDigit).take(30) match {
      case ""    => ".empty"
      case other => other
    }
  }

  def run = {

    val value = "ABC 123 abc 123 hello there world!"
    val st = time { i =>
      sanitiseValue(value + i)
    }
    println(st)
    val md5t = time { i =>
      MD5(value + i)
    }
    println(md5t)
    val hashCod3 = time { i =>
      (value + i).hashCode
    }
    println(hashCod3)
  }

  run
}
