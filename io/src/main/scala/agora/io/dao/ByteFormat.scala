package agora.io.dao

trait ByteFormat[T] extends ToBytes[T] with FromBytes[T]
