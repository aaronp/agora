package agora

/**
  * 'exec' is an example of how agora can be used to run generic commands across workers.
  *
  * We set up an 'RunProcess' requests, which run commands on worker node.
  * The worker node can change its subscription to filter/configure what gets run based on the
  * jobs submitted.
  */
package object exec {

  /**
    * the path to the 'workspaces' json element which contains all the
    * workspaces held by a worker which could be matched on by a
    * [[agora.exec.client.RemoteRunner]].
    *
    * The [[agora.exec.workspace.UpdatingWorkspaceClient]] is responsible
    * for updating work subscriptions
    */
  val WorkspacesKey = "workspaces"

}
