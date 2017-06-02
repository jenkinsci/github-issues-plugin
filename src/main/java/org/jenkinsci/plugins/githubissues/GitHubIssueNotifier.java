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
import org.kohsuke.stapler.DataBoundSetter;
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
    private boolean issueReopen = false;
    private boolean issueAppend = false;

    /**
     * Initialises the {@link GitHubIssueNotifier} instance.
     */
    @DataBoundConstructor
    public GitHubIssueNotifier() {
    }

    /**
     * Initialises the {@link GitHubIssueNotifier} instance.
     * @param issueTitle the issue title
     * @param issueBody  the issue body
     * @param issueLabel the issue label
     * @param issueRepo the issue repo
     * @param issueReopen reopen the issue
     * @param issueAppend append to existing issue
     */
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
     * @param run The run
     * @return The GitHub repository
     * @throws GitHubRepositoryException when the GitHub repository can not be loaded
     */
    public GHRepository getRepoForRun(Run<?, ?> run) throws GitHubRepositoryException {
        final String repoUrl;
        if (StringUtils.isNotBlank(this.issueRepo)) {
            repoUrl = this.issueRepo;
        } else {
            Job<?, ?> rootJob = run.getParent();
            if (run instanceof AbstractBuild<?, ?>) {
                // If the run is an AbstractBuild, it could be a build that contains a root build (for example, a
                // multi-build project). In that case, we need to get the root project as that's where the GitHub settings
                // are configured.
                rootJob = ((AbstractBuild) run).getRootBuild().getProject();
            }
            GithubProjectProperty project = rootJob.getProperty(GithubProjectProperty.class);
            if (project == null) {
                throw new GitHubRepositoryException("GitHub property not configured");
            }
            repoUrl = project.getProjectUrlStr();
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
            repo = getRepoForRun(run);
        } catch (GitHubRepositoryException ex) {
            logger.println("WARNING: No GitHub config available for this job, GitHub Issue Notifier will not run! Error: " + ex.getMessage());
            return;
        }

        Result result = run.getResult();

        final GitHubIssueAction previousGitHubIssueAction = getLatestIssueAction((Run) run.getPreviousBuild());
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

    private GitHubIssueAction getLatestIssueAction(Run previousBuild) {
        if (previousBuild != null) {
            GitHubIssueAction previousGitHubIssueAction = previousBuild.getAction(GitHubIssueAction.class);
            if (previousGitHubIssueAction != null) {
                return previousGitHubIssueAction;
            } else {
                return this.getLatestIssueAction(previousBuild.getPreviousBuild());
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

    @DataBoundSetter
    public void setIssueTitle(String issueTitle) {
        this.issueTitle = issueTitle;
    }

    /**
     * Returns the issue body.
     *
     * @return the issue body
     */
    public String getIssueBody() {
        return issueBody;
    }

    @DataBoundSetter
    public void setIssueBody(String issueBody) {
        this.issueBody = issueBody;
    }

    /**
     * Returns the issue label.
     *
     * @return the issue label
     */
    public String getIssueLabel() {
        return issueLabel;
    }

    @DataBoundSetter
    public void setIssueLabel(String issueLabel) {
        this.issueLabel = issueLabel;
    }

    /**
     * Flag to switch between reopening an existing issue or
     * creating a new one.
     * @return true if an existing issue should be reopened.
     */
    public boolean isIssueReopen() {
        return issueReopen;
    }

    @DataBoundSetter
    public void setIssueReopen(boolean issueReopen) {
        this.issueReopen = issueReopen;
    }

    /**
     * Flag to switch between commenting an issue on continuous failure or just on first failure.
     * @return true if an issue should be commented continuously on feach failures.
     */
    public boolean isIssueAppend() {
        return issueAppend;
    }

    @DataBoundSetter
    public void setIssueAppend(boolean issueAppend) {
        this.issueAppend = issueAppend;
    }

    /**
     * An alternative repo to report the issues.
     * @return the url of the issue repo if set
     */
    public String getIssueRepo() {
        return issueRepo;
    }

    @DataBoundSetter
    public void setIssueRepo(String issueRepo) {
        this.issueRepo = issueRepo;
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
