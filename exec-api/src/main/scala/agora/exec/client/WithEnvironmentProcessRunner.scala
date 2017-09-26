package agora.exec.client

import agora.exec.log.IterableLogger
import agora.exec.model.RunProcess

import scala.concurrent.Future

case class WithEnvironmentProcessRunner[T <: ProcessRunner](underlying: T, defaultEnv: Map[String, String])
    extends ProcessRunner {
  override def run(proc: RunProcess) = {
    underlying.run(proc.withEnv(defaultEnv ++ proc.env))
  }

  def execute(input: RunProcess, iterableLogger: IterableLogger)(implicit ev: T =:= LocalRunner): Future[Int] = {
    val proc = input.withEnv(defaultEnv ++ input.env)
    ev.apply(underlying).execute(proc, iterableLogger)
  }
}
