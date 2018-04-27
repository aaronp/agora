package lupin

import agora.BaseIOSpec
import agora.io.Lazy
import scala.language.{implicitConversions, postfixOps}

/**
  * A base class for agora tests, exposing 'withDir' and some timeouts
  *
  * See http://www.scalatest.org/user_guide/defining_base_classes
  */
abstract class BaseFlowSpec extends BaseIOSpec {

  private val lazyCtxt = Lazy(newContextWithThreadPrefix(getClass.getSimpleName))
  implicit def ctxt    = lazyCtxt.value

  override def afterAll(): Unit = {
    super.afterAll()
    lazyCtxt.foreach(_.shutdown())
  }
}
