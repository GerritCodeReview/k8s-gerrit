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
                ./publish --tag $(./get_version.sh) --update-latest
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
