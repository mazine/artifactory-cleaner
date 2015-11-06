package jetbrains.artifactory.cleaner

import sun.misc.BASE64Encoder
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.ws.rs.client.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val login = System.getProperty("auth.login")
val password = System.getProperty("auth.password")
val authKey = BASE64Encoder().encode("$login:$password".toByteArray())

fun main(args: Array<String>) {

    val client = ClientBuilder.newClient()
    val server = client.target("http://repo.labs.intellij.net")

    val ringUIBuilds = loadRingUIBuilds(server)
    val ringUIBuildsToDelete = ringUIBuilds.filterRingUIBuildsToDelete()

    println("Deleting ${ringUIBuildsToDelete.size} builds of ${ringUIBuilds.size}...")
    ringUIBuildsToDelete.forEach {
        println("  ${it.uri} was downloaded ${it.downloadCount} time(s). Last on ${it.lastDownloaded.format()}")
        deleteRingUIBuild(client, it.uri)
    }

    reindexNPM(server)
}

fun loadRingUIBuilds(server: WebTarget): List<ArtifactStats> {
    val folderTarget = server.path("api/storage/npm-ring/ring-ui/-")
    val folderInfo = folderTarget.request().get(ArtifactoryPath::class.java)
    val artifacts = folderInfo.children.mapWithProgress {
        val childTarget = folderTarget.path(it.uri).queryParam("stats", "")
        childTarget.request().get(ArtifactStats::class.java)
    }
    return artifacts
}

fun List<ArtifactStats>.filterRingUIBuildsToDelete(): List<ArtifactStats> {
    val now = Calendar.getInstance()
    val oneWeek = now.plus(-7)
    val oneMonth = now.plus(-30)
    val halfYear = now.plus(-30 * 6)

    return filter {
        val age = when {
            it.lastDownloaded.before(halfYear) -> Age.MORE_THEN_HALF_YEAR
            it.lastDownloaded.before(oneMonth) -> Age.MORE_THEN_MONTH
            it.lastDownloaded.before(oneWeek) -> Age.MORE_THEN_WEEK
            else -> Age.ONE_WEEK
        }
        it.downloadCount <= age.threshold
    }
}

val tgzNamePattern = Pattern.compile("\\Qhttp://repo.labs.intellij.net/npm-ring/ring-ui/-/\\E(.+)\\Q.tgz\\E")
fun deleteRingUIBuild(client: Client, tgzURI: String) {
    deleteArtifact(client, tgzURI)
    val m = tgzNamePattern.matcher(tgzURI)
    if (m.matches()) {
        val jsonURI = "http://repo.labs.intellij.net/npm-ring/.npm/ring-ui/${m.group(1)}.json"
        deleteArtifact(client, jsonURI)
    } else {
        println("  Cannot find json for ${tgzURI}")
    }
}

private fun deleteArtifact(client: Client, uri: String) {
    val response = client.target(uri).request().auth().delete()
    if (response.statusInfo.family != Response.Status.Family.SUCCESSFUL) {
        println("  ${response.readEntity(String::class.java)}")
    } else {
        println("  Deleted ${uri}")
    }
}

fun reindexNPM(server: WebTarget) {
    val response = server.path("api/npm/npm-ring/reindex").request().auth().post(Entity.entity("", MediaType.TEXT_PLAIN_TYPE))
    println(response.statusInfo)
    println(response.readEntity(String::class.java))
}

fun Invocation.Builder.auth(): Invocation.Builder {
    return header("Authorization", "Basic $authKey")
}


enum class Age(val title: String, val threshold: Int) {
    ONE_WEEK("< 1 week", 0),
    MORE_THEN_WEEK("> 1 week", 2),
    MORE_THEN_MONTH("> 1 month", 3),
    MORE_THEN_HALF_YEAR("> 6 month", 9)
}

fun Calendar.plus(num: Int, unit: TimeUnit = TimeUnit.DAYS): Calendar {
    val copy = Calendar.getInstance(timeZone)
    copy.timeInMillis = this.timeInMillis + unit.toMillis(num.toLong())
    return copy
}

inline fun<S, T> List<S>.mapWithProgress(fn: (S) -> T): List<T> {
    var i = 0
    val result = this.map {
        val result = fn(it)
        print(".")
        if (++i % 100 == 0) {
            println("$i/$size")
        }
        result
    }
    println("$i/$size")
    return result
}