package lupin.example

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

import agora.io.ToBytes
import lupin.pub.impl.HasKey

case class DataUpdate[ID, T](id: ID,
                             // dao : Lookup[ID, T]
                             data: T,
                             dataOperation: DataOperation.Value,
                             seqNo: SeqNo) {

  /**
    * Provided we know how to get at a property A from the data T, and a way to index it with the data's key, we
    * can expose a FieldUpdate to push out
    *
    * @param accessor
    * @param fieldDao
    * @tparam A
    * @return
    */
  def fieldUpdate[A](implicit accessor: Accessor.Aux[T, A], fieldDao: IndexedOrder[(A, ID)]): (FieldUpdateAux[ID, A], fieldDao.Self) = {
    val field: A                      = accessor.get(data)
    val (index, newDao)               = fieldDao.upsert(field -> id)
    val update: FieldUpdateAux[ID, A] = FieldUpdate(id, seqNo, index, field, dataOperation)
    update -> newDao
  }
}

object DataUpdate {

  /**
    * The idForSeqAndValue assumes something may be writing down (and figuring out) if a piece of data T (w/ incrementing seq no)
    * is an update/delete/create, and what it's ID is
    *
    * @param seqCounter
    * @param idForSeqAndValue
    * @tparam ID
    * @tparam T
    */
  class Instance[ID, T](seqCounter: AtomicLong)(implicit idForSeqAndValue: Accessor.Aux[(Long, T), (DataOperation.Value, ID)]) {
    def apply(value: T): DataUpdate[ID, T] = {
      val seqNo    = seqCounter.incrementAndGet()
      val (op, id) = idForSeqAndValue.get(seqNo -> value)
      DataUpdate(id, value, op, seqNo)
    }
  }

  def apply[ID, T](input: T)(implicit hasKey: HasKey[ID]): DataUpdate[ID, T] = ???

  def apply[ID, T](input: T)(implicit idForSeqAndValue: Accessor.Aux[(Long, T), (DataOperation.Value, ID)]) = {
    new Instance(new AtomicLong(0L))
  }

  // TODO - create an in-memory and disk-based writer
  def writer[T: ToBytes](dir: Path): Accessor.Aux[(Long, T), (DataOperation.Value, Long)] = {
    ???
  }

}
