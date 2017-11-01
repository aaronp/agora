package agora.exec.model

/**
  * Represents a configuration for what to do w/ a process output
  *
  * If 'canCache' is true, then the output of the standard out and err will be returned if already found.
  *
  * This is intended to work as follows:
  *
  * # In the case where no '<workspace>/.cache/<MD5 of the command>' entry exists
  * If there is no cached value, then the command is run as normal. A '.cache' directory will be created in the job's
  * workspace in which we store cache metadata to contain:
  *
  * {{{
  *   <workspace>/.cache/<MD5 of the command>/.stdOutFileName # contains the name of the stdout file name
  *   <workspace>/.cache/<MD5 of the command>/.stdErrFileName # contains the name of the stderr file name
  *   <workspace>/.cache/<MD5 of the command>/.exitCode # set to the exit code
  *   <workspace>/.cache/<MD5 of the command>/.job # a serialized version of the job
  * }}}
  *
  * If the 'stdOutFileName' or 'stdErrFileName' are left empty when 'canCache' is set, then a unique filename will
  * be used.
  *
  * This is intended to mitigate the scenario where the same command is run concurrently, both set to allow caching.
  *
  * This way both jobs will run, writing to different files, and just happily clobber the contents of the cache directory.
  *
  * another approach would be to detect unfinished jobs (e.g. no .exitCode exists) and tail the existing job.
  *
  * # In the case where a '<workspace>/.cache/<MD5 of the command>' entry exists
  *
  * If that directory exists and an .exitCode file exists, then:
  *
  * ## When streaming is set
  * if the exit code is listed in the a 'successExitCodes', then the contents .stdOutFileName is returned.
  * Otherwise the contents of the .stdErrFileName are returned.
  *
  * ## When streaming is not set
  * Then the [[FileResult]] is simply returned w/ the cached values (output file names, exit code, etc)
  *
  * @param streaming                   settings for streaming back the output of the job
  * @param stdOutFileName              an option filename to save the output file to. If 'canCache' is set and the 'stdOutFileName'
  *                                    isn't, then a unique output file name is chosen.
  * @param stdErrFileName              an option filename to save the output file to. If 'canCache' is set and the 'stdErrFileName'
  *                                    isn't, then a unique output file name is chosen.
  * @param logOutput                   if set, the value will be interepretted as a log level (e.g. trace, debug, info, etc)
  * @param canCache                    toggle ability to cache as detailed above
  * @param useCachedValueWhenAvailable use the cached value when available
  */
case class OutputSettings(streaming: Option[StreamingSettings] = Option(StreamingSettings()),
                          stdOutFileName: Option[String] = None,
                          stdErrFileName: Option[String] = None,
                          logOutput: Option[String] = None,
                          canCache: Boolean = false,
                          useCachedValueWhenAvailable: Boolean = true) {
  def withSettings(settings: StreamingSettings): OutputSettings = copy(streaming = Option(settings))

}
