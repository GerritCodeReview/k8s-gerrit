def imageNames = [
    "apache-git-http-backend",
    "gerrit-init",
    "gerrit-master",
    "gerrit-slave",
    "git-gc"]
def baseImage
def gerritBaseImage
def images =[:]

node("master") {
    checkout scm
    stage("Build base images") {
        baseImage = docker.build("base", "./container-images/base")
        gerritBaseImage = docker.build(
            "gerrit-base", "./container-images/gerrit-base")
    }
    stage("Build images") {
        imageNames.each {
            images[it] = docker.build(it, "./container-images/${it}")
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
