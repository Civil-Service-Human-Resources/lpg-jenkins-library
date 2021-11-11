properties([
  pipelineTriggers([
   [$class: 'GenericTrigger',
    genericVariables: [
     [ key: 'committer_name', value: '$.actor.displayName' ],
     [ key: 'committer_email', value: '$.actor.emailAddress' ],
     [ key: 'ref', value: '$.changes[0].refId'],
     [ key: 'tag', value: '$.changes[0].refId', regexpFilter: 'refs/tags/'],
     [ key: 'commit', value: '$.changes[0].toHash' ],
     [ key: 'repo_slug', value: '$.repository.slug' ],
     [ key: 'project_key', value: '$.repository.project.key' ],
     [ key: 'clone_url', value: '$.repository.links.clone[0].href' ]
    ],
     
    causeString: '$committer_name pushed tag $tag to $clone_url referencing $commit',
    
    token: 'abc123',
    
    printContributedVariables: true,
    printPostContent: true,
    
    regexpFilterText: '$ref',
    regexpFilterExpression: '^refs/tags/.*'
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
            deleteDir()
            sh '''
            echo git clone $clone_url
            echo git checkout $commit
            sleep 1
            '''
        }
        stage('Gradle Build') {
            agent { label 'master' }
            steps {
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
                        def acrRegionName = "test"
                        if (env.BRANCH_NAME =~ '^v\\d{1,2}(\\.\\d{1,2}){2}$') {
                            acrRegionName = "prod"
                        }
                        def acrRepoName =  "${pipelineParams.dockerRepository}/${acrRegionName}"
                        def customImage = docker.build("${acrRepoName}:${env.BRANCH_NAME}")
                        customImage.push("${env.BRANCH_NAME}")
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
