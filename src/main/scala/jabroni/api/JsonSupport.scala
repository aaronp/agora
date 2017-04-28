package jabroni.api

import io.circe.{Decoder, Encoder, Json}

trait BaseSupport[T] extends JsonSupport[T] with Encoder[T] with Decoder[T] {
  override def encoder : Encoder[T] = this
  override def decoder : Decoder[T] = this
}

trait ResponseSupport[T] extends BaseSupport[T]

trait JsonSupport[T] {
  def encoder : Encoder[T]
  def decoder : Decoder[T]
}
trait RequestSupport[T] extends BaseSupport[T]