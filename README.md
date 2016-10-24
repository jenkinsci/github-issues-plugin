Jenkins GitHub Issues Plugin
============================

The Jenkins GitHub Issues plugin allows you to create a GitHub issues whenever your build fails. Once the build starts passing again, the issue will automatically be closed.

It is currently under development.

Installation
============

1. Install the [GitHub plugin](https://wiki.jenkins-ci.org/display/JENKINS/GitHub+Plugin) if you don't already have it installed. The GitHub Issues plugin reuses some of the configuration from the GitHub plugin, such as the authentication details.
2. In `Manage Jenkins` → `Configure System` → `GitHub` → `GitHub Servers`, ensure at least one GitHub server is configured and the "Test connection" button works.
3. [TODO: Add instructions for installing github-issues plugin once it's released]
4. In your project configuration, add a post-build action of "Create GitHub issue on failure".

Building
========
Clone this repo and run `mvn hpi:run` to run a test instance of Jekins.

To package, run `mvn package` and grab the `target/github-issues.hpi` file.
