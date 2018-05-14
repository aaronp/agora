package lupin

import lupin.data.Accessor
import org.reactivestreams.Publisher

package object example {

  /**
    * 1) Raw input of some type T w/ some back-pressure
    */
  type RawFeed[T] = Publisher[T]

  /**
    * 2) IDFeed is a CRUD processor for T which has an ID and seq number added to the operation
    */
  type SeqNo = Long

  // subscribe goes from T => DataUpdate[ID, T] after the data has been processed (inserted, updated or deleted)
  type IDFeed[ID, T] = Publisher[DataUpdate[ID, T]]

  /**
    * 3) FieldFeed is an indexed field feed for each of the fields which are indexable for T
    *
    * After indexing, we can provide which index a particular value 'A' is at for a processed 'ID' and SeqNo
    */
  // can look up the value A of a field based on id 'K'
  type FieldDao[ID, A]       = Lookup[ID, A]
  type FieldUpdateAux[ID, A] = FieldUpdate[ID] { type FieldType = A }

  type FieldFeed[ID] = Publisher[(IndexSelection, FieldUpdate[ID])]

  /**
    * 4) View Feed provides the interested view (index range) for a particular field, as well as all the columns we're interested in
    *
    * we could/might want to split out the indices and the column selection into separate feeds, just to not have to
    * always send out e.g. all the columns all the time.
    *
    * TODO: plug this into json diff feed to make that kind of consideration moot, as we can re-hydrate from deltas
    */
  type ViewFeed = Publisher[ViewPort]

  case class FieldAndRange(field: String, selection: IndexSelection)

  /**
    * 5) some data structure to make available some field feeds.
    *
    * In practice we could create these by listening to a metadata feed e.g. of types w/ their Json properties
    */
  type VS[ID] = ViewState[ID]

  /**
    * 6) Given a ViewState which knows which field feeds to pay attention to, we can now expose a CellFeed
    * for cell updates within a ViewPort.
    *
    * These should be conflated, so we represent updates as a Map of all the updates for each position and
    * the latest sequence number published/received
    */
  type CellFeed[ID, U <: FieldUpdate[ID]] = Publisher[CellUpdate[ID, U]]

}
