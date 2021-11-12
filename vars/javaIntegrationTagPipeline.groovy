properties([
  pipelineTriggers([
   [$class: 'GenericTrigger',
    genericVariables: [
     [ key: 'tag', value: '$.ref' ],
     [ key: 'project_name', value: '$.repository.name' ],
     [ key: 'clone_url', value: '$.repository.clone_url' ],
     [ key: 'username', value: '$.sender.login' ]
    ],
     
    causeString: 'commit',
    
    token: 'java-tag',
    
    printContributedVariables: true,
    printPostContent: true,
    
    regexpFilterText: '$tag',
    regexpFilterExpression: '^v\\d{1,2}(\\.\\d{1,2}){2}$'
   ]
  ])
 ])

pipeline {
    agent none

    environment {
        jwt_key = "test-key"
    }

    stages {
        stage("Prepare") {
            agent { label 'master' }
            steps {
                deleteDir()
                script {
                    currentBuild.displayName = "${project_name} ${tag}"
                    currentBuild.description = "Building tag ${tag} for repo ${project_name}. Tag created by ${username}."
                }
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'refs/tags/$tag']],
                    userRemoteConfigs: [[url: '$clone_url']]
                ])
                stash 'workspace'
            }
        }
        stage('Gradle Build') {
            agent { label 'master' }
            steps {
                unstash 'workspace'
                sh './gradlew clean'
                sh './gradlew build'
                stash 'workspace'
            }
        }
        stage('Test') {
            agent { label 'master' }
            steps {
                unstash 'workspace'
                publishHTML([allowMissing         : false,
                                alwaysLinkToLastBuild: true,
                                keepAll              : true,
                                reportDir            : 'build/reports/tests/test',
                                reportFiles          : 'index.html',
                                reportName           : 'HTML Report',
                                reportTitles         : ''])
                stash 'workspace'
            }
        }
        stage('Build Container & Push to ACR') {
            agent { label 'master' }
            steps {
                unstash 'workspace'
                script {
                    docker.withRegistry("${env.DOCKER_REGISTRY_URL}", 'docker_registry_credentials') {
                        def acrRepoName =  "${project_name}/prod"
                        def customImage = docker.build("${acrRepoName}:${tag}")
                        customImage.push("${tag}")
                    }
                }
                stash 'workspace'
            }
        }
        stage('Post') {
            agent { label 'master' }
            steps {
                unstash 'workspace'
            }
            post {
                always {
                    junit 'build/test-results/**/TEST-*.xml'
                }
                cleanup {
                    deleteDir()
                }
            }
        }
    }
}
