package agora.io

trait ByteFormat[T] extends ToBytes[T] with FromBytes[T]
