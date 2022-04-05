node("master") {
    stage("Checkout source") {
        checkout scm
    }
    stage("Build") {
        sh(script: "./build")
    }
    stage("Publish") {
        withDockerRegistry(
                credentialsId: 'dockerhub',
                url: 'https://docker.io') {
            sh(script: '''
                ORG=k8sgerrit
                test -n $GERRIT_CHANGE_NUMBER && ORG=k8sgerrit-test
                ./publish --tag $(./get_version.sh)
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
