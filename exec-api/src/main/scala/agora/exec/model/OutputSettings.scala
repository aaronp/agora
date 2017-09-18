package agora.exec.model

/**
  * Represents a configuration for what to do w/ a process output
  *
  * @param stream
  * @param stdOutFileName
  * @param stdErrFileName
  * @param canCache if true, this process's output can be cached/reused
  */
case class OutputSettings(stream: Option[StreamingSettings] = Option(StreamingSettings()),
                          stdOutFileName: Option[String] = None,
                          stdErrFileName: Option[String] = None,
                          errorLimit: Option[Int] = None,
                          canCache: Boolean = false) {
  def withSettings(settings: StreamingSettings): OutputSettings = copy(stream = Option(settings))
}
