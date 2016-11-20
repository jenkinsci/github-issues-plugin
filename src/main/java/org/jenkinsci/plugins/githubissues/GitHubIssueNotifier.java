/**
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.jenkinsci.plugins.githubissues;

import com.cloudbees.jenkins.GitHubRepositoryName;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.githubissues.exceptions.GitHubRepositoryException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Notifier that creates GitHub issues when builds fail, and automatically closes the issue once the build starts
 * passing again.
 */
public class GitHubIssueNotifier extends Notifier implements SimpleBuildStep {
    private String issueTitle;
    private String issueBody;
    private String issueLabel;
    private String issueRepo;
    private boolean issueReopen = true;
    private boolean issueAppend = true;

    /**
     * Initialises the {@link GitHubIssueNotifier} instance.
     * @param issueTitle the issue title
     * @param issueBody  the issue body
     * @param issueLabel the issue label
     * @param issueRepo the issue repo
     * @param issueReopen reopen the issue
     * @param issueAppend append to existing issue
     */
    @DataBoundConstructor
    public GitHubIssueNotifier(String issueTitle, String issueBody, String issueLabel, String issueRepo, boolean issueReopen, boolean issueAppend) {
        this.issueTitle = issueTitle;
        this.issueBody = issueBody;
        this.issueLabel = issueLabel;
        this.issueRepo = issueRepo;
        this.issueReopen = issueReopen;
        this.issueAppend = issueAppend;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public GitHubIssueNotifier.DescriptorImpl getDescriptor() {
        return (GitHubIssueNotifier.DescriptorImpl) super.getDescriptor();
    }

    /**
     * Gets the GitHub repository for the specified job.
     * @param job The job
     * @return The GitHub repository
     * @throws GitHubRepositoryException when the GitHub repository can not be loaded
     */
    public GHRepository getRepoForJob(Job<?, ?> job) throws GitHubRepositoryException {
        final String repoUrl;
        if (StringUtils.isNotBlank(this.issueRepo)) {
            repoUrl = this.issueRepo;
        } else {
            GithubProjectProperty foo = job.getProperty(GithubProjectProperty.class);
            repoUrl = foo.getProjectUrlStr();
        }
        GitHubRepositoryName repoName = GitHubRepositoryName.create(repoUrl);
        if (repoName == null) {
            throw new GitHubRepositoryException("GitHub project not configured");
        }
        GHRepository repo = repoName.resolveOne();
        if (repo == null) {
            throw new GitHubRepositoryException(
                    "Could not connect to GitHub repository. Please double-check that you have correctly configured a " +
                            "GitHub API key."
            );
        }
        return repo;
    }

    @Override
    public void perform(
        @Nonnull Run<?, ?> run,
        @Nonnull FilePath workspace,
        @Nonnull Launcher launcher,
        @Nonnull TaskListener listener
    ) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();

        // If we got here, we need to grab the repo to create an issue (or close an existing issue)
        GHRepository repo;
        try {
            repo = getRepoForJob(run.getParent());
        } catch (GitHubRepositoryException ex) {
            logger.println("WARNING: No GitHub config available for this job, GitHub Issue Notifier will not run! Error: " + ex.getMessage());
            return;
        }

        if (repo == null) {
            logger.println("WARNING: No GitHub config available for this job, GitHub Issue Notifier will not run!");
            return;
        }

        Result result = run.getResult();
        final GitHubIssueAction previousGitHubIssueAction = getLatestIssueAction((Build) run.getPreviousBuild());
        GHIssue issue = null;
        if (previousGitHubIssueAction != null) {
            issue = repo.getIssue(previousGitHubIssueAction.getIssueNumber());
        }

        if (result == Result.FAILURE) {
            if (issue != null) {
                String issueBody = this.getIssueBody();
                if (StringUtils.isBlank(issueBody)) {
                    issueBody = this.getDescriptor().getIssueBody();
                }
                if (issue.getState() == GHIssueState.OPEN) {
                    if (issueAppend) {
                        //CONTINUE
                        issue.comment(IssueCreator.formatText(issueBody, run, listener, workspace));
                        logger.format(
                                 "GitHub Issue Notifier: Build is still failing and issue #%s already exists. " +
                                         "Not sending anything to GitHub issues%n",issue.getNumber());
                    }
                    run.addAction(new GitHubIssueAction(issue, GitHubIssueAction.TransitionAction.CONTINUE));
                } else if (issue.getState() == GHIssueState.CLOSED) {
                    if (issueReopen) {
                        // REOPEN
                        logger.format("GitHub Issue Notifier: Build has started failing again, reopend GitHub issue #%s%n", issue.getNumber());
                        issue.reopen();
                        issue.comment(IssueCreator.formatText(issueBody, run, listener, workspace));
                        //set new labels
                        if (issueLabel != null && !issueLabel.isEmpty()) {
                            issue.setLabels(issueLabel.split(",| "));
                        }
                        run.addAction(new GitHubIssueAction(issue, GitHubIssueAction.TransitionAction.REOPEN));
                    } else {
                        //CREATE NEW
                        issue = IssueCreator.createIssue(run, this, repo, listener, workspace);
                        logger.format("GitHub Issue Notifier: Build has started failing, filed GitHub issue #%s%n", issue.getNumber());
                        run.addAction(new GitHubIssueAction(issue, GitHubIssueAction.TransitionAction.OPEN));
                    }
                }
            } else {
                // CREATE NEW
                issue = IssueCreator.createIssue(run, this, repo, listener, workspace);
                logger.format("GitHub Issue Notifier: Build has started failing, filed GitHub issue #%s%n", issue.getNumber());
                run.addAction(new GitHubIssueAction(issue, GitHubIssueAction.TransitionAction.OPEN));
            }
        } else if (result == Result.SUCCESS && issue != null && issue.getState() == GHIssueState.OPEN) {
            issue.comment("Build was fixed!");
            issue.close();
            logger.format("GitHub Issue Notifier: Build was fixed, closing GitHub issue #%s%n", issue.getNumber());
            run.addAction(new GitHubIssueAction(issue, GitHubIssueAction.TransitionAction.CLOSE));
        }
    }

    private GitHubIssueAction getLatestIssueAction(Build previousBuild) {
        if (previousBuild != null) {
            GitHubIssueAction previousGitHubIssueAction = previousBuild.getAction(GitHubIssueAction.class);
            if (previousGitHubIssueAction != null) {
                return previousGitHubIssueAction;
            } else {
                return this.getLatestIssueAction((Build) previousBuild.getPreviousBuild());
            }
        }
        return null;
    }

    /**
     * Returns the issue title.
     *
     * @return the issue title
     */
    public String getIssueTitle() {
        return issueTitle;
    }

    /**
     * Returns the issue body.
     *
     * @return the issue body
     */
    public String getIssueBody() {
        return issueBody;
    }

    /**
     * Returns the issue label.
     *
     * @return the issue label
     */
    public String getIssueLabel() {
        return issueLabel;
    }

    public boolean isIssueReopen() {
        return issueReopen;
    }

    public boolean isIssueAppend() {
        return issueAppend;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private String issueTitle = "$JOB_NAME $BUILD_DISPLAY_NAME failed";
        private String issueBody =
            "Build '$JOB_NAME' is failing!\n\n" +
            "Last 50 lines of build output:\n\n" +
            "```\n" +
            "${BUILD_LOG, maxLines=50}\n" +
            "```\n\n" +
            "Changes since last successful build:\n" +
            "${CHANGES_SINCE_LAST_SUCCESS, format=\"%c\", changesFormat=\"- [%a] %r - %m\\n\"}\n\n" +
            "[View full output]($BUILD_URL)";
        private String issueLabel;

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            issueTitle = formData.getString("issueTitle");
            issueBody = formData.getString("issueBody");
            issueLabel = formData.getString("issueLabel");
            save();
            return super.configure(req, formData);
        }

        /**
         * Title of the issue to create on GitHub
         *
         * @return issueTitle
         */
        public String getIssueTitle() {
            return issueTitle;
        }

        /**
         * Body of the issue to create on GitHub
         *
         * @return issueBody
         */
        public String getIssueBody() {
            return issueBody;
        }

        /**
         * Label to use for the issues created on GitHub.
         *
         * @return issueLabel
         */
        public String getIssueLabel() {
            return issueLabel;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Create GitHub issue on failure";
        }
    }
}
