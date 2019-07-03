def imageNames = [
    "apache-git-http-backend",
    "gerrit-init",
    "gerrit-master",
    "gerrit-slave",
    "git-gc",
    "mysql-replication-init"]
def baseImage
def gerritBaseImage
def images =[:]

def revision

def registryUrl = "https://index.docker.io/v1/"
def registryCredId = "dockerhub"

node("master") {
    checkout scm
    stage("Build base images") {
        baseImage = docker.build("base", "--no-cache ./container-images/base")
        gerritBaseImage = docker.build(
            "gerrit-base", "--no-cache ./container-images/gerrit-base")
    }
    stage("Build images") {
        revision = sh(returnStdout: true, script: "git describe --dirty").trim()
        imageNames.each {
            images[it] = docker.build(
                "k8sgerrit/${it}:${revision}",
                "--no-cache ./container-images/${it}")
        }
    }
    // The job to run this build will need a boolean parameter called `PUBLISH`.
    if (params.PUBLISH) {
        stage("Publish images") {
            docker.withRegistry(registryUrl, registryCredId) {
                images.each { name, image ->
                    image.push(revision)
                    image.push("latest")
                }
            }
        }
    }
    stage("Cleanup") {
        images.each { name, image ->
            sh("docker rmi -f ${image.id}")
        }
        sh("docker rmi -f ${gerritBaseImage.id}")
        sh("docker rmi -f ${baseImage.id}")
    }
}
