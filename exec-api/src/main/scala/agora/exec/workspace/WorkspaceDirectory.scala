package agora.exec.workspace

import java.nio.file.Path

import agora.exec.model.Upload
import agora.exec.workspace.DependencyCheck._
import agora.exec.workspace.MetadataFile.metadataFileForUpload
import agora.io.implicits._
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Represents a particular workspace directory
  *
  * @param workspaceDirectory
  */
case class WorkspaceDirectory(workspaceDirectory: Path) extends StrictLogging {

  /** @return the non-metadata files in the directory
    */
  def eligibleFiles(awaitFlushedOutput: Boolean): Array[String] = MetadataFile.readyFiles(workspaceDirectory, awaitFlushedOutput).map(_.fileName)

  def nonMetadataWorkspaceFiles: Array[String] = {
    workspaceDirectory.children.collect {
      case uploadFile if metadataFileForUpload(uploadFile).isDefined => uploadFile.fileName
    }
  }

  override def toString = show

  def show = MetadataFile.describeDir(workspaceDirectory)

  /**
    * @param dependencies the names of files for which to determine their state
    * @return the states for each dependency
    */
  def dependencyStates(dependencies: Set[String]): DependencyCheck = {
    val filenamesInDir = workspaceDirectory.children.map(_.fileName).toSet
    val stateByDependency = dependencies.map { dependencyFileName =>
      val state: DependencyState = if (!filenamesInDir.contains(dependencyFileName)) {
        DoesNotExist
      } else {
        val fileDependency = workspaceDirectory.resolve(dependencyFileName)
        MetadataFile.metadataFileAndExpectedSizeForUpload(fileDependency) match {
          case Some((mdFile, expectedSize)) =>
            val actualSize = fileDependency.size
            if (expectedSize == actualSize) {
              Ready
            } else {
              NotOfExpectedSize(actualSize, expectedSize)
            }
          case None => NoMetadataFile
        }
      }

      dependencyFileName -> state
    }

    DependencyCheck(stateByDependency.toMap)
  }

  /**
    * Upload the file to the directory
    *
    * @param msg
    */
  def onUpload(msg: UploadFile)(callbackWtf: Try[(Long, Path)] => Unit)(implicit mat: Materializer): Future[(Long, Path)] = {
    import mat._
    val UploadFile(id, file, src, _) = msg

    logger.info(s"Uploading ${file} to $id")
    val tri: Try[UploadDao.FileUploadDao] = Try(UploadDao(workspaceDirectory.mkDirs()))
    //    val savedFileFuture: Future[(Long, Path)] = Future.fromTry(tri).fast.flatMap { dao =>
    //      dao.writeDownUpload(Upload(file, src))
    //    }
    val savedFileFuture: Future[(Long, Path)] = tri match {
      case Success(dao) => dao.writeDownUpload(Upload(file, src))
      case Failure(err) => Future.failed(err)
    }
    savedFileFuture.onComplete {
      case tri @ Success((size, uploadPath)) =>
        val mdFile = MetadataFile.createMetadataFileFor(uploadPath)
        require(uploadPath.size == size, s"${uploadPath.fileName}.size didn't match write count: ${uploadPath.size} bytes != ${size} bytes written")
        logger.debug(s"-UPLOAD- to ${workspaceDirectory.toAbsolutePath}/$file completed w/ ${size} bytes to $uploadPath, metadata file : $mdFile")

        callbackWtf(tri)
      case tri @ Failure(error) =>
        logger.debug(s"-UPLOAD- errored to ${workspaceDirectory}/$file, and $file isn't ready in ${show}. Error: $error")
        callbackWtf(tri)

    }

    savedFileFuture
  }
}
