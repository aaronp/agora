package crud

import cats.free.Free

package object api {

  type CrudFree[A] = Free[CrudFree, A]

}
