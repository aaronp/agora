package agora.api.exchange.dsl

import agora.api.exchange.{SubmitJob, Submitable}

/**
  * An instance of [[JobSyntax]]
  *
  * @param value      the input job (before being converted/marshalled/pickled to Json)
  * @param submitable evidence that we can convert 'T' values to a [[SubmitJob]]
  * @tparam T the input type which will be converted to the 'job' part of a [[SubmitJob]]
  */
class AsJob[T](override val value: T, override val submitable: Submitable[T]) extends JobSyntax[T]
