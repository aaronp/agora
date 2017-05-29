package jabroni.rest

package object test {


  implicit class RichList[T](list: List[T]) {
    def removeMatching[A](pf: PartialFunction[T, A]): (List[T], List[A]) = {
      list.foldLeft((List[T](), List[A]())) {
        case ((newList, collected), t) if pf.isDefinedAt(t) =>
          (newList, pf(t) :: collected)
        case ((newList, collected), t) =>
          (t :: newList, collected)
      }
    }
  }

}
