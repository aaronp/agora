package agora.domain

import java.security.MessageDigest

object MD5 {

  def apply(str: String) = {
    val digest = MessageDigest.getInstance("MD5")
    Hex(digest.digest(str.getBytes))
  }

}
