package crud.api

import cats.{Monad, Show}
//final class Print[A](override val toString: String, val value: A)
//
//object Print {
//
//  def apply[A: Show](value: A) = new Print(Show[A].show(value), value)
//
//  def apply[A](toString: String, value: A) = new Print(toString, value)
//
//  implicit object PrintMonad extends Monad[Print] {
//    override def pure[A](x: A): Print[A] = new Print(x.toString, x)
//
//    override def flatMap[A, B](fa: Print[A])(f: A => Print[B]): Print[B] = {
//      val pb = f(fa.value)
//      Print(fa.toString + "\n\tand then \n " + pb.toString, pb.value)
//    }
//
//    override def tailRecM[A, B](a: A)(f: A => Print[Either[A, B]]): Print[B] = {
//      var fa: Print[Either[A, B]] = f(a)
//      var res : Print[B] = null
//      var str = ""
//      while (res == null) {
//
//        str = str + fa.toString
//        fa.value match {
//          case Left(a) =>
//            fa = f(a)
//          case Right(b) =>
//            res = Print(str, b)
//        }
//      }
//
//      res
//    }
//  }
//
//}
