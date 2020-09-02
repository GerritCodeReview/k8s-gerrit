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
                . ./get_version.sh
                ./publish --tag $GIT_REV_TAG --update-latest
            ''')
        }
    }
    stage("Clean up") {
        sh(script: '''
            for PREFIX in k8sgerrit gerrit-base base; do
                echo "$(docker images --format '{{.Repository}}:{{.Tag}}' | grep $PREFIX)"
                docker rmi -f $(docker images --format '{{.Repository}}:{{.Tag}}'| grep $PREFIX)
                docker rmi -f $(docker images --filter 'dangling=true' -q --no-trunc) 2>/dev/null || continue
            done
        ''')
    }
}
