package crud

import cats.Show
import cats.data.Writer

package object api {

  type Print[A] = Writer[List[String], A]

  def Print[A: Show](value: A): Print[A] = Writer(Show[A].show(value) :: Nil, value)

  def Print[A](toString: String, value: A): Print[A] = Writer[List[String], A](toString :: Nil, value)
}
