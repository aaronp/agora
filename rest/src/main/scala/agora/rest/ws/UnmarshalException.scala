package agora.rest.ws

import scala.reflect.ClassTag

class UnmarshalException[T: ClassTag](value: String)
    extends Exception(s"Unable to unmarshal '$value' as ${implicitly[ClassTag[T]].runtimeClass}")
