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
import org.kohsuke.github.GHIssue;
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

    @DataBoundConstructor
    public GitHubIssueNotifier() {
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
     */
    public GHRepository getRepoForJob(Job<?, ?> job) {
        GithubProjectProperty foo = job.getProperty(GithubProjectProperty.class);
        GitHubRepositoryName repoName = GitHubRepositoryName.create(foo.getProjectUrlStr());
        return repoName.resolveOne();
    }

    @Override
    public void perform(
        @Nonnull Run<?, ?> run,
        @Nonnull FilePath workspace,
        @Nonnull Launcher launcher,
        @Nonnull TaskListener listener
    ) throws InterruptedException, IOException {
        Job<?, ?> job = run.getParent();
        PrintStream logger = listener.getLogger();

        Result result = run.getResult();
        GitHubIssueJobProperty property = GitHubIssueJobProperty.getOrCreateForJob(job);
        boolean hasIssue = property.getIssueNumber() != 0;

        // Return early without initialising the GitHub API client, if we can avoid it
        if (result == Result.SUCCESS && !hasIssue) {
            // The best case - Successful build with no open issue :D
            return;
        } else if ((result == Result.FAILURE || result == Result.UNSTABLE) && hasIssue) {
            // Issue was already created for a previous failure
            logger.format(
                "GitHub Issue Notifier: Build is still failing and issue #%s already exists. Not sending anything to GitHub issues",
                property.getIssueNumber()
            );
            return;
        }

        // If we got here, we need to grab the repo to create an issue (or close an existing issue)
        GHRepository repo = getRepoForJob(run.getParent());
        if (repo == null) {
            logger.println("WARNING: No GitHub config available for this job, GitHub Issue Notifier will not run!");
            return;
        }

        if (result == Result.FAILURE || result == Result.UNSTABLE) {
            GHIssue issue = IssueCreator.createIssue(run, getDescriptor(), repo, listener, workspace);
            logger.format("GitHub Issue Notifier: Build has started failing, filed GitHub issue #%s\n", issue.getNumber());
            property.setIssueNumber(issue.getNumber());
            job.save();
        } else if (result == Result.SUCCESS) {
            logger.format("GitHub Issue Notifier: Build was fixed, closing GitHub issue #%s", property.getIssueNumber());
            GHIssue issue = repo.getIssue(property.getIssueNumber());
            issue.comment("Build was fixed!");
            issue.close();
            property.setIssueNumber(0);
            job.save();
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private String issueTitle = "$JOB_NAME $BUILD_DISPLAY_NAME failed";
        private String issueBody =
            "Build '$JOB_NAME' is failing!\n\n" +
            "Last 50 lines of build output:\n\n" +
            "```\n" +
            "${OUTPUT, lines=50}\n" +
            "```\n\n" + "" +
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
         */
        public String getIssueTitle() {
            return issueTitle;
        }

        /**
         * Body of the issue to create on GitHub
         */
        public String getIssueBody() {
            return issueBody;
        }

        /**
         * Label to use for the issues created on GitHub.
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
