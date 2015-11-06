package jetbrains.artifactory.cleaner

import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*

class ArtifactoryPath {
    var uri: String = ""
    var repo: String = ""
    var path: String = ""
    var created: Calendar = Calendar.getInstance()
    var createdBy: String = ""
    var lastModified: Calendar = Calendar.getInstance()
    var modifiedBy: String = ""
    var lastUpdated: Calendar = Calendar.getInstance()
    var children: List<ArtifactoryPathChild> = listOf()

    var downloadUri: String? = null
    var mimeType: String? = null
    var size: Int? = null
    var checksums: Checksums? = null
    var originalChecksums: Checksums? = null

    fun dump(out: PrintStream) {
        out.println("uri: ${uri}")
        out.println("repo: ${repo}")
        out.println("path: ${path}")
        out.println("created: at ${created.format()} by ${createdBy}")
        out.println("last modified: at ${lastModified.format()} by ${modifiedBy}")
        out.println("last updated: at ${lastUpdated.format()}")
        downloadUri?.let { out.println("downloadUri: $it") }
        mimeType?.let { out.println("mimeType: $it") }
        size?.let { out.println("size: $it") }
        children.forEach {
            it.dump(out)
        }
    }

}

fun Calendar.format(): String {
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
    return simpleDateFormat.format(time)
}

class ArtifactoryPathChild {
    var uri: String = ""
    var folder: Boolean = false

    fun dump(out: PrintStream) {
        out.println("  ${if (folder) "folder" else "file"}: ${uri}")
    }
}

class Checksums {
    var sha1: String = ""
    var md5: String = ""
}

class ArtifactStats {
    var uri = ""
    var lastDownloaded = Calendar.getInstance()
    var downloadCount = 0
    var lastDownloadedBy = ""
    var remoteDownloadCount = 0
    var remoteLastDownloaded = 0
}