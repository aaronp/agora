package agora.exec.session

import java.nio.file.Path

import agora.exec.ExecConfig
import agora.rest.worker.WorkContext
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future

/**
  * A worker which, upon receiving an upload, will open a new subscription containing the uploaded files.
  *
  * A session is simply a workspace where files can be uploaded.
  *
  * The workflow is:
  *
  * The session handler requests N work items from the exchange (N being the number of upload 'sessions' it can
  * concurrently handle).
  *
  * When a new session is created, the 'Make Session Handler' creates a new subscription for 'session: XYX'.
  *
  * That new subscription will take one work item from the exchange, so the total work load requested remains
  * the same.
  *
  * For example, the session handler initially asks for 5 work items from the exchange, it will decrement that
  * pending count to 4 when the first session is created. That session handler will ask for 1 work item with
  * the 'aboutMe' work details of 'session : XYZ'.
  *
  * When items are uploaded to that session, it will update its subscription to detail that it contains those files.
  *
  * That way the client can upload files and work which requires those files out of order, and things will just work
  * as long as the 'submitJob' includes criteria to include the files it needs.
  *
  *
  * Consider this workflow:
  *
  * {{{
  *
  *                                                                                  Make Session       Session 'a'
  * Client                             Exchange                                        Handler           Handler
  * +                                     +                                               +                 +
  * |                                     |                                               |                 |
  * |                                     |                                               |                 |
  * |                                     |         subscribe(newSession)                 |                 |
  * |                                     <---------------------------------------------+ |                 |
  * |                                     |                                               |                 |
  * |                                     +---------------- ack id ---------------------> |                 |
  * |                                     |                                               |                 |
  * |                                     |                                               |                 |
  * |                                     |               take (5)                        |                 |
  * |                                     <---------------------------------------------+ |                 |
  * |                                     |                                               |                 |
  * |                                     |                                               |                 |
  * |          submit(newSession(a))      |                                               |   +---+         |
  * +------------------------------------>+               session(a)                      |   |   |         |
  * |                                     +---------------------------------------------->+   | A |         |
  * |                                     |                                                   |   |         |
  * |                                     |                                                   +---+         |
  * |                                     |              subscribe(session:a)                               |
  * |                                     <-----------------------------------------------------------------+
  * |                                     |                                                                 |
  * |                                     +--------------------------ack 'a'------------------------------->+
  * |    submit(upload(a,file1,file2))    |                                                                 |
  * +------------------------------------>+                          take(1)                                |
  * |                                     <-----------------------------------------------------------------+
  * |    submit(exec(cmd, needs:file1))   |                                                                 |
  * +------------------------------------>+                         upload(file1, file2)                    |
  * |  +---+                              +---------------------------------------------------------------->+
  * |  |   |                              |                                                                 |
  * |  | B |                              |      updateSubscription(files:[file1, file2], session: a)       |
  * |  |   |                              <-----------------------------------------------------------------+
  * |  +---+                              |                                                                 |
  * |                              +---+  |                     exec(cmd)                                   |
  * |                              |   |  +---------------------------------------------------------------->+
  * |                              | C |                                                                    |
  * |                              |   |                                                                    +
  * +                              +---+
  * }}}
  *
  * at 'A' the session is created, but no more work is requested from the exchange (as we've used up one of our sessions)
  *
  * a new subscription for the session is created.
  * At some point an upload is made for that session. Note, that can happen at any time ... even before the session was
  * first created!
  *
  * Upon each upload, the work subscription is updated to include those details.
  *
  * at 'B' a command is executed which requires 'file1'. That submission includes 'file1' in its match criteria,
  * therefore only being able to run once our subscription is updated to include file1.
  *
  *
  * at 'C' the job is executed, as the match can take place.
  *
  * We just now need to ensure sessions are closed when they are no longer needed so we don't stagnate
  * the 'make session' handler.
  */
class NewSessionHandler(conf: ExecConfig) extends FailFastCirceSupport {

  def chunkSize: Int = conf.chunkSize

  def workDirForSession(id: SessionId): Path = {
    val workDirOpt = conf.workingDirectory.dir(id)
    workDirOpt.getOrElse(sys.error("Invalid config -- 'workingDirectory.dir' not set"))
  }

  /**
    * We can take another work item for the session subscription once one session is closed.
    * We can also remove the session dir
    *
    * @param ctxt
    */
  def onCloseSession(ctxt: WorkContext[CloseSession]) = {
    val sessionId: SessionId = ctxt.request.close
    import agora.domain.io.implicits._
    val dir: Path = workDirForSession(sessionId)
    if (dir.exists) {
      dir.delete()
      require(!dir.exists, s"We couldn't actually delete $dir to close session $sessionId")

      // take 2 more work items ... one for the request we've just handled and 1 for the one we've taken up since
      // opening the session
      ctxt.completeWith(ctxt.asResponse(true), 2)
    } else {
      ctxt.complete(false)
    }
  }

  /**
    * Create endpoints for our session ... one for 'uploads' and one for 'exec' requests.
    *
    * This function will add two paths. They paths need to be unique, so they'll incorporate the session Id.
    * Afterwards there will be a 'name-*' and 'upload-*' path, both with 'session.id=<id>' in their data.
    *
    * @param ctxt
    * @return
    */
  def onOpenSession(ctxt: WorkContext[OpenSession]) = {
    val sessionId: SessionId = ctxt.request.open

    // create a 'session handler' to both accept uploads and execute commands
    val handler: SessionHandler = {
      val workDir = workDirForSession(sessionId)
      val runner  = conf.newRunner(ctxt.matchDetails, sessionId).withWorkDir(Option(workDir))
      new SessionHandler(runner, workDir.resolve(sessionId), chunkSize, conf.uploadTimeout)
    }
    val paths              = ctxt.routes.workerPaths
    val uploadSubscription = UseSession.prepareUploadSubscription(ctxt.subscription, sessionId)
    val execSubscription   = UseSession.prepareExecSubscription(ctxt.subscription, sessionId)

    /** @todo - FIXME ...  this check isn't thread-safe! We need a clean way to atomically add unique handler paths
      */
    if (paths.contains(uploadSubscription.details.path) || paths.contains(execSubscription.details.path)) {
      ctxt.completeWith[String](Future.failed(new Exception(s"Session '$sessionId' resolves to an already-open session for ${uploadSubscription.details.path}")))
    } else {
      // add the upload route
      ctxt.routes.withSubscription(uploadSubscription).withInitialRequest(1).addHandler(handler.onUpload)

      // ... and the exec route
      ctxt.routes.withSubscription(execSubscription).withInitialRequest(1).addHandler(handler.onWork)

      ctxt.routes.usingSubscription(CloseSession.prepareSubscription(sessionId, _)).addHandler(onCloseSession)

      // just reply -- don't request any more work
      ctxt.completeReplyOnly(sessionId)
    }
  }

}
