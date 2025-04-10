def call(Map configMap){
    pipeline {
    agent {
        label 'AGENT-1'
    }
    options{
        timeout(time: 30, unit: 'MINUTES')
    }
    parameters{
        booleanParam(name: 'deploy', defaultValue: false, description: 'select to deploy or not')
    }
    environment {
        appVersion = '' // This will become global, we can use env accross stages
        region = 'us-east-1'
        account_id = '361769572747'
        project = configMap.get("project")
        environment = 'dev'
        component = configMap.get("component") 
    }

    stages {

        stage('Read the version') {
            steps {
                script{
                    def packageJson = readJSON file: 'package.json'
                    appVersion = packageJson.version
                    echo "App version: ${appVersion}"
                }
            }
        }
        stage('Debug Env') {
            steps {
                sh '''
            echo "== Node location =="
            which node || echo "node not found"
            echo "== NPM location =="
            which npm || echo "npm not found"
            echo "== Versions =="
            node -v || echo "node version not found"
            npm -v || echo "npm version not found"
            echo "== PATH =="
            echo $PATH
        '''
            }
        }
        stage('Install dependencies') {
            steps {
                sh 'npm install'
            }
        }
        stage('Docker build') {
            when{
                expression {params.deploy}
            }
            steps{
                build job: 'backend-cd', parameters: [
                    string(name: 'version', value: "$appVersion"),
                    string(name: 'ENVIRONMENT', value: "dev"),
                    ], wait: true
            }
        }
        stage('Deploy'){
            steps{
                withAWS(region: 'us-east-1', credentials: 'aws-creds-terraform'){
                    sh """
                       aws eks update-kubeconfig --region ${region} --name ${project}-${environment}  
                       cd helm
                       sed -i 's/IMAGE_VERSION/${appVersion}/g' values-${environment}.yaml
                       helm upgrade --install ${component} -n ${project} -f values-${environment}.yaml .
                    """
                }

            }
        }
            
    }
        post {
            always {
                echo "this section runs always"
                deleteDir()
            }
            success{
                echo "this section runs when pipeline success"
            }
            failure{
                echo "this section runs when pipeline failure"
            }
        }
    }
}