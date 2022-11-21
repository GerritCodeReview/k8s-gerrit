node("k8s") {
    catchError(message: 'Build failed', stageResult: 'FAILURE') {
        stage("Checkout source") {
            checkout([
                $class: 'GitSCM',
                branches: scm.branches,
                extensions: [[
                    $class: 'CloneOption',
                    noTags: false,
                    reference: '',
                    shallow: false
                ]],
                userRemoteConfigs: scm.userRemoteConfigs
            ])
        }
        stage("Build") {
            sh(script: "./build")
            sh(script: "./build --tag latest")
        }
        stage("Publish") {
            withDockerRegistry(
                    credentialsId: 'dockerhub',
                    url: "https://docker.io") {
                sh(script: './publish --tag $(./get_version.sh)')
                sh(script: './publish --tag latest')
            }
        }
    }
    stage("Clean up") {
        sh(script: '''
            docker rmi -f $(docker images --format '{{.Repository}}:{{.Tag}}'| grep $(git describe --always --dirty))
            docker rmi -f $(docker images --filter 'dangling=true' -q --no-trunc) 2>/dev/null || continue
        ''')
    }
}
