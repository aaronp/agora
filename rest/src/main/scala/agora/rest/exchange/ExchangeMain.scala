package agora.rest.exchange

/** ExchangeMain
  * Main entry point for the rest service.
  */
object ExchangeMain {
  def main(args: Array[String]): Unit = {
    ExchangeConfig(args).start()
  }
}
