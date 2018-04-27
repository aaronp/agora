package lupin.example

class RenderableFieldUpdate[ID, A: AsText](update: FieldUpdateAux[ID, A]) extends FieldUpdate[ID] {
  override type FieldType = A

  def render: String = implicitly[AsText[A]].asText(value)

  override def id: ID = update.id

  override def seqNo: SeqNo = update.seqNo

  override def index: SeqNo = update.index

  override def value: A = update.value

  override def operation: DataOperation.Value = update.operation
}

object RenderableFieldUpdate {
  def delete[ID, FieldType: AsText](id: ID, seqNo: SeqNo, index: Long, value: FieldType): RenderableFieldUpdate[ID, FieldType] = {
    apply(id, seqNo, index, value, DataOperation.Delete)
  }

  def update[ID, FieldType: AsText](id: ID, seqNo: SeqNo, index: Long, value: FieldType): RenderableFieldUpdate[ID, FieldType] = {
    apply(id, seqNo, index, value, DataOperation.Update)
  }

  def create[ID, FieldType: AsText](id: ID, seqNo: SeqNo, index: Long, value: FieldType): RenderableFieldUpdate[ID, FieldType] = {
    apply(id, seqNo, index, value, DataOperation.Create)
  }

  def apply[ID, FieldType: AsText](id: ID,
                                   seqNo: SeqNo,
                                   index: Long,
                                   value: FieldType,
                                   operation: DataOperation.Value): RenderableFieldUpdate[ID, FieldType] = {
    val update: FieldUpdate.Instance[ID, FieldType] = FieldUpdate[ID, FieldType](id, seqNo, index, value, operation)
    val aux: FieldUpdateAux[ID, FieldType]          = update
    new RenderableFieldUpdate[ID, FieldType](aux)
  }

}
