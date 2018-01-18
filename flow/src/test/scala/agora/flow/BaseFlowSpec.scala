package agora.flow

import agora.BaseIOSpec

import scala.language.{implicitConversions, postfixOps}

/**
  * A base class for agora tests, exposing 'withDir' and some timeouts
  *
  * See http://www.scalatest.org/user_guide/defining_base_classes
  */
abstract class BaseFlowSpec extends BaseIOSpec
