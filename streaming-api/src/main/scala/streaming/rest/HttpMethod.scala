package streaming.rest

sealed trait HttpMethod

object HttpMethod {
  def GET = streaming.rest.GET
  def DELETE = streaming.rest.DELETE

  def POST = streaming.rest.POST

  def PUT = streaming.rest.PUT

  def HEAD = streaming.rest.HEAD
}

case object DELETE extends HttpMethod
case object GET extends HttpMethod

case object POST extends HttpMethod

case object PUT extends HttpMethod

case object HEAD extends HttpMethod
