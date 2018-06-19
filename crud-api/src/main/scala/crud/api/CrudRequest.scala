package crud.api

import scala.language.{higherKinds, implicitConversions, postfixOps}

// watching this:
// https://www.youtube.com/watch?v=cxMo1RMsD0M
sealed trait CrudRequest[A]

case class Create[ID, T](id: ID, value: T) extends CrudRequest[ID]

case class Delete[ID](id: ID) extends CrudRequest[Boolean]
