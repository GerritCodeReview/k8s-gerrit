node("master") {
    stage("Checkout source") {
        checkout scm
    }
    stage("Build") {
        sh(script: "./build")
    }
    stage("Publish") {
        withDockerRegistry(
                credentialsId: 'artifactory',
                url: 'https://gerrit.docker.repositories.sap.ondemand.com') {
            sh(script: '''
                ./publish --tag $(./get_version.sh) --registry gerrit.docker.repositories.sap.ondemand.com
            ''')
        }
    }
    stage("Clean up") {
        sh(script: '''
            docker rmi -f $(docker images --format '{{.Repository}}:{{.Tag}}'| grep $(git describe --always --dirty))
            docker rmi -f $(docker images --filter 'dangling=true' -q --no-trunc) 2>/dev/null || continue
        ''')
    }
}
