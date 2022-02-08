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
                script {
                    sh 'python3 -m pip install -r requirements.txt'
                    sh 'python3 script.py'
                }
            }
        }
    }
}