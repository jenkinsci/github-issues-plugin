Jenkins GitHub Issues Plugin
============================

The Jenkins GitHub Issues plugin allows you to create a GitHub issues whenever your build fails. Once the build starts passing again, the issue will automatically be closed.

See the wiki at https://wiki.jenkins-ci.org/display/JENKINS/GitHub+Issues+Plugin for documentation and installation instructions. Report bugs here: http://dl.vc/jenkins-github-issues-bug (you will need to first create a Jenkins account at https://accounts.jenkins.io/)

When using the great `job-dsl` plugin, you can configure a publisher step as follows:

```groovy
job(String name) {
  publishers {
    gitHubIssueNotifier {
      // Title to use for the GitHub issues created when builds start to fail.
      issueTitle(String value)
      // Body text to use for the GitHub issues created when builds start to fail.
      issueBody(String value)
      // If specified, this label will be applied to all tasks created through this plugin.
      issueLabel(String value)
      // Repo to use for the GitHub issues to create when builds start to fail.
      issueRepo(String value)
      // Check this to change the behavior when a job fails a second time and previously created issue exists, if checked, this issue get reopened instead of creating a new one.
      issueReopen(boolean value)
      // If checked, and a job is continuously failing, every additional failure adds a new comment.
      issueAppend(boolean value)
    }
  }
}
```


The documentation below is mainly for developers that want to modify the plugin itself.

Building
========
Clone this repo and run `mvn hpi:run` to run a test instance of Jekins.

To package, run `mvn package` and grab the `target/github-issues.hpi` file. Run `mvn release:prepare release:perform` to publish.


Setup
=====
In order to test the plugin in action, you need to create a dummy project in github and configure it either in your `settings.xml` or 
in the `prepare-developement-workspace` profile in your `pom.xml`.

In your `settings.xml` add the folling profile:

```xml
<profile>
  <id>github-issues-test-repo<id>
  <activation>
    <activeByDefault>true<activeByDefault>
  </activation>
  <properties>
    <github.test.project>https://github.com/YOUR_NAME/DUMMY_REPO/</github.test.project>
  </properties>
</profile>
```
In order to test the plugins interaction, you also need to create a github token and export it as an environment variable
`GITHUB_OAUTH_TOKEN`. Doing this will help the `src/dev/assets/work/init.groovy` to setup the github server config on first start.

After you started jenkins using `mvn hpi:run`, a job `test` will be created, that will alternate with failure and success.
     
