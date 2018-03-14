package agora.json

import io.circe.Decoder.Result
import io.circe._

import scala.reflect.ClassTag

trait JsonAppendable {

  def aboutMe: Json

  protected def mergeJson[T: Encoder](data: T, name: String = null) = {
    val namespace  = Option(name).getOrElse(data.getClass.getSimpleName)
    val json: Json = implicitly[Encoder[T]].apply(data)
    val that       = Json.obj(namespace -> json)
    aboutMe.deepMerge(that)
  }

  def valueOf[T: Decoder: ClassTag](name: String = null): Result[T] = {
    val c1ass = implicitly[ClassTag[T]].runtimeClass
    val dec   = implicitly[Decoder[T]]

    val fieldName = namespace(c1ass, name)
    aboutMe.hcursor.downField(fieldName) match {
      case h: HCursor => dec(h)
      case a          => Left(DecodingFailure(s"Expected '$fieldName", a.history))
    }
  }

  protected def namespace[T](c1ass: Class[T], name: String) =
    Option(name).getOrElse(c1ass.getSimpleName)
}
