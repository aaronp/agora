package agora.api.exchange

import agora.api.json.{JExpression, JPath}
import io.circe.Json

trait HasWorkMatcher {

  type Me

  def addUpdateAction(action: OnMatchUpdateAction): Me

  /**
    * Append the value obtained by evaluating the given expression to matched worker subscriptions on match.
    *
    * the expression is evaluated against the current work-subscription json.
    *
    * @param expression the json expression which will be evaluated against the existing worker details whose result value will be appended at the specified append path
    * @param path       the path to which the value should be appended
    * @return an updated SubsmissionDetails w/ an added [[OnMatchUpdateAction]]
    */
  def appendOnMatch(expression: JExpression, path: JPath = JPath.root) = {
    addUpdateAction(OnMatchUpdateAction.appendAction(expression, path))
  }

  def removeOnMatch(path: JPath) = addUpdateAction(OnMatchUpdateAction.removeAction(path))

  /**
    * Append the given json value to the matched worker subscription's path on match
    *
    * @param value the json value to append to the work matcher
    * @param path  the path to which the value should be appended
    * @return an updated [[SubmissionDetails]] w/ an added [[OnMatchUpdateAction]]
    */
  def appendJsonOnMatch(value: Json, path: JPath = JPath.root) = {
    import JExpression.implicits._
    appendOnMatch(value.asExpression, path)
  }
}
