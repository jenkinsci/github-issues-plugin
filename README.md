Jenkins GitHub Issues Plugin
============================

The Jenkins GitHub Issues plugin allows you to create a GitHub issues whenever your build fails. Once the build starts passing again, the issue will automatically be closed.

See the wiki at https://wiki.jenkins-ci.org/display/JENKINS/GitHub+Issues+Plugin for installation instructions.

Building
========
Clone this repo and run `mvn hpi:run` to run a test instance of Jekins.

To package, run `mvn package` and grab the `target/github-issues.hpi` file. Run `mvn release:prepare release:perform` to publish.
