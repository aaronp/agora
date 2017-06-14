package agora.rest

import miniraft.state.TestCluster.TestClusterNode

package object test {

  type TestNodeLogic = TestClusterNode[String]

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
