package agora.api.json

import io.circe.{Decoder, Json}
import org.scalatest.{FunSuite, Matchers, WordSpec}

class JPredicateTest extends WordSpec with Matchers {

  import io.circe.syntax._
  import io.circe.generic.auto._
  import JPredicate.implicits._
  import JPredicate._

  Seq[JPredicate](
    Eq("foo"),
    Gt("foo"),
    Gte("foo"),
    Lt("foo"),
    Lte("foo"),
    Not(Eq(123)),
    And(Eq(1), Eq(2)),
    Or(Eq(3), Eq(4)),
    JRegex("te.xt?")
  ).foreach { pred =>
    pred.toString should {
      s"be serializable from ${pred.asJson.noSpaces}" in {
        val Right(backAgain) = pred.asJson.as[JPredicate]
        backAgain shouldBe pred
      }
    }
  }
}
