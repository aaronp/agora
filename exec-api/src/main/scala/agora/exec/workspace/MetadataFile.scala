package agora.exec.workspace

import java.nio.file.Path

import agora.io.implicits._
import com.typesafe.scalalogging.StrictLogging

import scala.util.Try

/**
  * When creating a file in a workspace, either by uploading one or having a running process output to a file,
  * We need some means to know when that file is 'ready' to be used.
  *
  * So, in terms of a [[WorkspaceClient]] waiting on files to be ready, we need more than just the file's existence to
  * know it can be used.
  *
  * To solve that, we use the convention of creating a metadata file alongside workspace files.
  * The metadata files are only created when their corresponding files are complete (where complete means the writing
  * process up upload has finished).
  *
  * As a double-check, we also store the referenced file's size in the metadata file. That could be left out entirely
  * (as we only need the presence of a metadata file to know the file is readable), or we could extend it to include
  * e.g. a hash of the target file.
  *
  */
object MetadataFile extends StrictLogging {
  private val MetadataSuffix: String = ".metadata"
  private val UploadSuffix: String   = ".partialupload"

  private val MetadataPattern = "\\.(.*)\\.metadata".r

  def isMetadataFile(file: String) = fileNameForMetadata(file).isDefined

  def fileNameForMetadata(file: String) = file match {
    case MetadataPattern(name) => Option(name)
    case _                     => None
  }

  /**
    * Creates a '.<file name>.metadata' file in the same directory as the 'uploadPath' file
    *
    * @param uploadPath the file for which a metadata file should be created
    * @return the metadata file if the supplied file has a parent directory
    */
  def createMetadataFileFor(uploadPath: Path, expectedSizeOpt: Option[Long] = None): Option[Path] = {
    if (uploadPath.exists()) {
      val actualSize = expectedSizeOpt.getOrElse(uploadPath.size)

      metadataFileForUpload(uploadPath) match {
        case Some(metdataFile) =>
          val mdFile = metdataFile.text = actualSize.toString
          Option(mdFile)
        case None =>
          logger.error(s"Couldn't resolve a metadata file for $uploadPath")
          None
      }

    } else {
      logger.error(s"Couldn't create a metadata file non-existent file $uploadPath")
      None
    }
  }

  /**
    * @param uploadFile the file to check if it's 'ready' (there exists a metadata file for it)
    * @return true if the given file has a metadata file and its size matches
    */
  def isReady(uploadFile: Path) = {
    metadataFileAndExpectedSizeForUpload(uploadFile).exists {
      case (metadataFile, mdSize) =>
        val actualSize = uploadFile.size
        if (actualSize == mdSize) {
          true
        } else {
          logger.warn(s"Uploaded file not ready: ${uploadFile.toAbsolutePath} ($actualSize bytes) != ${metadataFile.toAbsolutePath} ($mdSize bytes)")
          false
        }
    }
  }

  def metadataFileAndExpectedSizeForUpload(uploadFile: Path): Option[(Path, Int)] = {
    metadataFileForUpload(uploadFile).filter(_.isFile).map { metadataFile =>
      metadataFile -> Try(metadataFile.text.toInt).getOrElse(-1)
    }
  }

  /** @param uploadFile the target file to check
    * @return the metadata file for the given file
    */
  def metadataFileForUpload(uploadFile: Path): Option[Path] = {
    if (uploadFile.fileName.endsWith(MetadataSuffix)) {
      None
    } else {
      uploadFile.parent.map { parentDir =>
        parentDir.resolve(s".${uploadFile.fileName}$MetadataSuffix")
      }
    }
  }

  /** @param uploadFile the target file to check
    * @return the metadata file for the given file
    */
  def newPartialUploadFileForUpload(uploadFile: Path): Option[Path] = {
    uploadFile.parent.flatMap { parentDir =>
      val candidate = parentDir.resolve(s".${uploadFile.fileName}$UploadSuffix")
      if (candidate.exists()) {
        def numberedFile(n: Int) = parentDir.resolve(s".${uploadFile.fileName}$UploadSuffix.$n")
        Iterator.from(1, 1).take(50).map(numberedFile).find(!_.exists())
      } else {
        Option(candidate)
      }
    }
  }

  /** @param potentiallyNotExistentDir
    * @return files which have a metadata file which matches the expected byte count
    */
  def readyFiles(potentiallyNotExistentDir: Path, awaitFlushedOutput: Boolean): Array[Path] = {
    val all = potentiallyNotExistentDir.children
    if (awaitFlushedOutput) {
      all.filter(isReady)
    } else {
      all
    }

  }

  /**
    * utility for show files in a directory and their corresponding metadata files
    *
    * @param potentiallyNotExistentDir
    * @return a description string
    */
  def describeDir(potentiallyNotExistentDir: Path) = {
    val files = potentiallyNotExistentDir.children.map { child =>
      val mdFile = metadataFileForUpload(child).map(_.fileName).map(name => s" (${name})").getOrElse("")
      s"${child}${mdFile}"
    }
    files.mkString("\n")
  }
}
