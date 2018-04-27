package lupin.example

trait FieldUpdate[ID] {
  type FieldType

  def id: ID

  def seqNo: SeqNo

  def index: Long

  def value: FieldType

  def operation: DataOperation.Value

  // we could supply this as an accessor to historic values or else make it available elsewhere
  //,fieldDao : FieldDao[ID, A]
}

object FieldUpdate {

  case class Instance[ID, A](override val id: ID,
                             override val seqNo: SeqNo,
                             override val index: Long,
                             override val value: A,
                             override val operation: DataOperation.Value)
      extends FieldUpdate[ID] {
    override type FieldType = A
  }

  def delete[ID, FieldType](id: ID, seqNo: SeqNo, index: Long, value: FieldType) = {
    apply(id, seqNo, index, value, DataOperation.Delete)
  }

  def update[ID, FieldType](id: ID, seqNo: SeqNo, index: Long, value: FieldType) = {
    apply(id, seqNo, index, value, DataOperation.Update)
  }

  def create[ID, FieldType](id: ID, seqNo: SeqNo, index: Long, value: FieldType) = {
    apply(id, seqNo, index, value, DataOperation.Create)
  }

  def apply[ID, FieldType](id: ID, seqNo: SeqNo, index: Long, value: FieldType, operation: DataOperation.Value) = {
    Instance(id, seqNo, index, value, operation)
  }

}
