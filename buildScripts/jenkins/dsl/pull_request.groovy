pipelineJob('netgod-terraform-pull-request') {
    displayName('netgod-terraform-pull-request')
    description("Pull Request CI for netgod-terraform")
    properties {
        githubProjectUrl('https://github.com/yudapinhas/netgod-terraform')
    }
    definition {
      cpsScm {
        scm {
          git {
            remote {
              url(repo.sshUrl)
              credentials('github-ssh-key')
              refspec('+refs/pull/*/head:refs/remotes/origin/pr/*')
            }
            branches('${ghprbActualCommit}')         
            }
        }
        scriptPath('buildScripts/jenkins/pipelines/pull_request.groovy')
      }
    }
    triggers {
        githubPullRequest {
            useGitHubHooks()
            permitAll()
            extensions {
                commitStatus {
                    context("CI - netgod-terraform PR")
                    triggeredStatus("Triggered by PR or comment")
                    startedStatus("Running CI for PR...")
                    completedStatus("SUCCESS", "Build succeeded")
                    completedStatus("FAILURE", "Build failed")
                }
            }
        }
    }
}
