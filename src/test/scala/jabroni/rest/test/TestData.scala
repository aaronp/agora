package jabroni.rest.test

import cucumber.api.DataTable

import scala.language.implicitConversions

object TestData {

  class RichTable(val table: DataTable) extends AnyVal {

    import scala.collection.JavaConverters._

    def toMap: List[Map[String, String]] = {
      table.asMaps(classOf[String], classOf[String]).asScala.toList.map(_.asScala.toMap)
    }

    def asScala: List[List[String]] = table.raw.asScala.toList.map(_.asScala.toList)

  }


}

trait TestData {

  import TestData._

  implicit def asRichTable(dataTable: DataTable): RichTable = new RichTable(dataTable)

}
