buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  publishAPI = true
  buildNode =  'jenkins-agent-java11'

  doApiLint = true
  apiTypes = 'RAML'
  apiDirectories = 'ramls'

  doDocker = {
    buildJavaDocker {
      publishMaster = true
      healthChk = true
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}

