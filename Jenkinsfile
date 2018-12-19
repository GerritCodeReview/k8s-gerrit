class Config {
  static Map<String, String> gerritVersions = [
    'master': 'https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-master/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war',
    '2.16': 'https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-stable-2.16/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war',
    '2.15': 'https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-stable-2.15/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war',
    '2.14': 'https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-stable-2.14/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war',
    '2.13': 'https://gerrit-ci.gerritforge.com/job/Gerrit-buck-stable-2.13/lastSuccessfulBuild/artifact/gerrit/buck-out/gen/gerrit.war',
    '2.12': 'https://gerrit-ci.gerritforge.com/job/Gerrit-buck-stable-2.12/lastSuccessfulBuild/artifact/gerrit/buck-out/gen/gerrit.war'
  ]
}

node('master') {
  stage('Checkout source') {
    checkout scm
  }
  Config.gerritVersions.each { k,v ->
    stage("Build container images for Gerrit ${k}") {
      sh(script: "./build --gerrit-url ${v}")
    }
  }
  stage('Clean up') {
      sh(script: '''
        for PREFIX in k8sgerrit gerrit-base base ubuntu; do
          docker rmi -f $(docker images --format '{{.Repository}}:{{.Tag}}' | grep $PREFIX)
          docker rmi -f $(docker images --filter 'dangling=true' -q --no-trunc) 2>/dev/null || continue
        done
      ''')
  }
}
