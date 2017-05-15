package jabroni

/**
  * This serves as a kind of example of how this whole jabroni stuff can be used.
  *
  * We set up an 'RunProcess' requests, which just runs things on a worker node.
  * The worker node can change its subscription to filter/configure what gets run.
  *
  * The worker also exposes multiple handlers - one for executing a job which returns the
  * jobs output, and another for being able to cancel a job based on a job id.
  *
  */
package object exec {

}
