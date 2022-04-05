node("master") {
    gerritReview labels: ['Verified': 0]

    catchError(message: 'Build failed', stageResult: 'FAILURE') {
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
                    ORG=k8sgerrit
                    test -n $GERRIT_CHANGE_NUMBER && ORG=k8sgerrit-test
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

    if (env.GERRIT_CHANGE_NUMBER != null) {
        stage('Voting') {
            if (currentBuild.result.equals("FAILURE")) {
                gerritReview labels: ['Verified': -1], message: env.BUILD_URL
            } else {
                gerritReview labels: ['Verified': 1], message: env.BUILD_URL
            }
        }
    }
}
