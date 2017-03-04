Jenkins GitHub Issues Plugin
============================

The Jenkins GitHub Issues plugin allows you to create a GitHub issues whenever your build fails. Once the build starts passing again, the issue will automatically be closed.

See the Jenkins plugins site at https://plugins.jenkins.io/github-issues for documentation and installation instructions. Report bugs here: http://dl.vc/jenkins-github-issues-bug (you will need to first create a Jenkins account at https://accounts.jenkins.io/)

Contribute
==========
The documentation below is mainly for developers that want to modify the plugin itself. If you simply want to use the plugin, refer to the documentation on the Jenkins plugin site.

Building
--------
Clone this repo and run `mvn hpi:run` to run a test instance of Jekins.

To package, run `mvn package` and grab the `target/github-issues.hpi` file. Run `mvn release:prepare release:perform` to publish.


Setup
-----
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
     
