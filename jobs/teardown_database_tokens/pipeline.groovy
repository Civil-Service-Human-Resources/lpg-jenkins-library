pipeline {
    agent none

    options {
        skipDefaultCheckout()
    }

    environment {
        SQL_HOST = credentials('prod_mysql_host')
        SQL_USER = credentials('prod_mysql_gp_user')
        SQL_PASSWORD = credentials('Prod_MySQL_GP_Pass')
    }

    stages {
        stage("Clear tokens") {
            agent { label 'master' }

            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'idt-feature-LC-1113-duplicate-token-cleanup']],
                    userRemoteConfigs: [[url: 'https://github.com/Civil-Service-Human-Resources/lpg-jenkins-library']]
                ])
                dir('./jobs/teardown_database_tokens') {
                    script {
                        sh 'python3 -m pip install -r requirements.txt'
                        sh 'python3 script.py'
                    }
                }
            }
        }
    }
}